package com.synth.synthmusic.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for AI chat messages.
 */
@Dao
interface AiChatMessageDao {
    @Query("SELECT * FROM ai_chat_messages WHERE chat_id = :chatId ORDER BY id ASC")
    fun observeForChat(chatId: Long): Flow<List<AiChatMessageEntity>>

    @Query("SELECT * FROM ai_chat_messages WHERE chat_id = :chatId ORDER BY id ASC")
    suspend fun getForChat(chatId: Long): List<AiChatMessageEntity>

    @Query("SELECT * FROM ai_chat_messages WHERE id = :messageId")
    suspend fun getById(messageId: Long): AiChatMessageEntity?

    @Insert
    suspend fun insert(message: AiChatMessageEntity): Long

    @Update
    suspend fun update(message: AiChatMessageEntity)

    @Query("UPDATE ai_chat_messages SET status = :status WHERE id = :messageId")
    suspend fun updateStatus(messageId: Long, status: String)

    @Query("DELETE FROM ai_chat_messages WHERE chat_id = :chatId")
    suspend fun deleteForChat(chatId: Long)

    @Query("DELETE FROM ai_chat_messages WHERE chat_id = :chatId AND id >= :fromMessageId")
    suspend fun deleteFrom(chatId: Long, fromMessageId: Long)
}
