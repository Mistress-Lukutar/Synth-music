package com.synth.synthmusic.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for the AI action audit log.
 */
@Dao
interface AiActionLogDao {
    @Query("SELECT * FROM ai_action_log ORDER BY timestamp DESC, id DESC")
    fun observeAll(): Flow<List<AiActionLogEntity>>

    @Query("SELECT * FROM ai_action_log WHERE chat_id = :chatId ORDER BY timestamp DESC, id DESC")
    fun observeForChat(chatId: Long): Flow<List<AiActionLogEntity>>

    @Insert
    suspend fun insert(entry: AiActionLogEntity): Long

    @Query("DELETE FROM ai_action_log WHERE chat_id = :chatId")
    suspend fun deleteForChat(chatId: Long)

    @Query("DELETE FROM ai_action_log")
    suspend fun clearAll()
}
