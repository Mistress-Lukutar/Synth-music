package com.synth.synthmusic.domain.model

/**
 * Domain model representing the current playback state.
 *
 * Does **not** include position or duration — those are exposed
 * as separate high-frequency flows from [PlaybackRepository]
 * to avoid unnecessary recompositions.
 *
 * @param currentSongId ID of the currently playing song.
 * @param isPlaying Whether playback is active.
 * @param repeatMode Repeat mode (0 = off, 1 = all, 2 = one).
 * @param shuffleEnabled Whether shuffle is enabled.
 */
data class PlaybackState(
    val currentSongId: String? = null,
    val isPlaying: Boolean = false,
    val repeatMode: Int = 0,
    val shuffleEnabled: Boolean = false
)
