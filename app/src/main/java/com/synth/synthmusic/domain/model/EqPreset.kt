package com.synth.synthmusic.domain.model

/**
 * Domain model representing an equalizer preset.
 *
 * @param id Unique identifier (auto-generated).
 * @param name Preset name.
 * @param bandValues Gain values for 5 bands (60Hz, 230Hz, 910Hz, 3.6kHz, 14kHz).
 */
data class EqPreset(
    val id: Long = 0,
    val name: String,
    val bandValues: List<Int>
)
