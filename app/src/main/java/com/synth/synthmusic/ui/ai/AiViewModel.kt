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
import kotlinx.coroutines.CancellationException
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

    /** Live copies of streaming messages, keyed by chat then message id. */
    private val overrides = MutableStateFlow<Map<Long, Map<Long, AiChatMessage>>>(emptyMap())

    /** Chats whose generation is currently running. */
    private val streamingChats = MutableStateFlow<Set<Long>>(emptySet())
    private val pendingAttachments = MutableStateFlow<List<AiMessagePart.Image>>(emptyList())
    private val error = MutableStateFlow<String?>(null)
    private val pendingApproval = MutableStateFlow<ChatApproval?>(null)

    /** One run job per generating chat; runs survive leaving the surface. */
    private val runJobs = mutableMapOf<Long, Job>()

    // Session-scoped "always allow" decisions; never persisted across chats.
    private val alwaysAllowed = mutableSetOf<String>()

    // Single consumer bridges the dispatcher to the approval cards.
    private var confirmationDeferred: CompletableDeferred<Boolean>? = null

    // Guards send() against double taps while a draft chat is being created.
    private val sendGate = Mutex(locked = false)

    /**
     * An approval request scoped to the chat whose run raised it, so the
     * card is only shown inside that conversation.
     */
    private data class ChatApproval(
        val chatId: Long,
        val request: ConfirmationRequest
    )

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
        streamingChats,
        draftAssistantId
    ) { chat, messages, allOverrides, streaming, draftAssistant ->
        ActiveSlice(
            id = chat?.id,
            chat = chat,
            messages = messages.map { allOverrides[chat?.id]?.get(it.id) ?: it },
            streaming = chat?.id in streaming,
            draftAssistant = draftAssistant
        )
    }

    private data class Extras(
        val attachments: List<AiMessagePart.Image>,
        val error: String?,
        val approval: ChatApproval?
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
            pendingApproval = extra.approval?.takeIf { it.chatId == active.id }?.request,
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
                // A run writing into a deleted chat must not keep going.
                runJobs[event.chatId]?.cancel()
                chatRepository.deleteChat(event.chatId)
                if (event.chatId == activeChatId.value) {
                    newChat()
                }
            }
            AiUiEvent.ClearMessages -> activeChatId.value?.let { id ->
                runJobs[id]?.cancel()
                viewModelScope.launch { chatRepository.clearMessages(id) }
            }
            AiUiEvent.ConsumeError -> error.value = null
            is AiUiEvent.ResolveApproval -> {
                if (event.approved && event.alwaysAllow) {
                    pendingApproval.value?.request?.toolName?.let { alwaysAllowed.add(it) }
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
     * Clears per-composer transient state. Deliberately does NOT touch
     * running generations: they keep streaming in the background and only
     * the Stop button (or process death) cancels them.
     */
    private fun resetConversationState() {
        pendingAttachments.value = emptyList()
        draftInput.value = ""
    }

    private fun send(text: String) {
        if (text.isBlank() && pendingAttachments.value.isEmpty()) return
        viewModelScope.launch {
            // Serialize sends so a double tap cannot create two draft chats.
            sendGate.withLock {
                val chatId = activeChatId.value ?: chatRepository.createChat(
                    title = DEFAULT_TITLE,
                    assistantId = draftAssistantId.value,
                    providerId = null,
                    modelId = null
                ).also { activeChatId.value = it }
                if (chatId in streamingChats.value) return@withLock
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

    /** Cancels the generation of the chat that is currently open. */
    private fun stop() {
        activeChatId.value?.let { id -> runJobs[id]?.cancel() }
    }

    private fun regenerate() {
        val chatId = activeChatId.value ?: return
        if (chatId in streamingChats.value) return
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
        if (runJobs[chatId]?.isActive == true) return
        streamingChats.value = streamingChats.value + chatId
        runJobs[chatId] = viewModelScope.launch {
            try {
                runChatUseCase.runTurn(chatId, approvalBridgeFor(chatId)).collect { event ->
                    when (event) {
                        is ChatRunEvent.MessageUpdated -> {
                            val forChat =
                                overrides.value[chatId].orEmpty() + (event.message.id to event.message)
                            overrides.value = overrides.value + (chatId to forChat)
                        }
                        is ChatRunEvent.TurnFinished -> {
                            if (event.error != null) error.value = event.error
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = e.message ?: "Stream failed"
            } finally {
                runJobs.remove(chatId)
                streamingChats.value = streamingChats.value - chatId
                // Let the DB flow reconcile; drop stale local copies lazily.
                overrides.value = overrides.value - chatId
            }
        }
    }

    /**
     * Builds an [ApprovalBridge] tagged with [chatId] so approval cards only
     * appear inside the conversation that raised them.
     */
    private fun approvalBridgeFor(chatId: Long) = object : ApprovalBridge {

        override suspend fun requestConfirmation(request: ConfirmationRequest): Boolean {
            val deferred = CompletableDeferred<Boolean>()
            confirmationDeferred?.cancel()
            confirmationDeferred = deferred
            pendingApproval.value = ChatApproval(chatId, request)
            try {
                return deferred.await()
            } finally {
                if (confirmationDeferred === deferred) {
                    confirmationDeferred = null
                    pendingApproval.value = null
                }
            }
        }

        override fun isAlwaysAllowed(toolName: String): Boolean =
            toolName in alwaysAllowed
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
