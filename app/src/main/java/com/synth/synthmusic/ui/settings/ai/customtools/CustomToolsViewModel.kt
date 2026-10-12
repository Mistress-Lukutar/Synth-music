package com.synth.synthmusic.ui.settings.ai.customtools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synth.synthmusic.data.ai.customtools.CustomToolRecord
import com.synth.synthmusic.data.ai.customtools.CustomToolRepository
import com.synth.synthmusic.domain.model.AiCapability
import com.synth.synthmusic.domain.usecase.ai.ToolContext
import com.synth.synthmusic.domain.usecase.ai.ToolOutcome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

/** State of the custom-tools management screen. */
data class CustomToolsUiState(
    val records: List<CustomToolRecord> = emptyList(),
    val isLoading: Boolean = true,
    /** Names of secrets with a stored value, keyed by tool name. */
    val storedSecrets: Map<String, Set<String>> = emptyMap(),
    /** Output of the last test run shown in the details dialog. */
    val testOutput: String? = null,
    val isTestRunning: Boolean = false
)

/** One-shot messages surfaced as snackbars. */
sealed class CustomToolsEvent {
    data class Message(val text: String) : CustomToolsEvent()
}

/**
 * ViewModel behind the custom-tools settings screen: lists imported tools,
 * handles SAF imports, enable/disable, deletion, secret values and explicit
 * test runs.
 */
class CustomToolsViewModel(
    private val repository: CustomToolRepository
) : ViewModel() {

    private val json = Json { ignoreUnknownKeys = true }

    private val _uiState = MutableStateFlow(CustomToolsUiState())

    /** Screen state. */
    val uiState: StateFlow<CustomToolsUiState> = _uiState.asStateFlow()

    private val _events = MutableStateFlow<CustomToolsEvent?>(null)

    /** One-shot snackbar events; consume via [consumeEvent]. */
    val events: StateFlow<CustomToolsEvent?> = _events.asStateFlow()

    init {
        refresh()
    }

    /** Reloads the tool list from the repository. */
    fun refresh() {
        viewModelScope.launch {
            val records = repository.records()
            val stored = records.associate { record ->
                record.name to repository.storedSecretNames(record.name)
            }
            _uiState.update {
                it.copy(records = records, storedSecrets = stored, isLoading = false)
            }
        }
    }

    /**
     * Imports a tool package read via SAF. Failures (bad zip, invalid
     * manifest, name collisions) are reported as messages.
     */
    fun import(bytes: ByteArray, sourceName: String) {
        viewModelScope.launch {
            repository.import(bytes, sourceName).fold(
                onSuccess = { record ->
                    _events.value = CustomToolsEvent.Message(
                        "Tool \"${record.displayName}\" imported"
                    )
                    refresh()
                },
                onFailure = { error ->
                    _events.value = CustomToolsEvent.Message(
                        "Import failed: ${error.message ?: "unknown error"}"
                    )
                }
            )
        }
    }

    /** Toggles whether the tool participates in chats. */
    fun setEnabled(name: String, enabled: Boolean) {
        viewModelScope.launch {
            repository.setEnabled(name, enabled)
            refresh()
        }
    }

    /** Removes a tool together with its secrets. */
    fun delete(name: String) {
        viewModelScope.launch {
            repository.delete(name)
            _events.value = CustomToolsEvent.Message("Tool deleted")
            refresh()
        }
    }

    /** Persists one encrypted secret value; blank clears it. */
    fun setSecret(toolName: String, secretName: String, value: String) {
        viewModelScope.launch {
            repository.setSecret(toolName, secretName, value)
            _uiState.update { state ->
                state.copy(
                    storedSecrets = state.storedSecrets + (toolName to repository.storedSecretNames(toolName))
                )
            }
        }
    }

    /**
     * Explicitly executes the tool with user-provided arguments. The test
     * run receives every capability — this is the user acting from settings,
     * not the model — and bypasses confirmation cards.
     */
    fun runTest(name: String, argsText: String) {
        val args: JsonObject = try {
            json.parseToJsonElement(argsText.ifBlank { "{}" }) as? JsonObject
                ?: run {
                    _events.value = CustomToolsEvent.Message("Arguments must be a JSON object")
                    return
                }
        } catch (e: Exception) {
            _events.value = CustomToolsEvent.Message("Arguments are not valid JSON")
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isTestRunning = true, testOutput = null) }
            val tool = repository.buildTool(name)
            val output: String = if (tool == null) {
                "Tool not available"
            } else {
                val outcome: ToolOutcome = tool.execute(
                    args,
                    ToolContext(
                        chatId = 0L,
                        grants = AiCapability.entries.toSet(),
                        allowedTools = null,
                        approvalBridge = null
                    )
                )
                (if (outcome.isError) "ERROR: " else "OK: ") + outcome.content
            }
            _uiState.update { it.copy(isTestRunning = false, testOutput = output.take(4000)) }
        }
    }

    /** Clears the test output (dialog closed). */
    fun clearTestOutput() {
        _uiState.update { it.copy(testOutput = null) }
    }

    /** Consumes the pending snackbar event. */
    fun consumeEvent() {
        _events.value = null
    }
}
