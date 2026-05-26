package com.synth.synthmusic.data.local.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached waveform amplitude envelope for a song.
 */
@Entity(tableName = "waveform_data")
data class WaveformDataEntity(
    @PrimaryKey
    @ColumnInfo(name = "song_id")
    val songId: String,
    @ColumnInfo(name = "amplitudes")
    val amplitudes: List<Float>
)
