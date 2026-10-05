package com.synth.synthmusic.ui.ai

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synth.synthmusic.data.ai.ImageAttachmentLoader
import com.synth.synthmusic.data.local.datastore.AiSettingsDataStore
import com.synth.synthmusic.domain.model.AiActionLogEntry
import com.synth.synthmusic.domain.model.AiAssistant
import com.synth.synthmusic.domain.model.AiChat
import com.synth.synthmusic.domain.model.AiChatMessage
import com.synth.synthmusic.domain.model.AiMessagePart
import com.synth.synthmusic.domain.model.AiMessageStatus
import com.synth.synthmusic.domain.model.AiModel
import com.synth.synthmusic.domain.model.AiRole
import com.synth.synthmusic.domain.repository.AiActionLogRepository
import com.synth.synthmusic.domain.repository.AiAssistantRepository
import com.synth.synthmusic.domain.repository.AiChatRepository
import com.synth.synthmusic.domain.repository.AiModelRepository
import com.synth.synthmusic.domain.repository.AiProviderRepository
import com.synth.synthmusic.domain.usecase.ai.ApprovalBridge
import com.synth.synthmusic.domain.usecase.ai.ChatRunEvent
import com.synth.synthmusic.domain.usecase.ai.ConfirmationRequest
import com.synth.synthmusic.domain.usecase.ai.RunChatUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * UI row model for a chat in the drawer history.
 */
data class ChatListItem(
    val id: Long,
    val title: String,
    val assistantName: String?,
    val assistantIcon: String?,
    val assistantColorIndex: Int?,
    val updatedAt: Long
)

/**
 * Aggregated UI state of the whole AI tab: drawer data (chats, assistants)
 * plus the active conversation (or the new-chat draft when [activeChatId]
 * is null).
 */
data class AiUiState(
    val isLoading: Boolean = true,
    val hasProvider: Boolean = false,
    val chats: List<ChatListItem> = emptyList(),
    val assistants: List<AiAssistant> = emptyList(),
    val activeChatId: Long? = null,
    val chat: AiChat? = null,
    val draftInput: String = "",
    val activeAssistant: AiAssistant? = null,
    val modelLabel: String? = null,
    val supportsVision: Boolean = false,
    val messages: List<AiChatMessage> = emptyList(),
    val isStreaming: Boolean = false,
    val pendingAttachments: List<AiMessagePart.Image> = emptyList(),
    val pendingApproval: ConfirmationRequest? = null,
    val error: String? = null
)

/**
 * Events the AI screen can send to its ViewModel.
 */
sealed class AiUiEvent {
    /** Resets the surface to a fresh new-chat draft. */
    data object NewChat : AiUiEvent()

    /** Opens a chat from the drawer history. */
    data class OpenChat(val chatId: Long) : AiUiEvent()

    /** Selects the assistant for the next chat, starting a new draft. */
    data class SelectAssistant(val assistant: AiAssistant?) : AiUiEvent()

    /** Updates the draft input text. */
    data class InputChanged(val text: String) : AiUiEvent()

    /** Sends a user message with the current pending attachments. */
    data class Send(val text: String) : AiUiEvent()

    /** Attaches an image from a photo-picker [uri]. */
    data class AttachImage(val uri: Uri) : AiUiEvent()

    /** Removes a pending attachment by index. */
    data class RemoveAttachment(val index: Int) : AiUiEvent()

    /** Cancels the running stream. */
    data object Stop : AiUiEvent()

    /** Deletes the last assistant answer and re-runs the turn. */
    data object Regenerate : AiUiEvent()

    /** Renames the given chat. */
    data class RenameChat(val chatId: Long, val title: String) : AiUiEvent()

    /** Deletes the given chat and returns to a draft if it was open. */
    data class DeleteChat(val chatId: Long) : AiUiEvent()

    /** Clears all messages in the active chat. */
    data object ClearMessages : AiUiEvent()

    /** Consumes the transient error. */
    data object ConsumeError : AiUiEvent()

    /** Resolves the pending approval card ("always allow" remembers in-chat). */
    data class ResolveApproval(val approved: Boolean, val alwaysAllow: Boolean = false) :
        AiUiEvent()
}

