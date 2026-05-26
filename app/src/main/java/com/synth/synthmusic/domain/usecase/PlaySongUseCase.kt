package com.synth.synthmusic.domain.usecase

import com.synth.synthmusic.data.media.MediaPlaybackManager
import com.synth.synthmusic.domain.model.Song

/**
 * Use case for starting playback of a list of songs.
 *
 * @param playbackManager the media playback manager controlling the queue.
 */
class PlaySongUseCase(
    private val playbackManager: MediaPlaybackManager
) {

    /**
     * Plays the provided songs starting at the given index.
     *
     * @param songs the list of songs to enqueue.
     * @param startIndex the zero-based index of the song to start playback from.
     */
    operator fun invoke(songs: List<Song>, startIndex: Int = 0) {
        playbackManager.playSongs(songs, startIndex)
    }
}
