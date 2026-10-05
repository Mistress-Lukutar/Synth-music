package com.synth.synthmusic.data.local.database

import androidx.room.ColumnInfo
import androidx.room.Entity

/**
 * Room entity for a model offered by an AI provider, keyed by
 * (provider_id, model_id). Discovered via the provider's model-list endpoint
 * or entered manually by the user.
 */
@Entity(
    tableName = "ai_models",
    primaryKeys = ["provider_id", "model_id"]
)
data class AiModelEntity(
    @ColumnInfo(name = "provider_id")
    val providerId: Long,
    @ColumnInfo(name = "model_id")
    val modelId: String,
    @ColumnInfo(name = "display_name")
    val displayName: String,
    @ColumnInfo(name = "supports_tools")
    val supportsTools: Boolean,
    @ColumnInfo(name = "supports_vision")
    val supportsVision: Boolean,
    @ColumnInfo(name = "is_pinned")
    val isPinned: Boolean
)
