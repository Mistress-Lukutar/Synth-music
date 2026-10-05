package com.synth.synthmusic.domain.repository

import com.synth.synthmusic.domain.model.AiChat
import com.synth.synthmusic.domain.model.AiChatMessage
import com.synth.synthmusic.domain.model.AiCapability
import com.synth.synthmusic.domain.model.AiMessageStatus
import kotlinx.coroutines.flow.Flow

/**
 * Domain repository for AI chats and their messages.
 */
interface AiChatRepository {

    /** Observes non-archived chats, newest activity first. */
    fun observeChats(): Flow<List<AiChat>>

    /** Observes a single chat. */
    fun observeChat(chatId: Long): Flow<AiChat?>

    /** Returns a chat snapshot, or null. */
    suspend fun getChat(chatId: Long): AiChat?

    /**
     * Creates a chat.
     *
     * @param providerId optional model snapshot; falls back to the global
     * default when null.
     * @return the new chat id.
     */
    suspend fun createChat(
        title: String,
        assistantId: Long?,
        providerId: Long?,
        modelId: String?
    ): Long

    /** Updates the chat title. */
    suspend fun updateTitle(chatId: Long, title: String)

    /** Bumps the chat's updated_at timestamp. */
    suspend fun touch(chatId: Long)

    /** Deletes the chat with its messages and audit entries. */
    suspend fun deleteChat(chatId: Long)

    /** Clears all messages of a chat. */
    suspend fun clearMessages(chatId: Long)

    /** Deletes all messages after (and including) [messageId] — regenerate support. */
    suspend fun deleteFrom(chatId: Long, messageId: Long)

    // --- Messages ---

    /** Observes all messages of a chat in insertion order. */
    fun observeMessages(chatId: Long): Flow<List<AiChatMessage>>

    /** Returns a message list snapshot in insertion order. */
    suspend fun getMessages(chatId: Long): List<AiChatMessage>

    /** Returns a message snapshot, or null. */
    suspend fun getMessage(messageId: Long): AiChatMessage?

    /** Appends a message and returns its row id. */
    suspend fun appendMessage(message: AiChatMessage): Long

    /** Replaces the stored content of a message. */
    suspend fun updateMessage(message: AiChatMessage)

    /** Updates only the status of a message. */
    suspend fun updateMessageStatus(messageId: Long, status: AiMessageStatus)
}
