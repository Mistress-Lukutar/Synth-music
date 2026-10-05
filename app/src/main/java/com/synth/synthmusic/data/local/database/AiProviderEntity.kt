package com.synth.synthmusic.data.local.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity for a user-configured AI provider (API endpoint + credentials).
 *
 * [apiKeyEncrypted] holds a payload produced by
 * [com.synth.synthmusic.data.ai.crypto.ApiKeyCipher]; the plaintext key never
 * reaches the database.
 */
@Entity(tableName = "ai_providers")
data class AiProviderEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,
    @ColumnInfo(name = "label")
    val label: String,
    @ColumnInfo(name = "protocol")
    val protocol: String,
    @ColumnInfo(name = "base_url")
    val baseUrl: String,
    @ColumnInfo(name = "api_key_encrypted")
    val apiKeyEncrypted: String,
    @ColumnInfo(name = "created_at")
    val createdAt: Long
)
