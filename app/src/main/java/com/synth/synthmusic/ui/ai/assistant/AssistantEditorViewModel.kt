package com.synth.synthmusic.ui.ai.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synth.synthmusic.domain.model.AiAssistant
import com.synth.synthmusic.domain.model.AiCapability
import com.synth.synthmusic.domain.model.AiAvatarIcon
import com.synth.synthmusic.domain.repository.AiAssistantRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Editable state of the assistant editor screen.
 */
data class AssistantEditorUiState(
    val assistantId: Long? = null,
    val isBuiltin: Boolean = false,
    val name: String = "",
    val description: String = "",
    val systemPrompt: String = "",
    val avatarIcon: AiAvatarIcon = AiAvatarIcon.SMART_TOY,
    val avatarColorIndex: Int = 0,
    val grants: Set<AiCapability> = emptySet(),
    val isLoaded: Boolean = false
)

/**
 * ViewModel for creating and editing assistant personas. Built-ins can be
 * duplicated ("Save as copy") but not edited in place.
 */
class AssistantEditorViewModel(
    private val assistantId: Long?,
    private val assistantRepository: AiAssistantRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantEditorUiState(assistantId = assistantId))

    /** Editor state. */
    val uiState: StateFlow<AssistantEditorUiState> = _uiState.asStateFlow()

    /** Emits after a successful save/delete so the screen can navigate back. */
    val finished = MutableStateFlow<Boolean>(false)

    init {
        if (assistantId != null) {
            viewModelScope.launch {
                assistantRepository.getAssistant(assistantId)?.let { assistant ->
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
                            grants = assistant.defaultGrants,
                            isLoaded = true
                        )
                    }
                }
            }
        } else {
            _uiState.update { it.copy(isLoaded = true) }
        }
    }

    /**
     * Applies a field edit to the editor state.
     */
    fun update(transform: (AssistantEditorUiState) -> AssistantEditorUiState) {
        _uiState.update(transform)
    }

    /**
     * Toggles a default capability grant.
     */
    fun toggleGrant(capability: AiCapability, enabled: Boolean) {
        _uiState.update { state ->
            state.copy(
                grants = if (enabled) state.grants + capability else state.grants - capability
            )
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
            assistantRepository.upsertAssistant(
                AiAssistant(
                    id = if (asCopy) 0 else state.assistantId ?: 0,
                    builtinKey = null,
                    name = state.name.ifBlank { "Assistant" },
                    description = state.description,
                    systemPrompt = state.systemPrompt,
                    avatarIcon = state.avatarIcon.name,
                    avatarColorIndex = state.avatarColorIndex,
                    defaultGrants = state.grants,
                    isBuiltin = false,
                    sortOrder = 100
                )
            )
            finished.value = true
        }
    }

    /**
     * Deletes a non-builtin assistant.
     */
    fun delete() {
        val state = _uiState.value
        if (state.isBuiltin || state.assistantId == null) return
        viewModelScope.launch {
            assistantRepository.deleteAssistant(state.assistantId)
            finished.value = true
        }
    }

    /** Consumes the finished flag. */
    fun consumeFinished() {
        finished.value = false
    }
}
