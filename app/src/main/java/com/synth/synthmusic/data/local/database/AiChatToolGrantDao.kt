package com.synth.synthmusic.data.local.database

import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Dao
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for per-chat capability grants.
 */
@Dao
interface AiChatToolGrantDao {
    @Query("SELECT * FROM ai_chat_tool_grants WHERE chat_id = :chatId")
    fun observeForChat(chatId: Long): Flow<List<AiChatToolGrantEntity>>

    @Query("SELECT * FROM ai_chat_tool_grants WHERE chat_id = :chatId")
    suspend fun getForChat(chatId: Long): List<AiChatToolGrantEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(grant: AiChatToolGrantEntity)

    @Query("DELETE FROM ai_chat_tool_grants WHERE chat_id = :chatId")
    suspend fun deleteForChat(chatId: Long)
}
