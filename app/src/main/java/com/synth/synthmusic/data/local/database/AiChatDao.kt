package com.synth.synthmusic.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for AI chats.
 */
@Dao
interface AiChatDao {
    @Query("SELECT * FROM ai_chats WHERE is_archived = 0 ORDER BY updated_at DESC")
    fun observeAll(): Flow<List<AiChatEntity>>

    @Query("SELECT * FROM ai_chats WHERE id = :chatId")
    fun observeById(chatId: Long): Flow<AiChatEntity?>

    @Query("SELECT * FROM ai_chats WHERE id = :chatId")
    suspend fun getById(chatId: Long): AiChatEntity?

    @Insert
    suspend fun insert(chat: AiChatEntity): Long

    @Update
    suspend fun update(chat: AiChatEntity)

    @Query("UPDATE ai_chats SET title = :title WHERE id = :chatId")
    suspend fun updateTitle(chatId: Long, title: String)

    @Query("UPDATE ai_chats SET updated_at = :timestamp WHERE id = :chatId")
    suspend fun touch(chatId: Long, timestamp: Long)

    @Query("DELETE FROM ai_chats WHERE id = :chatId")
    suspend fun deleteById(chatId: Long)
}
