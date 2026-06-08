package com.synth.synthmusic.ui.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synth.synthmusic.data.media.MediaPlaybackManager
import com.synth.synthmusic.domain.model.PlaybackState
import com.synth.synthmusic.domain.model.Song
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/**
 * Unified ViewModel for playback control actions used across multiple screens
 * (mini-player, now-playing, queue, etc.) to avoid duplicating playback logic.
 *
 * Exposes [currentPositionMs] and [currentDurationMs] as separate high-frequency
 * flows so that UI recompositions are scoped to position changes only.
 */
class PlaybackViewModel(
    private val playbackManager: MediaPlaybackManager
) : ViewModel() {

    val playbackState: StateFlow<PlaybackState> = playbackManager.playbackState
    val currentQueue: StateFlow<List<Song>> = playbackManager.currentQueue
    val currentPositionMs: StateFlow<Long> = playbackManager.currentPositionMs
    val currentDurationMs: StateFlow<Long> = playbackManager.currentDurationMs

    val currentSong: StateFlow<Song?> = combine(
        playbackManager.playbackState,
        playbackManager.currentQueue
    ) { playback, queue ->
        queue.find { it.id == playback.currentSongId }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    fun playPause() = playbackManager.playPause()

    fun next() = playbackManager.next()

    fun previous() = playbackManager.previous()

    fun seekTo(positionMs: Long) = playbackManager.seekTo(positionMs)
}
