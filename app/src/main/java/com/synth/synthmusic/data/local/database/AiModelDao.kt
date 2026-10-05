package com.synth.synthmusic.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for AI models, keyed by (provider_id, model_id).
 */
@Dao
interface AiModelDao {
    @Query("SELECT * FROM ai_models ORDER BY display_name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<AiModelEntity>>

    @Query("SELECT * FROM ai_models WHERE provider_id = :providerId ORDER BY display_name COLLATE NOCASE ASC")
    fun observeForProvider(providerId: Long): Flow<List<AiModelEntity>>

    @Query("SELECT * FROM ai_models WHERE provider_id = :providerId")
    suspend fun getForProvider(providerId: Long): List<AiModelEntity>

    @Query("SELECT * FROM ai_models WHERE provider_id = :providerId AND model_id = :modelId")
    suspend fun get(providerId: Long, modelId: String): AiModelEntity?

    @Query("SELECT * FROM ai_models WHERE is_pinned = 1")
    suspend fun getPinned(): List<AiModelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(models: List<AiModelEntity>)

    @Query("UPDATE ai_models SET is_pinned = :pinned WHERE provider_id = :providerId AND model_id = :modelId")
    suspend fun setPinned(providerId: Long, modelId: String, pinned: Boolean)

    @Query("DELETE FROM ai_models WHERE provider_id = :providerId")
    suspend fun deleteForProvider(providerId: Long)
}
