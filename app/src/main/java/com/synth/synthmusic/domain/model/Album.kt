package com.synth.synthmusic.domain.model

/**
 * Domain model representing a music album.
 *
 * @param id Unique identifier (hash of "album|albumArtist").
 * @param title Album title.
 * @param artist Album artist.
 * @param year Release year.
 * @param artworkUri URI for album artwork.
 * @param songCount Number of tracks in the album.
 * @param totalDurationMs Total duration of all tracks in milliseconds.
 */
data class Album(
    val id: String,
    val title: String,
    val artist: String,
    val year: Int,
    val artworkUri: String?,
    val songCount: Int,
    val totalDurationMs: Long
)
