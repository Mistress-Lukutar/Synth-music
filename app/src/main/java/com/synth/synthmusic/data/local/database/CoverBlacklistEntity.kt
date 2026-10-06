package com.synth.synthmusic.data.local.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for the cover blacklist: SHA-256 hashes of embedded artwork
 * bytes flagged as garbage (e.g. site banners baked into downloaded files).
 * One row per distinct image; matching is content-based so a single entry
 * covers every track carrying the same picture.
 */
@Entity(tableName = "cover_blacklist")
data class CoverBlacklistEntity(
    /** Lowercase hex SHA-256 of the embedded image bytes. */
    @PrimaryKey
    @ColumnInfo(name = "hash")
    val hash: String,
    @ColumnInfo(name = "width")
    val width: Int,
    @ColumnInfo(name = "height")
    val height: Int,
    @ColumnInfo(name = "byte_size")
    val byteSize: Int,
    /** Song the cover was first blacklisted from, for traceability. */
    @ColumnInfo(name = "source_song_id")
    val sourceSongId: String?,
    @ColumnInfo(name = "reason")
    val reason: String,
    @ColumnInfo(name = "blacklisted_at")
    val blacklistedAt: Long
)
