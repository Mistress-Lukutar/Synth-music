package com.synth.synthmusic.data.local.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.synth.synthmusic.data.ai.crypto.ApiKeyCipher
import com.synth.synthmusic.domain.model.AiCapability
import com.synth.synthmusic.domain.model.AiSettings
import com.synth.synthmusic.domain.model.WebSearchProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.aiSettingsStore: DataStore<Preferences> by preferencesDataStore(
    name = "ai_settings"
)

/**
 * DataStore-backed storage for global AI settings. API keys are stored
 * encrypted via [ApiKeyCipher]; plaintext never reaches storage or logs.
 */
class AiSettingsDataStore(
    private val context: Context,
    private val cipher: ApiKeyCipher
) {
    private val dataStore = context.aiSettingsStore

    private companion object {
        val ACTIVE_PROVIDER_ID = longPreferencesKey("active_provider_id")
        val ACTIVE_MODEL_ID = stringPreferencesKey("active_model_id")
        val LAST_ASSISTANT_ID = longPreferencesKey("last_assistant_id")
        val CONFIRM_EDITS = booleanPreferencesKey("confirm_edits")
        val WEB_SEARCH_PROVIDER = stringPreferencesKey("web_search_provider")
        val WEB_SEARCH_KEY_ENCRYPTED = stringPreferencesKey("web_search_key_encrypted")

        fun capabilityKey(capability: AiCapability) =
            booleanPreferencesKey("capability_${capability.name.lowercase()}")
    }

    /** Observes the current AI settings. */
    val settings: Flow<AiSettings> = dataStore.data.map { prefs ->
        val grants = AiCapability.entries.associateWith { capability ->
            prefs[capabilityKey(capability)]
                ?: AiSettings.DEFAULT_CAPABILITY_GRANTS.getValue(capability)
        }
        AiSettings(
            activeProviderId = prefs[ACTIVE_PROVIDER_ID],
            activeModelId = prefs[ACTIVE_MODEL_ID],
            capabilityGrants = grants,
            confirmEdits = prefs[CONFIRM_EDITS] ?: true,
            webSearchProvider = prefs[WEB_SEARCH_PROVIDER]
                ?.let { runCatching { WebSearchProvider.valueOf(it) }.getOrNull() }
                ?: WebSearchProvider.NONE,
            webSearchKeyEncrypted = prefs[WEB_SEARCH_KEY_ENCRYPTED]
        )
    }

    /** Stores the globally active model reference. */
    suspend fun setActiveModel(providerId: Long, modelId: String) {
        dataStore.edit {
            it[ACTIVE_PROVIDER_ID] = providerId
            it[ACTIVE_MODEL_ID] = modelId
        }
    }

    /** Clears the globally active model reference. */
    suspend fun clearActiveModel() {
        dataStore.edit {
            it.remove(ACTIVE_PROVIDER_ID)
            it.remove(ACTIVE_MODEL_ID)
        }
    }

    /** Observes the assistant used for the last new chat (null = General). */
    val lastAssistantId: Flow<Long?> = dataStore.data.map { it[LAST_ASSISTANT_ID] }

    /** Stores the assistant pre-selected for new chats. Null resets to General. */
    suspend fun setLastAssistantId(assistantId: Long?) {
        dataStore.edit {
            if (assistantId == null) {
                it.remove(LAST_ASSISTANT_ID)
            } else {
                it[LAST_ASSISTANT_ID] = assistantId
            }
        }
    }

    /** Toggles a global capability kill-switch. */
    suspend fun setCapabilityEnabled(capability: AiCapability, enabled: Boolean) {
        dataStore.edit { it[capabilityKey(capability)] = enabled }
    }

    /** Toggles whether edit tools ask for confirmation before executing. */
    suspend fun setConfirmEdits(enabled: Boolean) {
        dataStore.edit { it[CONFIRM_EDITS] = enabled }
    }

    /** Stores the optional web-search backend selection. */
    suspend fun setWebSearchProvider(provider: WebSearchProvider) {
        dataStore.edit { it[WEB_SEARCH_PROVIDER] = provider.name }
    }

    /**
     * Stores the web-search API key encrypted. Null clears the stored key.
     */
    suspend fun setWebSearchKey(plaintext: String?) {
        dataStore.edit { prefs ->
            val encrypted = plaintext?.takeIf { it.isNotBlank() }
                ?.let { cipher.encrypt(it) }
            if (encrypted == null) {
                prefs.remove(WEB_SEARCH_KEY_ENCRYPTED)
            } else {
                prefs[WEB_SEARCH_KEY_ENCRYPTED] = encrypted
            }
        }
    }

    /** Decrypts and returns the web-search API key, or null. */
    suspend fun getWebSearchKey(): String? =
        dataStore.data.first()[WEB_SEARCH_KEY_ENCRYPTED]?.let {
            runCatching { cipher.decrypt(it) }.getOrNull()
        }
}
