package com.synth.synthmusic.domain.model

/**
 * Domain model of a chat conversation.
 */
data class AiChat(
    val id: Long,
    val title: String,
    val assistantId: Long?,
    val providerId: Long?,
    val modelId: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val isArchived: Boolean
)

/**
 * Domain model of one message in a chat. [parts] carries the ordered content
 * (text segments, tool calls, tool results, image attachments).
 */
data class AiChatMessage(
    val id: Long,
    val chatId: Long,
    val role: AiRole,
    val parts: List<AiMessagePart>,
    val status: AiMessageStatus,
    val inputTokens: Int?,
    val outputTokens: Int?,
    val createdAt: Long
) {
    /** Concatenated text of all text parts. */
    val text: String
        get() = parts.filterIsInstance<AiMessagePart.Text>().joinToString("") { it.text }

    /** Returns a copy of this message with [parts] replaced. */
    fun withParts(parts: List<AiMessagePart>): AiChatMessage = copy(parts = parts)
}
