package com.synth.synthmusic.domain.model

/**
 * Domain model representing the current playback state.
 *
 * @param currentSongId ID of the currently playing song.
 * @param positionMs Current playback position in milliseconds.
 * @param isPlaying Whether playback is active.
 * @param repeatMode Repeat mode (0 = off, 1 = all, 2 = one).
 * @param shuffleMode Whether shuffle is enabled.
 */
data class PlaybackState(
    val currentSongId: String?,
    val positionMs: Long,
    val isPlaying: Boolean,
    val repeatMode: Int,
    val shuffleMode: Boolean
)
