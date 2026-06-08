package com.synth.synthmusic.ui.playback

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synth.synthmusic.data.media.PlaybackRepository
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
    private val playbackRepository: PlaybackRepository
) : ViewModel() {

    val playbackState: StateFlow<PlaybackState> = playbackRepository.playbackState
    val currentQueue: StateFlow<List<Song>> = playbackRepository.currentQueue
    val currentPositionMs: StateFlow<Long> = playbackRepository.currentPositionMs
    val currentDurationMs: StateFlow<Long> = playbackRepository.currentDurationMs

    val currentSong: StateFlow<Song?> = combine(
        playbackRepository.playbackState,
        playbackRepository.currentQueue
    ) { playback, queue ->
        queue.find { it.id == playback.currentSongId }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = null
    )

    fun playPause() = playbackRepository.playPause()

    fun next() = playbackRepository.next()

    fun previous() = playbackRepository.previous()

    fun seekTo(positionMs: Long) = playbackRepository.seekTo(positionMs)
}
