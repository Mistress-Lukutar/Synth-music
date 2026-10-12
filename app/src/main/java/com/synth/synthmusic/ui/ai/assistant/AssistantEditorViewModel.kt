package com.synth.synthmusic.ui.ai.assistant

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synth.synthmusic.data.ai.AssistantAvatarStore
import com.synth.synthmusic.domain.model.AiAssistant
import com.synth.synthmusic.domain.model.AiCapability
import com.synth.synthmusic.domain.model.AiAvatarIcon
import com.synth.synthmusic.domain.repository.AiAssistantRepository
import com.synth.synthmusic.domain.usecase.ai.AiToolDescriptor
import com.synth.synthmusic.domain.usecase.ai.ToolDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Editable state of the assistant editor screen.
 *
 * @property selectedTools explicit per-tool allow-list saved as the
 * assistant's [AiAssistant.defaultTools].
 * @property avatarImage file name of a custom avatar image; `null` renders
 * the preset [avatarIcon].
 */
data class AssistantEditorUiState(
    val assistantId: Long? = null,
    val isBuiltin: Boolean = false,
    val name: String = "",
    val description: String = "",
    val systemPrompt: String = "",
    val avatarIcon: AiAvatarIcon = AiAvatarIcon.SMART_TOY,
    val avatarColorIndex: Int = 0,
    val selectedTools: Set<String> = emptySet(),
    val avatarImage: String? = null,
    val isLoaded: Boolean = false
)

/**
 * ViewModel for creating and editing assistant personas. Built-ins can be
 * duplicated ("Save as copy") but not edited in place. Tools are granted
 * individually; on save the capability grants are derived as the union of
 * the selected tools' requirements so the global kill-switches keep working.
 */
