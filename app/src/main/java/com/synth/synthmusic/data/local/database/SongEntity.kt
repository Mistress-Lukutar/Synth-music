package com.synth.synthmusic.data.local.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a song in the local database.
 */
@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "artist")
    val artist: String,
    @ColumnInfo(name = "album")
    val album: String,
    @ColumnInfo(name = "album_artist")
    val albumArtist: String,
    @ColumnInfo(name = "duration_ms")
    val durationMs: Long,
    @ColumnInfo(name = "track_number")
    val trackNumber: Int,
    @ColumnInfo(name = "year")
    val year: Int,
    @ColumnInfo(name = "genre")
    val genre: String,
    @ColumnInfo(name = "comment")
    val comment: String,
    @ColumnInfo(name = "path")
    val path: String,
    @ColumnInfo(name = "uri")
    val uri: String,
    @ColumnInfo(name = "bitrate")
    val bitrate: Int,
    @ColumnInfo(name = "sample_rate")
    val sampleRate: Int,
    @ColumnInfo(name = "file_size")
    val fileSize: Long,
    @ColumnInfo(name = "artwork_uri")
    val artworkUri: String?,
    @ColumnInfo(name = "rating")
    val rating: Float,
    @ColumnInfo(name = "play_count")
    val playCount: Int,
    @ColumnInfo(name = "last_played")
    val lastPlayed: Long?,
    @ColumnInfo(name = "date_added")
    val dateAdded: Long,
    @ColumnInfo(name = "date_modified")
    val dateModified: Long,
    @ColumnInfo(name = "lyrics")
    val lyrics: String?,
    @ColumnInfo(name = "is_favorite")
    val isFavorite: Boolean = false,
    @ColumnInfo(name = "replay_gain_track_db")
    val replayGainTrackDb: Float? = null,
    @ColumnInfo(name = "replay_gain_album_db")
    val replayGainAlbumDb: Float? = null
)
