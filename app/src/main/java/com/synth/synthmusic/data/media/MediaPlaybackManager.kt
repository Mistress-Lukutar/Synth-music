package com.synth.synthmusic.data.media

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.synth.synthmusic.domain.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Wrapper around ExoPlayer providing playback control and state observation.
 */
class MediaPlaybackManager(
    context: Context
) {
    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _currentQueue = MutableStateFlow<List<Song>>(emptyList())
    val currentQueue: StateFlow<List<Song>> = _currentQueue.asStateFlow()

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
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playbackState.update {
                it.copy(
                    isPlaying = isPlaying,
                    currentSongId = player.currentMediaItem?.mediaId
                )
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            _playbackState.update {
                it.copy(
                    currentSongId = mediaItem?.mediaId,
                    positionMs = 0,
                    durationMs = player.duration.coerceAtLeast(0)
                )
            }
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
        }
    }

    init {
        player.addListener(listener)
    }

    fun playSongs(songs: List<Song>, startIndex: Int = 0) {
        _currentQueue.value = songs
        val mediaItems = songs.map { song ->
            MediaItem.Builder()
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
        }
        player.setMediaItems(mediaItems, startIndex, 0)
        player.prepare()
        player.play()
    }

    fun playQueueItem(index: Int) {
        player.seekTo(index, 0)
        player.play()
    }

    fun removeFromQueue(index: Int) {
        val queue = _currentQueue.value.toMutableList()
        if (index in queue.indices) {
            queue.removeAt(index)
            _currentQueue.value = queue
            player.removeMediaItem(index)
        }
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
    }

    fun cycleRepeatMode() {
        val next = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        player.repeatMode = next
        _playbackState.update { it.copy(repeatMode = next) }
    }

    fun release() {
        player.removeListener(listener)
        player.release()
    }

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
