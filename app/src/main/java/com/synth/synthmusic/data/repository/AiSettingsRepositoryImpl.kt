package com.synth.synthmusic.data.repository

import com.synth.synthmusic.data.local.datastore.AiSettingsDataStore
import com.synth.synthmusic.domain.model.AiCapability
import com.synth.synthmusic.domain.model.AiSettings
import com.synth.synthmusic.domain.model.WebSearchProvider
import com.synth.synthmusic.domain.repository.AiSettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * DataStore-backed [AiSettingsRepository]. Key encryption/decryption is
 * delegated to [AiSettingsDataStore]; this class adds no crypto of its own.
 */
class AiSettingsRepositoryImpl(
    private val dataStore: AiSettingsDataStore
) : AiSettingsRepository {

    override val settings: Flow<AiSettings> = dataStore.settings

    override suspend fun current(): AiSettings = dataStore.settings.first()

    override suspend fun setActiveModel(providerId: Long, modelId: String) {
        dataStore.setActiveModel(providerId, modelId)
    }

    override suspend fun clearActiveModel() {
        dataStore.clearActiveModel()
    }

    override suspend fun setCapabilityEnabled(capability: AiCapability, enabled: Boolean) {
        dataStore.setCapabilityEnabled(capability, enabled)
    }

    override suspend fun setConfirmEdits(enabled: Boolean) {
        dataStore.setConfirmEdits(enabled)
    }

    override suspend fun setWebSearchProvider(provider: WebSearchProvider) {
        dataStore.setWebSearchProvider(provider)
    }

    override suspend fun setWebSearchKey(plaintext: String?) {
        dataStore.setWebSearchKey(plaintext)
    }

    override suspend fun getWebSearchKey(): String? = dataStore.getWebSearchKey()
}
