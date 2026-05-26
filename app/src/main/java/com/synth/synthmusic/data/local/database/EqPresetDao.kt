package com.synth.synthmusic.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for equalizer presets.
 */
@Dao
interface EqPresetDao {
    @Query("SELECT * FROM eq_presets ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<EqPresetEntity>>

    @Query("SELECT * FROM eq_presets WHERE id = :presetId")
    suspend fun getById(presetId: Long): EqPresetEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(preset: EqPresetEntity): Long

    @Update
    suspend fun update(preset: EqPresetEntity)

    @Query("DELETE FROM eq_presets WHERE id = :presetId")
    suspend fun deleteById(presetId: Long)
}
