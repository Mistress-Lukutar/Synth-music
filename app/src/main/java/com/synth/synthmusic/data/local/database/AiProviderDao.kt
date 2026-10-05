package com.synth.synthmusic.data.local.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for AI providers.
 */
@Dao
interface AiProviderDao {
    @Query("SELECT * FROM ai_providers ORDER BY created_at ASC")
    fun observeAll(): Flow<List<AiProviderEntity>>

    @Query("SELECT * FROM ai_providers ORDER BY created_at ASC")
    suspend fun getAll(): List<AiProviderEntity>

    @Query("SELECT * FROM ai_providers WHERE id = :providerId")
    suspend fun getById(providerId: Long): AiProviderEntity?

    @Insert
    suspend fun insert(provider: AiProviderEntity): Long

    @Update
    suspend fun update(provider: AiProviderEntity)

    @Delete
    suspend fun delete(provider: AiProviderEntity)

    @Query("SELECT COUNT(*) FROM ai_models WHERE provider_id = :providerId")
    suspend fun modelCount(providerId: Long): Int
}
