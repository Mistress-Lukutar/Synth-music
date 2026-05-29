package com.synth.synthmusic.domain.model

/**
 * Domain model representing a user-created playlist.
 *
 * @param id Unique identifier (auto-generated).
 * @param name Playlist name.
 * @param createdAt Creation timestamp.
 * @param songCount Number of tracks in the playlist.
 * @param artworkUri Artwork URI snapshot (first track's art).
 */
data class Playlist(
    val id: Long = 0,
    val name: String,
    val createdAt: Long,
    val songCount: Int,
    val artworkUri: String? = null
)
