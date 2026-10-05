package com.synth.synthmusic.data.local.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for one message in an AI chat.
 *
 * [contentJson] serializes an ordered list of message parts (text segments,
 * tool calls, tool results, image attachment refs) as defined by the chat
 * engine's [com.synth.synthmusic.domain.model.AiMessagePart] hierarchy.
 * [status] uses the string values of [com.synth.synthmusic.domain.model.AiMessageStatus].
 */
@Entity(
    tableName = "ai_chat_messages",
    indices = [Index("chat_id")]
)
data class AiChatMessageEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "chat_id")
    val chatId: Long,
    @ColumnInfo(name = "role")
    val role: String,
    @ColumnInfo(name = "content_json")
    val contentJson: String,
    @ColumnInfo(name = "status")
    val status: String,
    @ColumnInfo(name = "input_tokens")
    val inputTokens: Int?,
    @ColumnInfo(name = "output_tokens")
    val outputTokens: Int?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
