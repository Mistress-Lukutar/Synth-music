package com.synth.synthmusic.data.local.database

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * Room entity for a capability grant of a single chat, keyed by
 * (chat_id, capability). Grants never persist across chats and can never
 * exceed the global capability kill-switches in AI settings.
 */
@Entity(
    tableName = "ai_chat_tool_grants",
    primaryKeys = ["chat_id", "capability"]
)
data class AiChatToolGrantEntity(
    @ColumnInfo(name = "chat_id")
    val chatId: Long,
    @ColumnInfo(name = "capability")
    val capability: String,
    @ColumnInfo(name = "granted")
    val granted: Boolean,
    @ColumnInfo(name = "granted_at")
    val grantedAt: Long
)
