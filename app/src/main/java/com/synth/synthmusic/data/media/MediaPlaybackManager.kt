package com.synth.synthmusic.data.media

import android.content.Context
import android.os.Handler
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.Renderer
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import com.synth.synthmusic.data.local.database.PlaybackStateDao
import com.synth.synthmusic.data.local.database.PlaybackStateEntity
import com.synth.synthmusic.data.local.database.SongDao
import com.synth.synthmusic.domain.repository.SettingsRepository
import com.synth.synthmusic.domain.repository.SongRepository
import com.synth.synthmusic.data.local.database.toDomain
import com.synth.synthmusic.domain.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.math.pow
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/**
 * Wrapper around ExoPlayer providing playback control, state observation,
 * and automatic process-death recovery via Room.
 */
class MediaPlaybackManager(
    context: Context,
    private val playbackStateDao: PlaybackStateDao,
    private val songDao: SongDao,
    private val songRepository: SongRepository,
    private val settingsRepository: SettingsRepository
) {
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentQueue = MutableStateFlow<List<Song>>(emptyList())
    val currentQueue: StateFlow<List<Song>> = _currentQueue.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @OptIn(UnstableApi::class)
    private val renderersFactory = object : DefaultRenderersFactory(context) {
        override fun buildAudioRenderers(
            context: Context,
            extensionRendererMode: Int,
            mediaCodecSelector: MediaCodecSelector,
            enableDecoderFallback: Boolean,
            audioSink: AudioSink,
            eventHandler: Handler,
            eventListener: AudioRendererEventListener,
            out: ArrayList<Renderer>
        ) {
            val customAudioSink = DefaultAudioSink.Builder(context)
                .setEnableAudioTrackPlaybackParams(false)
                .build()
            out.add(
                MediaCodecAudioRenderer(
                    context,
                    mediaCodecSelector,
                    enableDecoderFallback,
                    eventHandler,
                    eventListener,
                    customAudioSink
                )
            )
        }
    }

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setRenderersFactory(renderersFactory)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true // handleAudioFocus
        )
        .setWakeMode(C.WAKE_MODE_LOCAL)
        .build()

    private val fadeManager = AudioFadeManager(player, scope)
    private var fadeDurationMs: Int = 300
    private var currentTargetVolume: Float = 1f
    private var endOfTrackJob: Job? = null
    private var persistJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            _playbackState.update {
                it.copy(
                    isPlaying = player.isPlaying,
                    positionMs = player.currentPosition,
                    durationMs = player.duration.coerceAtLeast(0)
                )
            }
            persistState()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playbackState.update {
                it.copy(
                    isPlaying = isPlaying,
                    currentSongId = player.currentMediaItem?.mediaId,
                    positionMs = player.currentPosition,
                    durationMs = player.duration.coerceAtLeast(0)
                )
            }
            if (isPlaying) {
                if (fadeDurationMs > 0) {
                    fadeManager.fadeIn(fadeDurationMs.toLong(), currentTargetVolume)
                } else {
                    player.volume = currentTargetVolume
                }
                startEndOfTrackMonitor()
            } else {
                stopEndOfTrackMonitor()
            }
            persistState()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _playbackState.update {
                it.copy(
                    currentSongId = mediaItem?.mediaId,
                    positionMs = 0,
                    durationMs = player.duration.coerceAtLeast(0)
                )
            }
            mediaItem?.mediaId?.let { songId ->
                updateTargetVolume(songId)
                scope.launch { songRepository.incrementPlayCount(songId) }
            }
            if (player.isPlaying && fadeDurationMs > 0) {
                player.volume = 0f
                fadeManager.fadeIn(fadeDurationMs.toLong(), currentTargetVolume)
            } else {
                player.volume = currentTargetVolume
            }
            stopEndOfTrackMonitor()
            startEndOfTrackMonitor()
            persistState()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            _playbackState.update {
                it.copy(
                    positionMs = player.currentPosition,
                    durationMs = player.duration.coerceAtLeast(0)
                )
            }
            persistState()
        }
    }

    init {
        player.addListener(listener)
        restoreState()
        collectPlaybackSettings()
    }

    private fun collectPlaybackSettings() {
        scope.launch {
            settingsRepository.settings.collect { settings ->
                withContext(Dispatchers.Main) {
                    player.setPlaybackParameters(
                        androidx.media3.common.PlaybackParameters(
                            settings.playbackSpeed.coerceIn(0.25f, 4.0f),
                            settings.playbackPitch.coerceIn(0.25f, 4.0f)
                        )
                    )
                    player.setSkipSilenceEnabled(settings.skipSilence)
                }
                fadeDurationMs = settings.fadeDurationMs.coerceIn(0, 2000)
            }
        }
    }

    fun playSongs(songs: List<Song>, startIndex: Int = 0) {
        _currentQueue.value = songs
        val mediaItems = songs.map { songToMediaItem(it) }
        if (player.isPlaying && fadeDurationMs > 0) {
            fadeManager.fadeOut(fadeDurationMs.toLong()) {
                player.setMediaItems(mediaItems, startIndex, 0)
                player.prepare()
                player.play()
            }
        } else {
            player.setMediaItems(mediaItems, startIndex, 0)
            player.prepare()
            player.play()
        }
        persistState()
    }

    fun playQueueItem(index: Int) {
        if (player.isPlaying && fadeDurationMs > 0) {
            fadeManager.fadeOut(fadeDurationMs.toLong()) {
                player.seekTo(index, 0)
                player.play()
            }
        } else {
            player.seekTo(index, 0)
            player.play()
        }
    }

    fun addToQueue(song: Song) {
        _currentQueue.update { it + song }
        player.addMediaItem(songToMediaItem(song))
        persistState()
    }

    fun playNext(song: Song) {
        val currentIndex = player.currentMediaItemIndex
        val queue = _currentQueue.value.toMutableList()
        val insertIndex = (currentIndex + 1).coerceAtMost(queue.size)
        queue.add(insertIndex, song)
        _currentQueue.value = queue
        player.addMediaItem(insertIndex, songToMediaItem(song))
        persistState()
    }

    fun clearQueue() {
        if (player.isPlaying && fadeDurationMs > 0) {
            fadeManager.fadeOut(fadeDurationMs.toLong()) {
                _currentQueue.value = emptyList()
                player.clearMediaItems()
            }
        } else {
            _currentQueue.value = emptyList()
            player.clearMediaItems()
        }
        persistState()
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val queue = _currentQueue.value.toMutableList()
        if (fromIndex !in queue.indices) return
        val item = queue.removeAt(fromIndex)
        val newIndex = toIndex.coerceIn(0, queue.size)
        queue.add(newIndex, item)
        _currentQueue.value = queue
        player.moveMediaItem(fromIndex, newIndex)
        persistState()
    }

    fun removeFromQueue(index: Int) {
        val queue = _currentQueue.value.toMutableList()
        if (index in queue.indices) {
            queue.removeAt(index)
            _currentQueue.value = queue
            player.removeMediaItem(index)
        }
        persistState()
    }

    fun play() {
        if (!player.isPlaying) {
            player.play()
            if (fadeDurationMs > 0) {
                fadeManager.fadeIn(fadeDurationMs.toLong(), currentTargetVolume)
            } else {
                player.volume = currentTargetVolume
            }
        }
    }

    fun pause() {
        if (player.isPlaying) {
            if (fadeDurationMs > 0) {
                fadeManager.fadeOut(fadeDurationMs.toLong()) {
                    player.pause()
                }
            } else {
                player.pause()
            }
        }
    }

    fun playPause() {
        if (player.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun next() {
        if (fadeDurationMs > 0) {
            fadeManager.fadeOut(fadeDurationMs.toLong()) {
                player.seekToNext()
            }
        } else {
            player.seekToNext()
        }
    }

    fun previous() {
        if (fadeDurationMs > 0) {
            fadeManager.fadeOut(fadeDurationMs.toLong()) {
                player.seekToPrevious()
            }
        } else {
            player.seekToPrevious()
        }
    }

    fun seekTo(positionMs: Long) {
        if (fadeDurationMs > 0) {
            fadeManager.fadeOut(fadeDurationMs.toLong()) {
                player.seekTo(positionMs)
                fadeManager.fadeIn(fadeDurationMs.toLong(), currentTargetVolume)
            }
        } else {
            player.seekTo(positionMs)
        }
    }

    fun setShuffleEnabled(enabled: Boolean) {
        player.shuffleModeEnabled = enabled
        _playbackState.update { it.copy(shuffleEnabled = enabled) }
        persistState()
    }

    fun cycleRepeatMode() {
        val next = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        player.repeatMode = next
        _playbackState.update { it.copy(repeatMode = next) }
        persistState()
    }

    fun initAudioEffects(manager: AudioEffectsManager) {
        manager.initWithSession(player.audioSessionId)
    }

    fun release() {
        stopEndOfTrackMonitor()
        fadeManager.cancel()
        player.removeListener(listener)
        player.release()
        scope.cancel()
    }

    private fun startEndOfTrackMonitor() {
        stopEndOfTrackMonitor()
        if (fadeDurationMs <= 0) return
        endOfTrackJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                if (player.isPlaying && player.duration > 0) {
                    val remaining = player.duration - player.currentPosition
                    if (remaining <= fadeDurationMs && !fadeManager.isFading && player.volume > 0f) {
                        fadeManager.fadeOut(fadeDurationMs.toLong())
                    }
                }
                delay(100)
            }
        }
    }

    private fun stopEndOfTrackMonitor() {
        endOfTrackJob?.cancel()
        endOfTrackJob = null
    }

    private fun updateTargetVolume(songId: String) {
        val song = _currentQueue.value.find { it.id == songId }
        val gainDb = song?.replayGainTrackDb
        currentTargetVolume = if (gainDb != null) {
            10.0.pow(gainDb / 20.0).toFloat().coerceIn(0f, 1f)
        } else {
            1f
        }
    }

    private fun persistState() {
        val state = _playbackState.value
        val queue = _currentQueue.value
        persistJob?.cancel()
        persistJob = scope.launch {
            delay(500)
            playbackStateDao.insert(
                PlaybackStateEntity(
                    currentSongId = state.currentSongId,
                    positionMs = state.positionMs,
                    isPlaying = false, // do not auto-resume; just restore position
                    repeatMode = state.repeatMode,
                    shuffleMode = state.shuffleEnabled,
                    queueIds = queue.joinToString(",") { it.id }.takeIf { it.isNotEmpty() }
                )
            )
        }
    }

    private fun restoreState() {
        scope.launch {
            val saved = playbackStateDao.get() ?: return@launch
            val queueIds = saved.queueIds?.split(",")?.filter { it.isNotEmpty() } ?: emptyList()
            val songs = if (queueIds.isNotEmpty()) {
                songDao.getByIds(queueIds)
            } else {
                saved.currentSongId?.let { id ->
                    songDao.getById(id)?.let { listOf(it) } ?: emptyList()
                } ?: emptyList()
            }
            if (songs.isEmpty()) return@launch

            val domainSongs = songs.map { it.toDomain() }
            val startIndex = domainSongs.indexOfFirst { it.id == saved.currentSongId }.coerceAtLeast(0)

            _currentQueue.value = domainSongs
            val mediaItems = domainSongs.map { song -> songToMediaItem(song) }
            withContext(Dispatchers.Main) {
                player.setMediaItems(mediaItems, startIndex, saved.positionMs.coerceAtLeast(0))
                player.prepare()
            }

            _playbackState.value = PlaybackState(
                currentSongId = saved.currentSongId,
                isPlaying = false,
                positionMs = saved.positionMs,
                durationMs = withContext(Dispatchers.Main) { player.duration.coerceAtLeast(0) },
                repeatMode = saved.repeatMode,
                shuffleEnabled = saved.shuffleMode
            )
            withContext(Dispatchers.Main) {
                player.repeatMode = saved.repeatMode
                player.shuffleModeEnabled = saved.shuffleMode
            }
        }
    }

    private fun songToMediaItem(song: Song): MediaItem = with(song) { MediaItem.Builder() }
        .setMediaId(song.id)
        .setUri(song.uri)
        .setMediaMetadata(
            androidx.media3.common.MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album)
                .setArtworkUri(android.net.Uri.parse(song.artworkUri ?: ""))
                .build()
        )
        .build()

    /**
     * Current playback state exposed as a data class.
     */
    data class PlaybackState(
        val currentSongId: String? = null,
        val isPlaying: Boolean = false,
        val positionMs: Long = 0,
        val durationMs: Long = 0,
        val repeatMode: Int = Player.REPEAT_MODE_OFF,
        val shuffleEnabled: Boolean = false
    )
}
