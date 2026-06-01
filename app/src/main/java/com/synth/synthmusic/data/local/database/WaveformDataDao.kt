package com.synth.synthmusic.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Data access object for cached waveform data.
 */
@Dao
interface WaveformDataDao {
    @Query("SELECT * FROM waveform_data WHERE song_id = :songId")
    suspend fun getBySongId(songId: String): WaveformDataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(data: WaveformDataEntity)

    @Query("DELETE FROM waveform_data WHERE song_id = :songId")
    suspend fun deleteBySongId(songId: String)

    @Query("DELETE FROM waveform_data WHERE song_id NOT IN (SELECT id FROM songs)")
    suspend fun deleteOrphaned()

    @Query("SELECT song_id FROM waveform_data")
    suspend fun getAllSongIds(): List<String>
}
