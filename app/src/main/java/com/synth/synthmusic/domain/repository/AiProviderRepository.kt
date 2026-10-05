package com.synth.synthmusic.domain.repository

import com.synth.synthmusic.domain.model.AiModel
import com.synth.synthmusic.domain.model.AiProtocol
import com.synth.synthmusic.domain.model.AiProvider
import kotlinx.coroutines.flow.Flow

/**
 * Domain repository for user-configured AI providers.
 *
 * Implementations handle API-key encryption/decryption; domain objects carry
 * only the encrypted payload and plaintext keys are resolved separately for
 * request building, never exposed to the UI beyond masked previews.
 */
interface AiProviderRepository {

    /** Observes all providers in creation order. */
    fun observeProviders(): Flow<List<AiProvider>>

    /** Returns all providers. */
    suspend fun getProviders(): List<AiProvider>

    /** Returns the provider with [providerId], or null. */
    suspend fun getProvider(providerId: Long): AiProvider?

    /**
     * Adds a provider, encrypting [apiKey].
     *
     * @return the new provider id.
     */
    suspend fun addProvider(
        label: String,
        protocol: AiProtocol,
        baseUrl: String,
        apiKey: String
    ): Long

    /**
     * Updates a provider. When [newApiKey] is non-null it replaces the stored
     * (re-encrypted) key; otherwise the existing key is kept.
     */
    suspend fun updateProvider(
        provider: AiProvider,
        newApiKey: String?
    )

    /**
     * Deletes a provider and all its models.
     */
    suspend fun deleteProvider(providerId: Long)

    /**
     * Decrypts and returns the API key of [providerId], or null.
     */
    suspend fun getApiKey(providerId: Long): String?

    /** Returns a masked preview (e.g. `sk-…abcd`) of the provider's key. */
    suspend fun getMaskedKey(providerId: Long): String?
}

/**
 * Domain repository for AI models offered by providers.
 */
interface AiModelRepository {

    /** Observes all models of a provider. */
    fun observeModels(providerId: Long): Flow<List<AiModel>>

    /** Returns all models of a provider. */
    suspend fun getModels(providerId: Long): List<AiModel>

    /** Returns the model with (providerId, modelId), or null. */
    suspend fun getModel(providerId: Long, modelId: String): AiModel?

    /** Returns every pinned model across providers. */
    suspend fun getPinnedModels(): List<AiModel>

    /** Inserts or updates a batch of models. */
    suspend fun upsertModels(models: List<AiModel>)

    /** Updates the pinned flag of one model. */
    suspend fun setPinned(providerId: Long, modelId: String, pinned: Boolean)

    /** Deletes all models of a provider. */
    suspend fun deleteModelsForProvider(providerId: Long)
}
