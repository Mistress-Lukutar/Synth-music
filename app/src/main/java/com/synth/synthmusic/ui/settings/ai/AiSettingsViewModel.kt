package com.synth.synthmusic.ui.settings.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synth.synthmusic.data.ai.client.AiClientFactory
import com.synth.synthmusic.domain.model.AiCapability
import com.synth.synthmusic.domain.model.AiEndpoint
import com.synth.synthmusic.domain.model.AiModel
import com.synth.synthmusic.domain.model.AiProtocol
import com.synth.synthmusic.domain.model.ActiveModelRef
import com.synth.synthmusic.domain.model.AiSettings
import com.synth.synthmusic.domain.model.guessModelCapabilities
import com.synth.synthmusic.domain.model.WebSearchProvider
import com.synth.synthmusic.data.ai.tools.TrashManager
import com.synth.synthmusic.data.ai.tools.TrashEntry
import com.synth.synthmusic.domain.model.AiActionLogEntry
import com.synth.synthmusic.domain.repository.AiActionLogRepository
import com.synth.synthmusic.domain.repository.AiModelRepository
import com.synth.synthmusic.domain.repository.AiProviderRepository
import com.synth.synthmusic.domain.repository.AiSettingsRepository
import com.synth.synthmusic.domain.usecase.ai.FetchModelsUseCase
import com.synth.synthmusic.domain.usecase.ai.TestConnectionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient

/**
 * UI row model for a configured provider.
 */
data class ProviderUi(
    val id: Long,
    val label: String,
    val protocol: AiProtocol,
    val baseUrl: String,
    val maskedKey: String?,
    val modelCount: Int
)

/**
 * UI row model for a model selectable as the default.
 */
data class ModelUi(
    val providerId: Long,
    val providerLabel: String,
    val model: AiModel
)

/**
 * Complete UI state of the AI settings screen.
 */
data class AiSettingsUiState(
    val isLoading: Boolean = true,
    val providers: List<ProviderUi> = emptyList(),
    val models: List<ModelUi> = emptyList(),
    val activeModel: ActiveModelRef? = null,
    val capabilities: Map<AiCapability, Boolean> = AiSettings.DEFAULT_CAPABILITY_GRANTS,
    val confirmEdits: Boolean = true,
    val webSearchProvider: WebSearchProvider = WebSearchProvider.NONE,
    val webSearchKeyConfigured: Boolean = false,
    val busyProviderId: Long? = null,
    val trashCount: Int = 0,
    val trashPath: String = "",
    val trashEntries: List<TrashEntry> = emptyList(),
    val actionLog: List<AiActionLogEntry> = emptyList()
)

/**
 * One-shot UI feedback events emitted by [AiSettingsViewModel].
 */
sealed class AiSettingsEvent {
    /** Transient snackbar message. */
    data class Message(val text: String) : AiSettingsEvent()
}

/**
 * Events the AI settings screen can send to its ViewModel.
 */
sealed class AiSettingsUiEvent {
    /**
     * Creates (providerId == null) or updates a provider. A blank [apiKey]
     * keeps the stored key when updating.
     */
    data class SaveProvider(
        val providerId: Long?,
        val label: String,
        val protocol: AiProtocol,
        val baseUrl: String,
        val apiKey: String
    ) : AiSettingsUiEvent()

    /** Deletes a provider and its cached models. */
    data class DeleteProvider(val providerId: Long) : AiSettingsUiEvent()

    /** Fetches the provider's model list from its endpoint. */
    data class FetchModels(val providerId: Long) : AiSettingsUiEvent()

    /** Sends a 1-token ping to verify credentials. */
    data class TestConnection(val providerId: Long) : AiSettingsUiEvent()

    /** Sets the global default model. */
    data class SetDefaultModel(val providerId: Long, val modelId: String) : AiSettingsUiEvent()

    /** Toggles a global capability kill-switch. */
    data class ToggleCapability(val capability: AiCapability, val enabled: Boolean) :
        AiSettingsUiEvent()

    /** Toggles whether edit tools ask for confirmation before executing. */
    data class ToggleConfirmEdits(val enabled: Boolean) : AiSettingsUiEvent()

    /** Selects the web-search backend. */
    data class SetWebSearchProvider(val provider: WebSearchProvider) : AiSettingsUiEvent()

    /** Stores (or clears when blank) the web-search API key. */
    data class SetWebSearchKey(val key: String) : AiSettingsUiEvent()

    /** Empties the AI trash permanently. */
    data object EmptyTrash : AiSettingsUiEvent()

    /** Restores one trash entry. */
    data class RestoreTrashEntry(val entry: TrashEntry) : AiSettingsUiEvent()