class AssistantEditorViewModel(
    private val assistantId: Long?,
    private val assistantRepository: AiAssistantRepository,
    private val toolDispatcher: ToolDispatcher,
    private val avatarStore: AssistantAvatarStore
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantEditorUiState(assistantId = assistantId))

    /** Editor state. */
    val uiState: StateFlow<AssistantEditorUiState> = _uiState.asStateFlow()

    /** Emits after a successful save/delete so the screen can navigate back. */
    val finished = MutableStateFlow<Boolean>(false)

    /**
     * Catalog of registered tools (built-in + user-imported custom) rendered
     * as per-tool grant switches. Loaded asynchronously because custom tools
     * are read from disk.
     */
    private val _toolCatalog = MutableStateFlow<List<AiToolDescriptor>>(emptyList())

    /** Editor tool catalog. */
    val toolCatalog: StateFlow<List<AiToolDescriptor>> = _toolCatalog.asStateFlow()

    /** Avatar file name loaded from the persisted row (replacement baseline). */
    private var originalAvatarImage: String? = null

    /** Last avatar image imported this session but not persisted yet. */
    private var pendingAvatarImage: String? = null

    /** Whether the current state was persisted (suppresses avatar cleanup). */
    private var persisted = assistantId == null

    init {
        viewModelScope.launch {
            // Load the catalog first: legacy rows derive their default tool
            // selection from it.
            _toolCatalog.value = toolDispatcher.allTools().sortedBy { it.name }
            if (assistantId != null) {
                assistantRepository.getAssistant(assistantId)?.let { assistant ->
                    val toolCatalog = _toolCatalog.value
                    originalAvatarImage = assistant.avatarImage
                    // Legacy rows (and built-ins) have no explicit tool list:
                    // pre-select every tool their capability grants cover.
                    val selected = assistant.defaultTools
                        ?: toolCatalog
                            .filter { tool ->
                                tool.requiredGrants.all { it in assistant.defaultGrants }
                            }
                            .map { it.name }
                            .toSet()
                    _uiState.update {
                        AssistantEditorUiState(
                            assistantId = assistant.id,
                            isBuiltin = assistant.isBuiltin,
                            name = assistant.name,
                            description = assistant.description,
                            systemPrompt = assistant.systemPrompt,
                            avatarIcon = runCatching {
                                AiAvatarIcon.valueOf(assistant.avatarIcon)
                            }.getOrDefault(AiAvatarIcon.SMART_TOY),
                            avatarColorIndex = assistant.avatarColorIndex,
                            selectedTools = selected,
                            avatarImage = assistant.avatarImage,
                            isLoaded = true
                        )
                    }
                }
            } else {
                _uiState.update { it.copy(isLoaded = true) }
            }
        }
    }

    /**
     * Applies a field edit to the editor state.
     */
    fun update(transform: (AssistantEditorUiState) -> AssistantEditorUiState) {
        _uiState.update(transform)
    }

    /**
     * Toggles one tool in the per-tool allow-list.
     */
    fun toggleTool(toolName: String, enabled: Boolean) {
        _uiState.update { state ->
            state.copy(
                selectedTools = if (enabled) {
                    state.selectedTools + toolName
                } else {
                    state.selectedTools - toolName
                }
            )
        }
    }

    /**
     * Selects or deselects every tool of one capability group at once.
     */
    fun toggleGroup(capability: AiCapability, enabled: Boolean) {
        val groupTools = _toolCatalog.value
            .filter { capability in it.requiredGrants }
            .map { it.name }
            .toSet()
        _uiState.update { state ->
            state.copy(
                selectedTools = if (enabled) {
                    state.selectedTools + groupTools
                } else {
                    state.selectedTools - groupTools
                }
            )
        }
    }

    /**
     * Selects a preset icon and drops a pending custom image, if any.
     */
    fun selectPresetIcon(icon: AiAvatarIcon) {
        val dropped = pendingAvatarImage
        if (dropped != null && _uiState.value.avatarImage == dropped) {
            avatarStore.delete(dropped)
            pendingAvatarImage = null
        }
        _uiState.update {
            it.copy(avatarImage = null, avatarIcon = icon, avatarColorIndex = icon.ordinal % 6)
        }
    }

    /**
     * Imports a picked image as the custom avatar. The previous pending
     * import (never persisted) is deleted; the currently persisted image is
     * only removed on save.
     */
    fun onImagePicked(uri: Uri) {
        viewModelScope.launch {
            val imported = avatarStore.import(uri) ?: return@launch
            val previousPending = pendingAvatarImage
            if (previousPending != null && _uiState.value.avatarImage == previousPending) {
                avatarStore.delete(previousPending)
            }
            pendingAvatarImage = imported
            _uiState.update { it.copy(avatarImage = imported) }
        }
    }

    /**
     * Saves the persona. When editing a built-in, a duplicate is created
     * instead (`isBuiltin = false`).
     */
    fun save() {
        val state = _uiState.value
        viewModelScope.launch {
            val asCopy = state.isBuiltin
            val grants = _toolCatalog.value
                .filter { it.name in state.selectedTools }
                .flatMap { it.requiredGrants }
                .toSet()
            assistantRepository.upsertAssistant(
                AiAssistant(
                    id = if (asCopy) 0 else state.assistantId ?: 0,
                    builtinKey = null,
                    name = state.name.ifBlank { "Assistant" },
                    description = state.description,
                    systemPrompt = state.systemPrompt,
                    avatarIcon = state.avatarIcon.name,
                    avatarColorIndex = state.avatarColorIndex,
                    defaultGrants = grants,
                    isBuiltin = false,
                    sortOrder = 100,
                    defaultTools = state.selectedTools,
                    avatarImage = state.avatarImage
                )
            )
            if (!asCopy && state.avatarImage != originalAvatarImage) {
                avatarStore.delete(originalAvatarImage)
            }
            persisted = true
            pendingAvatarImage = null
            finished.value = true
        }
    }

    /**
     * Deletes a non-builtin assistant together with its custom avatar image.
     */
    fun delete() {
        val state = _uiState.value
        if (state.isBuiltin || state.assistantId == null) return
        viewModelScope.launch {
            assistantRepository.deleteAssistant(state.assistantId)
            avatarStore.delete(originalAvatarImage)
            if (pendingAvatarImage != null && pendingAvatarImage != originalAvatarImage) {
                avatarStore.delete(pendingAvatarImage)
            }
            persisted = true
            pendingAvatarImage = null
            finished.value = true
        }
    }

    /** Consumes the finished flag. */
    fun consumeFinished() {
        finished.value = false
    }

    override fun onCleared() {
        // An imported but never persisted avatar would leak on disk.
        if (!persisted) {
            val state = _uiState.value
            if (state.avatarImage != null && state.avatarImage == pendingAvatarImage) {
                avatarStore.delete(pendingAvatarImage)
            }
        }
        super.onCleared()
    }
}
