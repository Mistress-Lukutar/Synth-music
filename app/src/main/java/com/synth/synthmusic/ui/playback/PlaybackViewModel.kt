package com.synth.synthmusic.ui.playback

import androidx.lifecycle.ViewModel
import com.synth.synthmusic.data.media.MediaPlaybackManager
import com.synth.synthmusic.domain.model.Song
import kotlinx.coroutines.flow.StateFlow

/**
 * Unified ViewModel for playback control actions used across multiple screens
 * (mini-player, now-playing, queue, etc.) to avoid duplicating playback logic.
 */
class PlaybackViewModel(
    private val playbackManager: MediaPlaybackManager
) : ViewModel() {

    val playbackState: StateFlow<MediaPlaybackManager.PlaybackState> = playbackManager.playbackState
    val currentQueue: StateFlow<List<Song>> = playbackManager.currentQueue

    fun playPause() = playbackManager.playPause()

    fun next() = playbackManager.next()

    fun previous() = playbackManager.previous()

    fun seekTo(positionMs: Long) = playbackManager.seekTo(positionMs)
}
