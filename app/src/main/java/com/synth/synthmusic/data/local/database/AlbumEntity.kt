package com.synth.synthmusic.data.local.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing an album aggregate in the local database.
 */
@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "artist")
    val artist: String,
    @ColumnInfo(name = "year")
    val year: Int,
    @ColumnInfo(name = "artwork_uri")
    val artworkUri: String?,
    @ColumnInfo(name = "song_count")
    val songCount: Int,
    @ColumnInfo(name = "total_duration_ms")
    val totalDurationMs: Long
)
