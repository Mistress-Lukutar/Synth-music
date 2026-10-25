package com.synth.synthmusic.domain.model

/**
 * Domain model of a chat conversation.
 *
 * [activeLeafId] pins the branch of the conversation tree currently shown;
 * `null` means the newest message.
 */
data class AiChat(
    val id: Long,
    val title: String,
    val assistantId: Long?,
    val providerId: Long?,
    val modelId: String?,
    val activeLeafId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val isArchived: Boolean
)

/**
 * Domain model of one message in a chat. [parts] carries the ordered content
 * (text segments, tool calls, tool results, image attachments).
 *
 * [parentId] is the id of the message this one replies to (null = first
 * message of the chat); sibling messages sharing a parent and a role are
 * alternative variants of the same turn.
 */
data class AiChatMessage(
    val id: Long,
    val chatId: Long,
    val role: AiRole,
    val parentId: Long? = null,
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
