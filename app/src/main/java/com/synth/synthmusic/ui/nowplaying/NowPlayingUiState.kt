package com.synth.synthmusic.ui.nowplaying

import com.synth.synthmusic.domain.model.Song

/**
 * UI state for the now playing screen.
 */
data class NowPlayingUiState(
    val song: Song? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val repeatMode: Int = androidx.media3.common.Player.REPEAT_MODE_OFF,
    val shuffleEnabled: Boolean = false,
    val rating: Float = 0f,
    val isFavorite: Boolean = false,
    val waveformAmplitudes: List<Float> = emptyList()
)
