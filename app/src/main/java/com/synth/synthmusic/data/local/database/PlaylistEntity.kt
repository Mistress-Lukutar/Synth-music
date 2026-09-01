package com.synth.synthmusic.data.local.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a user playlist.
 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "song_count")
    val songCount: Int,
    @ColumnInfo(name = "artwork_uri")
    val artworkUri: String?,
    @ColumnInfo(name = "has_custom_artwork")
    val hasCustomArtwork: Boolean = false,
    @ColumnInfo(name = "is_fixed")
    val isFixed: Boolean = false
)
