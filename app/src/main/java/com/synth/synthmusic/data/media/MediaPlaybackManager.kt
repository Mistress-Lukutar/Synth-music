package com.synth.synthmusic.data.media

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.synth.synthmusic.data.local.database.AppDatabase
import com.synth.synthmusic.data.local.database.PlaybackQueueItemEntity
import com.synth.synthmusic.data.local.database.PlaybackStateEntity
import com.synth.synthmusic.data.local.database.SongDao
import com.synth.synthmusic.domain.model.PlaybackState
import com.synth.synthmusic.domain.repository.PlaylistRepository
import com.synth.synthmusic.domain.repository.SettingsRepository
import com.synth.synthmusic.domain.repository.SongRepository
import com.synth.synthmusic.data.local.database.toDomain
import com.synth.synthmusic.domain.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.pow

/**
 * Wrapper around ExoPlayer providing playback control, state observation,
 * and automatic process-death recovery via Room.
 *
 * Playback state is split into two layers:
 * 1. [playbackState] — event-driven (track changes, play/pause, repeat/shuffle).
 * 2. [currentPositionMs] / [currentDurationMs] — high-frequency position updates
 *    (50 ms polling) exposed as separate flows to avoid spurious recompositions.
 */
class MediaPlaybackManager(
    private val context: Context,
    private val appDatabase: AppDatabase,
    private val songDao: SongDao,
    private val songRepository: SongRepository,
    private val settingsRepository: SettingsRepository,
    private val playlistRepository: PlaylistRepository
) {
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentQueue = MutableStateFlow<List<Song>>(emptyList())
    val currentQueue: StateFlow<List<Song>> = _currentQueue.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _currentDurationMs = MutableStateFlow(0L)
    val currentDurationMs: StateFlow<Long> = _currentDurationMs.asStateFlow()

    private val _audioSessionId = MutableStateFlow(0)
    val audioSessionId: StateFlow<Int> = _audioSessionId.asStateFlow()

    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var _player: ExoPlayer? = null
    val player: ExoPlayer
        get() = _player ?: createPlayer().also { _player = it }

    private var fadeManager: AudioFadeManager = createFadeManager()
    private var fadeDurationMs: Int = 300
    private var currentTargetVolume: Float = 1f
    private var endOfTrackJob: Job? = null
    private var positionUpdateJob: Job? = null
    private var persistJob: Job? = null
    private var positionPersistJob: Job? = null
    private val persistMutex = Mutex()
    private val isRestoring = AtomicBoolean(false)
    private var isReleased = false

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            _playbackState.update {
                it.copy(isPlaying = player.isPlaying)
            }
            _currentDurationMs.value = player.duration.coerceAtLeast(0)
            if (player.isPlaying) {
                startPositionUpdates()
            } else {
                stopPositionUpdates()
            }
            persistStateImmediate()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playbackState.update {
                it.copy(
                    isPlaying = isPlaying,
                    currentSongId = player.currentMediaItem?.mediaId
                )
            }
            _currentDurationMs.value = player.duration.coerceAtLeast(0)
            if (isPlaying) {
                if (fadeDurationMs > 0) {
                    fadeManager.fadeIn(fadeDurationMs.toLong(), currentTargetVolume)
                } else {
                    player.volume = currentTargetVolume
                }
                startEndOfTrackMonitor()
                startPositionUpdates()
            } else {
                stopEndOfTrackMonitor()
                stopPositionUpdates()
            }
            persistStateImmediate()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _playbackState.update {
                it.copy(currentSongId = mediaItem?.mediaId)
            }
            _currentPositionMs.value = 0
            _currentDurationMs.value = player.duration.coerceAtLeast(0)
            mediaItem?.mediaId?.let { songId ->
                updateTargetVolume(songId)
                scope.launch { playlistRepository.recordPlayAndSyncPlaylists(songId) }
            }
            if (player.isPlaying && fadeDurationMs > 0) {
                player.volume = 0f
                fadeManager.fadeIn(fadeDurationMs.toLong(), currentTargetVolume)
            } else {
                player.volume = currentTargetVolume
            }
            stopEndOfTrackMonitor()
            startEndOfTrackMonitor()
            persistStateImmediate()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            _currentPositionMs.value = player.currentPosition
            _currentDurationMs.value = player.duration.coerceAtLeast(0)
            persistPositionDebounced()
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            _audioSessionId.value = audioSessionId
        }
    }

    init {
        ensureInitialized()
        restoreState()
        collectPlaybackSettings()
    }

    /**
     * Verifies that the player and coroutine scope are alive.
     * If the manager was previously released (e.g. service death),
     * this method recreates all internal resources so the singleton
     * remains usable across service restarts.
     */
    fun ensureInitialized() {
        if (isReleased || _player == null || !scope.isActive) {
            isReleased = false
            isRestoring.set(false)
            _player?.let { safeRelease(it) }
            _player = createPlayer()
            if (!scope.isActive) {
                scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
            }
            fadeManager = createFadeManager()
            _player?.addListener(listener)
            // Do not call restoreState here; it runs once in init.
        }
    }

    private fun createPlayer(): ExoPlayer {
        return ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true // handleAudioFocus
            )
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
    }

    private fun createFadeManager(): AudioFadeManager {
        return AudioFadeManager(player, scope)
    }

    private fun safeRelease(player: ExoPlayer) {
        try {
            player.removeListener(listener)
            player.release()
        } catch (_: Exception) {
            // Already released or in a bad state — ignore.
        }
    }

    private fun collectPlaybackSettings() {
        scope.launch {
            settingsRepository.settings.collect { settings ->
                withContext(Dispatchers.Main) {
                    player.playbackParameters = androidx.media3.common.PlaybackParameters(
                        settings.playbackSpeed.coerceIn(0.25f, 4.0f),
                        settings.playbackPitch.coerceIn(0.25f, 4.0f)
                    )
                    player.skipSilenceEnabled = settings.skipSilence
                }
                fadeDurationMs = settings.fadeDurationMs.coerceIn(0, 2000)
            }
        }
    }

    fun playSongs(songs: List<Song>, startIndex: Int = 0) {
        if (isRestoring.get()) return
        isRestoring.set(false) // user explicitly chose a song — abort restore
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
        persistStateImmediate()
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
        if (isRestoring.get()) return
        _currentQueue.update { it + song }
        player.addMediaItem(songToMediaItem(song))
        persistStateImmediate()
    }

    fun playNext(song: Song) {
        if (isRestoring.get()) return
        val currentIndex = player.currentMediaItemIndex
        val queue = _currentQueue.value.toMutableList()
        val insertIndex = (currentIndex + 1).coerceAtMost(queue.size)
        queue.add(insertIndex, song)
        _currentQueue.value = queue
        player.addMediaItem(insertIndex, songToMediaItem(song))
        persistStateImmediate()
    }

    fun clearQueue() {
        if (isRestoring.get()) return
        if (player.isPlaying && fadeDurationMs > 0) {
            fadeManager.fadeOut(fadeDurationMs.toLong()) {
                _currentQueue.value = emptyList()
                player.clearMediaItems()
            }
        } else {
            _currentQueue.value = emptyList()
            player.clearMediaItems()
        }
        persistStateImmediate()
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        if (isRestoring.get()) return
        val queue = _currentQueue.value.toMutableList()
        if (fromIndex !in queue.indices) return
        val item = queue.removeAt(fromIndex)
        val newIndex = toIndex.coerceIn(0, queue.size)
        queue.add(newIndex, item)
        _currentQueue.value = queue
        player.moveMediaItem(fromIndex, newIndex)
        persistStateImmediate()
    }

    fun removeFromQueue(index: Int) {
        if (isRestoring.get()) return
        val queue = _currentQueue.value.toMutableList()
        if (index in queue.indices) {
            queue.removeAt(index)
            _currentQueue.value = queue
            player.removeMediaItem(index)
        }
        persistStateImmediate()
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

    fun stop() {
        if (player.isPlaying) {
            if (fadeDurationMs > 0) {
                fadeManager.fadeOut(fadeDurationMs.toLong()) {
                    player.stop()
                }
            } else {
                player.stop()
            }
        } else {
            player.stop()
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
        persistStateImmediate()
    }

    fun cycleRepeatMode() {
        val next = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        player.repeatMode = next
        _playbackState.update { it.copy(repeatMode = next) }
        persistStateImmediate()
    }

    /**
     * Releases native resources. Intended for **testing teardown only**.
     * After this call the manager will be automatically re-initialized
     * on the next [ensureInitialized] invocation.
     */
    internal fun release() {
        isReleased = true
        stopEndOfTrackMonitor()
        stopPositionUpdates()
        fadeManager.cancel()
        _player?.let { safeRelease(it) }
        _player = null
        scope.cancel()
    }

    /**
     * Synchronously flushes any pending state to disk.
     * Called by [PlaybackService] before the service is destroyed
     * to guarantee that the latest position / queue survive process death.
     */
    fun flushPersist() {
        if (isReleased || !scope.isActive) return
        persistJob?.cancel()
        positionPersistJob?.cancel()
        scope.launch {
            writePlaybackState()
        }
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

    private fun startPositionUpdates() {
        stopPositionUpdates()
        positionUpdateJob = scope.launch(Dispatchers.Main) {
            while (isActive) {
                if (player.isPlaying) {
                    _currentPositionMs.value = player.currentPosition
                }
                delay(50)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
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

    /**
     * Persists critical state (track, queue, repeat/shuffle) **immediately**.
     * Used for discrete events where data loss is unacceptable.
     */
    private fun persistStateImmediate() {
        if (isReleased || !scope.isActive) return
        persistJob?.cancel()
        persistJob = scope.launch {
            writePlaybackState()
        }
    }

    /**
     * Debounced persistence for high-frequency position updates.
     * Only writes [PlaybackStateEntity.positionMs] together with the rest of the row.
     */
    private fun persistPositionDebounced() {
        if (isReleased || !scope.isActive) return
        positionPersistJob?.cancel()
        positionPersistJob = scope.launch {
            delay(1000)
            writePlaybackState()
        }
    }

    private suspend fun writePlaybackState() {
        if (isReleased) return
        val state = _playbackState.value
        val queue = _currentQueue.value
        persistMutex.withLock {
            appDatabase.savePlaybackState(
                PlaybackStateEntity(
                    currentSongId = state.currentSongId,
                    positionMs = _currentPositionMs.value,
                    isPlaying = false, // do not auto-resume; just restore position
                    repeatMode = state.repeatMode,
                    shuffleMode = state.shuffleEnabled
                ),
                queue.mapIndexed { index, song ->
                    PlaybackQueueItemEntity(songId = song.id, orderIndex = index)
                }
            )
        }
    }

    private fun restoreState() {
        scope.launch {
            isRestoring.set(true)
            try {
                val saved = appDatabase.playbackStateDao().get() ?: return@launch
                val queueItems = appDatabase.playbackQueueItemDao().getAllOrdered()
                val songs = if (queueItems.isNotEmpty()) {
                    val songMap = queueItems.chunked(999)
                        .flatMap { chunk ->
                            songDao.getByIds(chunk.map { it.songId })
                        }
                        .associateBy { it.id }
                    queueItems.mapNotNull { songMap[it.songId] }
                } else {
                    saved.currentSongId?.let { id ->
                        songDao.getById(id)?.let { listOf(it) } ?: emptyList()
                    } ?: emptyList()
                }
                if (songs.isEmpty()) return@launch

                // Abort restore if the user already started playback while we were reading DB.
                if (!isRestoring.get()) return@launch

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
                    repeatMode = saved.repeatMode,
                    shuffleEnabled = saved.shuffleMode
                )
                _currentPositionMs.value = saved.positionMs
                _currentDurationMs.value = withContext(Dispatchers.Main) { player.duration.coerceAtLeast(0) }
                withContext(Dispatchers.Main) {
                    player.repeatMode = saved.repeatMode
                    player.shuffleModeEnabled = saved.shuffleMode
                }
            } finally {
                isRestoring.set(false)
            }
        }
    }

    private fun songToMediaItem(song: Song): MediaItem = with(song) {
        MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(song.uri)
            .setMediaMetadata(
                androidx.media3.common.MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setAlbumTitle(song.album)
                    .setArtworkUri(song.artworkUri?.let { it.toUri() })
                    .build()
            )
            .build()
    }
}
