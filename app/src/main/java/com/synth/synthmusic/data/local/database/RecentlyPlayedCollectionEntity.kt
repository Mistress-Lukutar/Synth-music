package com.synth.synthmusic.data.local.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a recently played collection (album, artist, or playlist).
 */
@Entity(tableName = "recently_played_collections")
data class RecentlyPlayedCollectionEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "type")
    val type: String,

    @ColumnInfo(name = "identifier")
    val identifier: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "extra")
    val extra: String?,

    @ColumnInfo(name = "artwork_uri")
    val artworkUri: String?,

    @ColumnInfo(name = "played_at")
    val playedAt: Long
)