/**
 * ViewModel for the unified AI surface: drawer history, assistant selection,
 * the new-chat draft and the active conversation with streaming and approval
 * cards. Tool availability is governed by AI settings, not per chat.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AiViewModel(
    private val chatRepository: AiChatRepository,
    private val assistantRepository: AiAssistantRepository,
    providerRepository: AiProviderRepository,
    private val modelRepository: AiModelRepository,
    private val runChatUseCase: RunChatUseCase,
    private val imageAttachmentLoader: ImageAttachmentLoader,
    actionLogRepository: AiActionLogRepository,
    private val aiSettingsDataStore: AiSettingsDataStore
) : ViewModel() {

    /** Id of the open chat; null while composing a fresh draft. */
    private val activeChatId = MutableStateFlow<Long?>(null)

    /** Assistant pre-selected for the next chat; null = General. */
    private val draftAssistantId = MutableStateFlow<Long?>(null)

    private val draftInput = MutableStateFlow("")
    private val overrides = MutableStateFlow<Map<Long, AiChatMessage>>(emptyMap())
    private val isStreaming = MutableStateFlow(false)
    private val pendingAttachments = MutableStateFlow<List<AiMessagePart.Image>>(emptyList())
    private val error = MutableStateFlow<String?>(null)
    private val pendingApproval = MutableStateFlow<ConfirmationRequest?>(null)

    // Session-scoped "always allow" decisions; never persisted across chats.
    private val alwaysAllowed = mutableSetOf<String>()

    // Single consumer bridges the dispatcher to the approval cards.
    private var confirmationDeferred: CompletableDeferred<Boolean>? = null

    private val approvalBridge = object : ApprovalBridge {

        override suspend fun requestConfirmation(request: ConfirmationRequest): Boolean {
            val deferred = CompletableDeferred<Boolean>()
            confirmationDeferred?.cancel()
            confirmationDeferred = deferred
            pendingApproval.value = request
            try {
                return deferred.await()
            } finally {
                confirmationDeferred = null
                pendingApproval.value = null
            }
        }

        override fun isAlwaysAllowed(toolName: String): Boolean =
            toolName in alwaysAllowed
    }

    // Guards send() against double taps while a draft chat is being created.
    private val sendGate = Mutex(locked = false)

    private var runJob: Job? = null

    init {
        viewModelScope.launch {
            draftAssistantId.value = aiSettingsDataStore.lastAssistantId.first()
        }
    }

    /** Audit log of the active chat ("AI activity" menu entry). */
    val actionLog: StateFlow<List<AiActionLogEntry>> = activeChatId
        .flatMapLatest { id ->
            id?.let(actionLogRepository::observeForChat) ?: flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val chatFlow = activeChatId.flatMapLatest { id ->
        id?.let(chatRepository::observeChat) ?: flowOf(null)
    }

    private val messagesFlow = activeChatId.flatMapLatest { id ->
        id?.let(chatRepository::observeMessages) ?: flowOf(emptyList())
    }

    private data class DrawerData(
        val chats: List<ChatListItem>,
        val assistants: List<AiAssistant>
    )

    private val drawerData = combine(
        chatRepository.observeChats(),
        assistantRepository.observeAssistants()
    ) { chats, assistants ->
        val byId = assistants.associateBy { it.id }
        DrawerData(
            chats = chats.map { chat ->
                val assistant = chat.assistantId?.let { byId[it] }
                ChatListItem(
                    id = chat.id,
                    title = chat.title,
                    assistantName = assistant?.name,
                    assistantIcon = assistant?.avatarIcon,
                    assistantColorIndex = assistant?.avatarColorIndex,
                    updatedAt = chat.updatedAt
                )
            },
            assistants = assistants
        )
    }

    private data class ActiveSlice(
        val id: Long?,
        val chat: AiChat?,
        val messages: List<AiChatMessage>,
        val streaming: Boolean,
        val draftAssistant: Long?
    )

    private val activeSlice = combine(
        chatFlow,
        messagesFlow,
        overrides,
        isStreaming,
        draftAssistantId
    ) { chat, messages, localOverrides, streaming, draftAssistant ->
        ActiveSlice(
            id = chat?.id,
            chat = chat,
            messages = messages.map { localOverrides[it.id] ?: it },
            streaming = streaming,
            draftAssistant = draftAssistant
        )
    }

    private data class Extras(
        val attachments: List<AiMessagePart.Image>,
        val error: String?,
        val approval: ConfirmationRequest?
    )

    private val extras = combine(
        pendingAttachments,
        error,
        pendingApproval
    ) { attachments, err, approval ->
        Extras(attachments, err, approval)
    }

    /**
     * Aggregate UI state combining drawer data, the active conversation and
     * transient composer state.
     */
    val uiState: StateFlow<AiUiState> = combine(
        drawerData,
        providerRepository.observeProviders(),
        activeSlice,
        draftInput,
        extras
    ) { drawer, providers, active, input, extra ->
        val activeAssistant = when {
            active.chat != null ->
                drawer.assistants.firstOrNull { it.id == active.chat.assistantId }
            else ->
                drawer.assistants.firstOrNull { it.id == active.draftAssistant }
        }
        val model = active.chat?.let { resolveModel(it) }
        AiUiState(
            isLoading = false,
            hasProvider = providers.isNotEmpty(),
            chats = drawer.chats,
            assistants = drawer.assistants,
            activeChatId = active.id,
            chat = active.chat,
            draftInput = input,
            activeAssistant = activeAssistant,
            modelLabel = model?.displayName,
            supportsVision = model?.supportsVision ?: false,
            messages = active.messages,
            isStreaming = active.streaming,
            pendingAttachments = extra.attachments,
            pendingApproval = extra.approval,
            error = extra.error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiUiState())

    private suspend fun resolveModel(chat: AiChat): AiModel? {
        val providerId = chat.providerId ?: return null
        val modelId = chat.modelId ?: return null
        return modelRepository.getModel(providerId, modelId)
    }

    /**
     * Handles all AI surface events.
     */
    fun onEvent(event: AiUiEvent) {
        when (event) {
            AiUiEvent.NewChat -> newChat()
            is AiUiEvent.OpenChat -> openChat(event.chatId)
            is AiUiEvent.SelectAssistant -> selectAssistant(event.assistant)
            is AiUiEvent.InputChanged -> draftInput.value = event.text
            is AiUiEvent.Send -> send(event.text)
            is AiUiEvent.AttachImage -> attachImage(event.uri)
            is AiUiEvent.RemoveAttachment -> {
                pendingAttachments.value =
                    pendingAttachments.value.filterIndexed { i, _ -> i != event.index }
            }
            AiUiEvent.Stop -> stop()
            AiUiEvent.Regenerate -> regenerate()
            is AiUiEvent.RenameChat -> viewModelScope.launch {
                chatRepository.updateTitle(event.chatId, event.title)
            }
            is AiUiEvent.DeleteChat -> viewModelScope.launch {
                chatRepository.deleteChat(event.chatId)
                if (event.chatId == activeChatId.value) {
                    newChat()
                }
            }
            AiUiEvent.ClearMessages -> activeChatId.value?.let { id ->
                viewModelScope.launch { chatRepository.clearMessages(id) }
            }
            AiUiEvent.ConsumeError -> error.value = null
            is AiUiEvent.ResolveApproval -> {
                if (event.approved && event.alwaysAllow) {
                    pendingApproval.value?.toolName?.let { alwaysAllowed.add(it) }
                }
                confirmationDeferred?.complete(event.approved)
            }
        }
    }

    /** Resets to a fresh new-chat draft without persisting anything. */
    fun newChat() {
        resetConversationState()
        activeChatId.value = null
    }

    private fun openChat(chatId: Long) {
        resetConversationState()
        activeChatId.value = chatId
    }

    /**
     * Selects the assistant for the next chat; always starts a new draft so
     * a persona switch never rewrites the context of an open conversation.
     */
    private fun selectAssistant(assistant: AiAssistant?) {
        viewModelScope.launch {
            aiSettingsDataStore.setLastAssistantId(assistant?.id)
        }
        draftAssistantId.value = assistant?.id
        resetConversationState()
        activeChatId.value = null
    }

    /**
     * Cancels any streaming run and clears per-conversation transient state.
     */
    private fun resetConversationState() {
        runJob?.cancel()
        runJob = null
        confirmationDeferred?.cancel()
        isStreaming.value = false
        overrides.value = emptyMap()
        pendingAttachments.value = emptyList()
        draftInput.value = ""
        pendingApproval.value = null
    }

    private fun send(text: String) {
        if (text.isBlank() && pendingAttachments.value.isEmpty()) return
        if (isStreaming.value) return
        viewModelScope.launch {
            // Serialize sends so a double tap cannot create two draft chats.
            sendGate.withLock {
                if (isStreaming.value) return@withLock
                val chatId = activeChatId.value ?: chatRepository.createChat(
                    title = DEFAULT_TITLE,
                    assistantId = draftAssistantId.value,
                    providerId = null,
                    modelId = null
                ).also { activeChatId.value = it }
                val parts = buildList {
                    if (text.isNotBlank()) add(AiMessagePart.Text(text))
                    addAll(pendingAttachments.value)
                }
                pendingAttachments.value = emptyList()
                draftInput.value = ""
                chatRepository.appendMessage(
                    AiChatMessage(
                        id = 0,
                        chatId = chatId,
                        role = AiRole.USER,
                        parts = parts,
                        status = AiMessageStatus.COMPLETE,
                        inputTokens = null,
                        outputTokens = null,
                        createdAt = System.currentTimeMillis()
                    )
                )
                autoTitle(chatId, text)
                startRun(chatId)
            }
        }
    }

    private fun attachImage(uri: Uri) {
        viewModelScope.launch {
            try {
                pendingAttachments.value =
                    pendingAttachments.value + imageAttachmentLoader.load(uri)
            } catch (e: Exception) {
                error.value = e.message ?: "Cannot attach image"
            }
        }
    }

    private fun stop() {
        runJob?.cancel()
    }

    private fun regenerate() {
        if (isStreaming.value) return
        val chatId = activeChatId.value ?: return
        viewModelScope.launch {
            val messages = chatRepository.getMessages(chatId)
            val lastUser = messages.lastOrNull { it.role == AiRole.USER } ?: return@launch
            // Drop everything after the last user message (assistant answer
            // and tool activity) so the engine regenerates from the same input.
            val firstAfterUser = messages.firstOrNull { it.id > lastUser.id } ?: return@launch
            chatRepository.deleteFrom(chatId, firstAfterUser.id)
            startRun(chatId)
        }
    }

    private fun startRun(chatId: Long) {
        if (runJob?.isActive == true) return
        isStreaming.value = true
        runJob = viewModelScope.launch {
            try {
                runChatUseCase.runTurn(chatId, approvalBridge).collect { event ->
                    when (event) {
                        is ChatRunEvent.MessageUpdated -> {
                            overrides.value = overrides.value + (event.message.id to event.message)
                        }
                        is ChatRunEvent.TurnFinished -> {
                            if (event.error != null) error.value = event.error
                        }
                    }
                }
            } catch (e: Exception) {
                error.value = e.message ?: "Stream failed"
            } finally {
                isStreaming.value = false
                // Let the DB flow reconcile; drop stale local copies lazily.
                overrides.value = emptyMap()
            }
        }
    }

    private suspend fun autoTitle(chatId: Long, firstUserText: String) {
        val chat = chatRepository.getChat(chatId) ?: return
        if (chat.title != DEFAULT_TITLE) return
        if (firstUserText.isBlank()) return
        val title = firstUserText.trim().replace('\n', ' ')
        chatRepository.updateTitle(
            chatId,
            title.take(TITLE_MAX_LENGTH) + if (title.length > TITLE_MAX_LENGTH) "…" else ""
        )
    }

    private companion object {
        const val DEFAULT_TITLE = "New chat"
        const val TITLE_MAX_LENGTH = 40
    }
}
