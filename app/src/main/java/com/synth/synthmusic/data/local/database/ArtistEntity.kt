package com.synth.synthmusic.data.local.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing an artist aggregate in the local database.
 */
@Entity(tableName = "artists")
data class ArtistEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "song_count")
    val songCount: Int,
    @ColumnInfo(name = "album_count")
    val albumCount: Int,
    @ColumnInfo(name = "artwork_uri")
    val artworkUri: String?
)
