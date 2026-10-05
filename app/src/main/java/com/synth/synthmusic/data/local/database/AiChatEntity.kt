package com.synth.synthmusic.data.local.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for a chat conversation with the AI assistant.
 *
 * [providerId] and [modelId] snapshot the model chosen at chat creation (or
 * overridden later); `null` falls back to the global default model.
 */
@Entity(tableName = "ai_chats")
data class AiChatEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "assistant_id")
    val assistantId: Long?,
    @ColumnInfo(name = "provider_id")
    val providerId: Long?,
    @ColumnInfo(name = "model_id")
    val modelId: String?,
    @ColumnInfo(name = "created_at")
    val createdAt: Long,
    @ColumnInfo(name = "updated_at")
    val updatedAt: Long,
    @ColumnInfo(name = "is_archived")
    val isArchived: Boolean
)
