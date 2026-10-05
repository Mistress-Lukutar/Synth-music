package com.synth.synthmusic.domain.repository

import com.synth.synthmusic.domain.model.AiSettings
import com.synth.synthmusic.domain.model.AiCapability
import com.synth.synthmusic.domain.model.WebSearchProvider
import kotlinx.coroutines.flow.Flow

/**
 * Domain repository for global AI settings (active model, capability
 * kill-switches, optional web search configuration).
 */
interface AiSettingsRepository {

    /** Observes the current AI settings. */
    val settings: Flow<AiSettings>

    /** Returns the current settings snapshot. */
    suspend fun current(): AiSettings

    /** Stores the globally active model reference. */
    suspend fun setActiveModel(providerId: Long, modelId: String)

    /** Clears the globally active model reference. */
    suspend fun clearActiveModel()

    /** Toggles a global capability kill-switch. */
    suspend fun setCapabilityEnabled(capability: AiCapability, enabled: Boolean)

    /** Stores the optional web-search backend selection. */
    suspend fun setWebSearchProvider(provider: WebSearchProvider)

    /**
     * Stores the web-search API key. The repository encrypts the plaintext
     * before persisting; null clears the key.
     */
    suspend fun setWebSearchKey(plaintext: String?)

    /** Decrypts and returns the web-search API key, or null. */
    suspend fun getWebSearchKey(): String?
}
