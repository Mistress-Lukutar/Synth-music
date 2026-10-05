package com.synth.synthmusic.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for AI assistant personas.
 */
@Dao
interface AiAssistantDao {
    @Query("SELECT * FROM ai_assistants ORDER BY sort_order ASC, id ASC")
    fun observeAll(): Flow<List<AiAssistantEntity>>

    @Query("SELECT * FROM ai_assistants ORDER BY sort_order ASC, id ASC")
    suspend fun getAll(): List<AiAssistantEntity>

    @Query("SELECT * FROM ai_assistants WHERE id = :assistantId")
    suspend fun getById(assistantId: Long): AiAssistantEntity?

    @Query("SELECT * FROM ai_assistants WHERE builtin_key = :builtinKey")
    suspend fun getByBuiltinKey(builtinKey: String): AiAssistantEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(assistant: AiAssistantEntity): Long

    @Update
    suspend fun update(assistant: AiAssistantEntity)

    @Query("DELETE FROM ai_assistants WHERE id = :assistantId")
    suspend fun deleteById(assistantId: Long)
}
