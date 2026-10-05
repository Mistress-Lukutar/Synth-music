package com.synth.synthmusic.ui.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synth.synthmusic.domain.model.AiAssistant
import com.synth.synthmusic.domain.model.AiChat
import com.synth.synthmusic.domain.repository.AiAssistantRepository
import com.synth.synthmusic.domain.repository.AiChatRepository
import com.synth.synthmusic.domain.repository.AiProviderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * UI row model for the chat list on the AI home screen.
 */
data class ChatListItem(
    val id: Long,
    val title: String,
    val assistantName: String?,
    val updatedAt: Long
)

/**
 * UI state of the AI home screen (chat home).
 */
data class AiHomeUiState(
    val isLoading: Boolean = true,
    val hasProvider: Boolean = false,
    val query: String = "",
    val chats: List<ChatListItem> = emptyList(),
    val assistants: List<AiAssistant> = emptyList()
)

/**
 * ViewModel backing the AI chat home: chat list observation, search filter
 * and chat creation with an assistant persona.
 */
class AiHomeViewModel(
    private val chatRepository: AiChatRepository,
    private val assistantRepository: AiAssistantRepository,
    private val providerRepository: AiProviderRepository
) : ViewModel() {

    private val query = MutableStateFlow("")

    /** Emits the id of a freshly created chat; consumed after navigation. */
    val createdChatId = MutableStateFlow<Long?>(null)

    /**
     * Aggregated UI state combining chats, providers and the search query.
     */
    val uiState: StateFlow<AiHomeUiState> = combine(
        chatRepository.observeChats(),
        providerRepository.observeProviders(),
        assistantRepository.observeAssistants(),
        query
    ) { chats, providers, assistants, currentQuery ->
        AiHomeUiState(
            isLoading = false,
            hasProvider = providers.isNotEmpty(),
            query = currentQuery,
            chats = chats
                .filter { it.title.contains(currentQuery, ignoreCase = true) }
                .map { it.toListItem() },
            assistants = assistants
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiHomeUiState())

    /**
     * Updates the chat title search filter.
     */
    fun onQueryChange(newQuery: String) {
        query.value = newQuery
    }

    /**
     * Creates a new chat with [assistant]'s persona and exposes its id via
     * [createdChatId]. Passes null for a neutral assistant without grants.
     */
    fun createChat(assistant: AiAssistant?) {
        viewModelScope.launch {
            createdChatId.value = chatRepository.createChat(
                title = "New chat",
                assistantId = assistant?.id,
                providerId = null,
                modelId = null
            )
        }
    }

    /** Consumes [createdChatId] after navigation. */
    fun consumeCreatedChat() {
        createdChatId.value = null
    }

    private suspend fun AiChat.toListItem() = ChatListItem(
        id = id,
        title = title,
        assistantName = assistantId?.let { assistantRepository.getAssistant(it)?.name },
        updatedAt = updatedAt
    )
}
