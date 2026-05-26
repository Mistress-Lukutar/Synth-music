package com.synth.synthmusic.data.media

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.synth.synthmusic.data.local.database.PlaybackStateDao
import com.synth.synthmusic.data.local.database.PlaybackStateEntity
import com.synth.synthmusic.data.local.database.SongDao
import com.synth.synthmusic.domain.repository.SongRepository
import com.synth.synthmusic.data.local.database.toDomain
import com.synth.synthmusic.domain.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Wrapper around ExoPlayer providing playback control, state observation,
 * and automatic process-death recovery via Room.
 */
class MediaPlaybackManager(
    context: Context,
    private val playbackStateDao: PlaybackStateDao,
    private val songDao: SongDao,
    private val songRepository: SongRepository
) {
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentQueue = MutableStateFlow<List<Song>>(emptyList())
    val currentQueue: StateFlow<List<Song>> = _currentQueue.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true // handleAudioFocus
        )
        .setWakeMode(C.WAKE_MODE_LOCAL)
        .build()

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
                    currentSongId = player.currentMediaItem?.mediaId
                )
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
                scope.launch { songRepository.incrementPlayCount(songId) }
            }
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
    }

    fun playSongs(songs: List<Song>, startIndex: Int = 0) {
        _currentQueue.value = songs
        val mediaItems = songs.map { songToMediaItem(it) }
        player.setMediaItems(mediaItems, startIndex, 0)
        player.prepare()
        player.play()
        persistState()
    }

    fun playQueueItem(index: Int) {
        player.seekTo(index, 0)
        player.play()
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
        _currentQueue.value = emptyList()
        player.clearMediaItems()
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

    fun playPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun next() = player.seekToNext()
    fun previous() = player.seekToPrevious()
    fun seekTo(positionMs: Long) = player.seekTo(positionMs)

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
        player.removeListener(listener)
        player.release()
        scope.cancel()
    }

    private fun persistState() {
        val state = _playbackState.value
        val queue = _currentQueue.value
        scope.launch {
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
            player.setMediaItems(mediaItems, startIndex, saved.positionMs.coerceAtLeast(0))
            player.prepare()

            _playbackState.value = PlaybackState(
                currentSongId = saved.currentSongId,
                isPlaying = false,
                positionMs = saved.positionMs,
                durationMs = player.duration.coerceAtLeast(0),
                repeatMode = saved.repeatMode,
                shuffleEnabled = saved.shuffleMode
            )
            player.repeatMode = saved.repeatMode
            player.shuffleModeEnabled = saved.shuffleMode
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
