package com.synth.synthmusic.domain.model

/**
 * Domain model representing a single audio track.
 *
 * @param id Unique identifier (content URI hash or persistent ID).
 * @param title Track title.
 * @param artist Track artist.
 * @param album Album name.
 * @param albumArtist Album artist.
 * @param durationMs Track duration in milliseconds.
 * @param trackNumber Track number in album.
 * @param year Release year.
 * @param genre Music genre.
 * @param comment User comment.
 * @param path File system path.
 * @param uri Content URI.
 * @param bitrate Bitrate in kbps.
 * @param sampleRate Sample rate in Hz.
 * @param fileSize File size in bytes.
 * @param artworkUri URI for embedded or folder artwork.
 * @param rating Star rating from 0.0 to 5.0.
 * @param playCount Number of times played.
 * @param lastPlayed Timestamp of last playback.
 * @param dateAdded Timestamp when added to library.
 * @param dateModified Timestamp of last file modification.
 * @param lyrics Embedded or external lyrics.
 * @param isFavorite Whether the song is marked as favorite.
 */
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val durationMs: Long,
    val trackNumber: Int,
    val year: Int,
    val genre: String,
    val comment: String,
    val path: String,
    val uri: String,
    val bitrate: Int,
    val sampleRate: Int,
    val fileSize: Long,
    val artworkUri: String?,
    val rating: Float,
    val playCount: Int,
    val lastPlayed: Long?,
    val dateAdded: Long,
    val dateModified: Long,
    val lyrics: String?,
    val isFavorite: Boolean = false
)
