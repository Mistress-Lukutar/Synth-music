package com.synth.synthmusic.ui.queue

import androidx.lifecycle.ViewModel
import com.synth.synthmusic.data.media.MediaPlaybackManager
import kotlinx.coroutines.flow.StateFlow

/**
 * ViewModel for the playback queue screen.
 */
class QueueViewModel(
    private val playbackManager: MediaPlaybackManager
) : ViewModel() {

    val queue: StateFlow<List<com.synth.synthmusic.domain.model.Song>> = playbackManager.currentQueue
    val playbackState: StateFlow<MediaPlaybackManager.PlaybackState> = playbackManager.playbackState

    fun playItem(index: Int) {
        playbackManager.playQueueItem(index)
    }

    fun removeItem(index: Int) {
        playbackManager.removeFromQueue(index)
    }
}
