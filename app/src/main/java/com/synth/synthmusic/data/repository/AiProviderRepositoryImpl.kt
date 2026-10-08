package com.synth.synthmusic.data.repository

import com.synth.synthmusic.data.ai.crypto.ApiKeyCipher
import com.synth.synthmusic.data.local.database.AiModelDao
import com.synth.synthmusic.data.local.database.AiProviderDao
import com.synth.synthmusic.data.local.database.toDomain
import com.synth.synthmusic.data.local.database.toEntity
import com.synth.synthmusic.domain.model.AiModel
import com.synth.synthmusic.domain.model.AiProtocol
import com.synth.synthmusic.domain.model.AiProvider
import com.synth.synthmusic.domain.model.ModelLimitsSource
import com.synth.synthmusic.domain.repository.AiModelRepository
import com.synth.synthmusic.domain.repository.AiProviderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed [AiProviderRepository]. Encrypts API keys on write, decrypts
 * only on explicit request for request building.
 */
class AiProviderRepositoryImpl(
    private val providerDao: AiProviderDao,
    private val modelDao: AiModelDao,
    private val cipher: ApiKeyCipher
) : AiProviderRepository {

    override fun observeProviders(): Flow<List<AiProvider>> =
        providerDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getProviders(): List<AiProvider> =
        providerDao.getAll().map { it.toDomain() }

    override suspend fun getProvider(providerId: Long): AiProvider? =
        providerDao.getById(providerId)?.toDomain()

    override suspend fun addProvider(
        label: String,
        protocol: AiProtocol,
        baseUrl: String,
        apiKey: String
    ): Long {
        val rowId = providerDao.insert(
            com.synth.synthmusic.data.local.database.AiProviderEntity(
                label = label,
                protocol = protocol.name,
                baseUrl = baseUrl,
                apiKeyEncrypted = cipher.encrypt(apiKey.trim()),
                createdAt = System.currentTimeMillis()
            )
        )
        return rowId
    }

    override suspend fun updateProvider(provider: AiProvider, newApiKey: String?) {
        val encrypted = when {
            newApiKey != null && newApiKey.isNotBlank() -> cipher.encrypt(newApiKey.trim())
            else -> provider.apiKeyEncrypted
        }
        providerDao.update(provider.copy(apiKeyEncrypted = encrypted).toEntity())
    }

    override suspend fun deleteProvider(providerId: Long) {
        modelDao.deleteForProvider(providerId)
        providerDao.getById(providerId)?.let { providerDao.delete(it) }
    }

    override suspend fun getApiKey(providerId: Long): String? =
        providerDao.getById(providerId)?.apiKeyEncrypted
            ?.let { runCatching { cipher.decrypt(it) }.getOrNull() }

    override suspend fun getMaskedKey(providerId: Long): String? =
        providerDao.getById(providerId)?.apiKeyEncrypted
            ?.let { runCatching { cipher.mask(cipher.decrypt(it)) }.getOrNull() }
}

/**
 * Room-backed [AiModelRepository].
 */
class AiModelRepositoryImpl(
    private val modelDao: AiModelDao
) : AiModelRepository {

    override fun observeModels(providerId: Long): Flow<List<AiModel>> =
        modelDao.observeForProvider(providerId).map { list -> list.map { it.toDomain() } }

    override suspend fun getModels(providerId: Long): List<AiModel> =
        modelDao.getForProvider(providerId).map { it.toDomain() }

    override suspend fun getModel(providerId: Long, modelId: String): AiModel? =
        modelDao.get(providerId, modelId)?.toDomain()

    override suspend fun getPinnedModels(): List<AiModel> =
        modelDao.getPinned().map { it.toDomain() }

    override suspend fun upsertModels(models: List<AiModel>) {
        val entities = models.map { incoming ->
            val existing = modelDao.get(incoming.providerId, incoming.modelId)
            if (existing == null) {
                incoming.toEntity()
            } else {
                mergeModel(incoming, existing.toDomain()).toEntity()
            }
        }
        modelDao.upsertAll(entities)
    }

    /**
     * Merges an incoming model row with the stored one. Limits and the pinned
     * flag are user-visible state that a plain REPLACE would clobber:
     * - manually entered limits always win (a re-fetch must not overwrite
     *   them with provider-reported values);
     * - otherwise provider-reported limits replace stale ones, but a fetch
     *   that parses no limits keeps the values already stored;
     * - the pinned flag is preserved from the stored row.
     */
    private fun mergeModel(incoming: AiModel, existing: AiModel): AiModel {
        val limits = when {
            incoming.limitsSource == ModelLimitsSource.MANUAL -> incoming
            incoming.limitsSource == ModelLimitsSource.PROVIDER -> incoming
            else -> incoming.copy(
                contextTokens = existing.contextTokens,
                maxOutputTokens = existing.maxOutputTokens,
                limitsSource = existing.limitsSource
            )
        }
        return limits.copy(isPinned = existing.isPinned)
    }

    override suspend fun setPinned(providerId: Long, modelId: String, pinned: Boolean) {
        modelDao.setPinned(providerId, modelId, pinned)
    }

    override suspend fun deleteModelsForProvider(providerId: Long) {
        modelDao.deleteForProvider(providerId)
    }
}
