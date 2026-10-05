package com.synth.synthmusic.data.local.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for an AI assistant persona (system instruction + avatar +
 * default capability grants). Built-ins are seeded idempotently and identified
 * by [builtinKey]; user-created assistants have `builtin_key = null`.
 */
@Entity(tableName = "ai_assistants")
data class AiAssistantEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "builtin_key")
    val builtinKey: String?,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "description")
    val description: String,
    @ColumnInfo(name = "system_prompt")
    val systemPrompt: String,
    @ColumnInfo(name = "avatar_icon")
    val avatarIcon: String,
    @ColumnInfo(name = "avatar_color_index")
    val avatarColorIndex: Int,
    @ColumnInfo(name = "default_grants_json")
    val defaultGrantsJson: String,
    @ColumnInfo(name = "is_builtin")
    val isBuiltin: Boolean,
    @ColumnInfo(name = "sort_order")
    val sortOrder: Int
)
