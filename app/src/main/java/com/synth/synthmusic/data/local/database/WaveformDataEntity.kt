package com.synth.synthmusic.data.local.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Cached waveform amplitude envelope for a song.
 *
 * Cascade-deleted when the parent [SongEntity] is removed.
 */
@Entity(
    tableName = "waveform_data",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["song_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["song_id"])]
)
data class WaveformDataEntity(
    @PrimaryKey
    @ColumnInfo(name = "song_id")
    val songId: String,
    @ColumnInfo(name = "amplitudes")
    val amplitudes: List<Float>
)
