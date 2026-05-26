package com.synth.synthmusic.domain.model

/**
 * Domain model representing a music artist.
 *
 * @param id Unique identifier.
 * @param name Artist name.
 * @param songCount Total number of songs by this artist.
 * @param albumCount Total number of albums by this artist.
 */
data class Artist(
    val id: String,
    val name: String,
    val songCount: Int,
    val albumCount: Int
)