    /**
     * Adds a model to a provider manually, without fetching the model list.
     */
    data class AddManualModel(
        val providerId: Long,
        val modelId: String,
        val displayName: String
    ) : AiSettingsUiEvent()
}

/**
 * ViewModel backing the AI settings screen: provider CRUD, model fetching,
 * default model selection, capability kill-switches and web search config.
 */
class AiSettingsViewModel(
    private val providerRepository: AiProviderRepository,
    private val modelRepository: AiModelRepository,
    private val settingsRepository: AiSettingsRepository,
    private val clientFactory: AiClientFactory,
    private val fetchModelsUseCase: FetchModelsUseCase,
    private val testConnectionUseCase: TestConnectionUseCase,
    private val httpClient: OkHttpClient,
    private val json: Json,
    private val trashManager: TrashManager,
    private val actionLogRepository: AiActionLogRepository
) : ViewModel() {

    private val providersState = MutableStateFlow<List<ProviderUi>>(emptyList())
    private val modelsState = MutableStateFlow<List<ModelUi>>(emptyList())
    private val busyProviderId = MutableStateFlow<Long?>(null)
    private val _events = MutableStateFlow<AiSettingsEvent?>(null)
    private val trashEntriesState = MutableStateFlow<List<TrashEntry>>(emptyList())

    /** Trash directory path, for display in the settings UI. */
    val trashPath: String get() = trashManager.trashPath()

    /** One-shot UI events; consumed via [consumeEvent]. */
    val events: StateFlow<AiSettingsEvent?> = _events.asStateFlow()

    /** Provider currently running a fetch/test operation, if any. */
    val busyProvider: StateFlow<Long?> = busyProviderId.asStateFlow()

    /**
     * Aggregated UI state combining provider/model data with settings.
     */
    val uiState: StateFlow<AiSettingsUiState> = combine(
        providersState,
        modelsState,
        settingsRepository.settings,
        busyProviderId,
        combine(actionLogRepository.observeAll(), trashEntriesState) { log, trash ->
            log to trash
        }
    ) { providers, models, settings, busy, (log, trash) ->
        AiSettingsUiState(
            isLoading = false,
            providers = providers,
            models = models,
            activeModel = settings.activeProviderId?.let { providerId ->
                settings.activeModelId?.let { modelId ->
                    ActiveModelRef(providerId, modelId)
                }
            },
            capabilities = settings.capabilityGrants,
            confirmEdits = settings.confirmEdits,
            webSearchProvider = settings.webSearchProvider,
            webSearchKeyConfigured = settings.webSearchKeyEncrypted != null,
            busyProviderId = busy,
            actionLog = log.take(100),
            trashCount = trash.size,
            trashPath = trashPath,
            trashEntries = trash
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiSettingsUiState())

    init {
        refresh()
        refreshTrash()
    }

    /**
     * Reloads providers, masked keys, model counts and selectable models.
     */
    fun refresh() {
        viewModelScope.launch {
            val providers = providerRepository.getProviders()
            providersState.value = providers.map { provider ->
                ProviderUi(
                    id = provider.id,
                    label = provider.label,
                    protocol = provider.protocol,
                    baseUrl = provider.baseUrl,
                    maskedKey = providerRepository.getMaskedKey(provider.id),
                    modelCount = modelRepository.getModels(provider.id).size
                )
            }
            modelsState.value = providers.flatMap { provider ->
                modelRepository.getModels(provider.id).map { model ->
                    ModelUi(provider.id, provider.label, model)
                }
            }
        }
    }

    /**
     * Handles all screen events.
     */
    fun onEvent(event: AiSettingsUiEvent) {
        when (event) {
            is AiSettingsUiEvent.SaveProvider -> saveProvider(event)
            is AiSettingsUiEvent.DeleteProvider -> deleteProvider(event.providerId)
            is AiSettingsUiEvent.FetchModels -> fetchModels(event.providerId)
            is AiSettingsUiEvent.TestConnection -> testConnection(event.providerId)
            is AiSettingsUiEvent.SetDefaultModel -> viewModelScope.launch {
                settingsRepository.setActiveModel(event.providerId, event.modelId)
            }
            is AiSettingsUiEvent.ToggleCapability -> viewModelScope.launch {
                settingsRepository.setCapabilityEnabled(event.capability, event.enabled)
            }
            is AiSettingsUiEvent.ToggleConfirmEdits -> viewModelScope.launch {
                settingsRepository.setConfirmEdits(event.enabled)
            }
            is AiSettingsUiEvent.SetWebSearchProvider -> viewModelScope.launch {
                settingsRepository.setWebSearchProvider(event.provider)
            }
            is AiSettingsUiEvent.SetWebSearchKey -> viewModelScope.launch {
                settingsRepository.setWebSearchKey(event.key.takeIf { it.isNotBlank() })
            }
            AiSettingsUiEvent.EmptyTrash -> viewModelScope.launch {
                val removed = trashManager.empty()
                _events.value = AiSettingsEvent.Message("Removed $removed file(s)")
                refreshTrash()
            }
            is AiSettingsUiEvent.RestoreTrashEntry -> viewModelScope.launch {
                val restored = trashManager.restore(event.entry)
                _events.value = AiSettingsEvent.Message(
                    if (restored) "Restored ${event.entry.fileName}"
                    else "Restore failed"
                )
                refreshTrash()
            }
            is AiSettingsUiEvent.AddManualModel -> viewModelScope.launch {
                val modelId = event.modelId.trim()
                if (modelId.isEmpty()) {
                    _events.value = AiSettingsEvent.Message("Model ID is required")
                    return@launch
                }
                val (tools, vision) = guessModelCapabilities(modelId)
                modelRepository.upsertModels(
                    listOf(
                        AiModel(
                            providerId = event.providerId,
                            modelId = modelId,
                            displayName = event.displayName.trim()
                                .ifEmpty { modelId },
                            supportsTools = tools,
                            supportsVision = vision,
                            isPinned = false
                        )
                    )
                )
                refresh()
                _events.value = AiSettingsEvent.Message("Model $modelId added")
            }
        }
    }

    /**
     * Reloads trash entries; called on init and after mutations.
     */
    fun refreshTrash() {
        viewModelScope.launch {
            trashEntriesState.value = trashManager.list()
        }
    }


    /** Consumes the pending one-shot event. */
    fun consumeEvent() {
        _events.value = null
    }

    private fun saveProvider(event: AiSettingsUiEvent.SaveProvider) {
        viewModelScope.launch {
            try {
                if (event.providerId == null) {
                    providerRepository.addProvider(
                        event.label, event.protocol, event.baseUrl, event.apiKey
                    )
                } else {
                    val existing = providerRepository.getProvider(event.providerId)
                        ?: return@launch
                    providerRepository.updateProvider(
                        existing.copy(
                            label = event.label,
                            protocol = event.protocol,
                            baseUrl = event.baseUrl
                        ),
                        newApiKey = event.apiKey.takeIf { it.isNotBlank() }
                    )
                }
                refresh()
            } catch (e: Exception) {
                _events.value = AiSettingsEvent.Message(e.message ?: "Failed to save provider")
            }
        }
    }

    private fun deleteProvider(providerId: Long) {
        viewModelScope.launch {
            providerRepository.deleteProvider(providerId)
            val settings = settingsRepository.current()
            if (settings.activeProviderId == providerId) {
                settingsRepository.clearActiveModel()
            }
            refresh()
        }
    }

    private fun fetchModels(providerId: Long) {
        viewModelScope.launch {
            try {
                val provider = providerRepository.getProvider(providerId)
                    ?: run {
                        _events.value = AiSettingsEvent.Message("Provider not found")
                        return@launch
                    }
                val apiKey = providerRepository.getApiKey(providerId)
                    ?: run {
                        _events.value = AiSettingsEvent.Message("API key missing — re-save the key")
                        return@launch
                    }
                busyProviderId.value = providerId
                fetchModelsUseCase(provider, apiKey, httpClient, json, modelRepository)
                refresh()
            } catch (e: Exception) {
                _events.value = AiSettingsEvent.Message(e.message ?: "Fetch failed")
            } finally {
                busyProviderId.value = null
            }
        }
    }

    private fun testConnection(providerId: Long) {
        viewModelScope.launch {
            try {
                val provider = providerRepository.getProvider(providerId)
                    ?: run {
                        _events.value = AiSettingsEvent.Message("Provider not found")
                        return@launch
                    }
                val apiKey = providerRepository.getApiKey(providerId)
                    ?: run {
                        _events.value = AiSettingsEvent.Message("API key missing — re-save the key")
                        return@launch
                    }
                busyProviderId.value = providerId
                val client = clientFactory.clientFor(provider.protocol)
                val probeModelId = modelRepository.getModels(providerId)
                    .firstOrNull()?.modelId ?: "gpt-4o-mini"
                val error = testConnectionUseCase(
                    AiEndpoint(provider.baseUrl, apiKey, probeModelId, provider.protocol),
                    provider.protocol,
                    client
                )
                _events.value = AiSettingsEvent.Message(error ?: "Connection successful")
            } catch (e: Exception) {
                _events.value = AiSettingsEvent.Message(e.message ?: "Connection failed")
            } finally {
                busyProviderId.value = null
            }
        }
    }
}
