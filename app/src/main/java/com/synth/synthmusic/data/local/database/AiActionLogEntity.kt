package com.synth.synthmusic.data.local.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for the AI action audit log. One row per tool call, including
 * denied ones. [outcome] uses the string values of
 * [com.synth.synthmusic.domain.model.AiActionOutcome].
 */
@Entity(
    tableName = "ai_action_log",
    indices = [Index("chat_id")]
)
data class AiActionLogEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "chat_id")
    val chatId: Long?,
    @ColumnInfo(name = "timestamp")
    val timestamp: Long,
    @ColumnInfo(name = "tool_name")
    val toolName: String,
    @ColumnInfo(name = "args_json")
    val argsJson: String,
    @ColumnInfo(name = "outcome")
    val outcome: String,
    @ColumnInfo(name = "summary")
    val summary: String
)
