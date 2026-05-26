package com.synth.synthmusic.domain.model

/**
 * Domain model representing a timestamp bookmark within a track.
 *
 * @param id Unique identifier (auto-generated).
 * @param songId Reference to the song.
 * @param positionMs Bookmark position in milliseconds.
 * @param label User-defined label.
 * @param createdAt Creation timestamp.
 */
data class Bookmark(
    val id: Long = 0,
    val songId: String,
    val positionMs: Long,
    val label: String,
    val createdAt: Long
)
