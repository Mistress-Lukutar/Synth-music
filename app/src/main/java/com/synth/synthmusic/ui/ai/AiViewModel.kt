package com.synth.synthmusic.ui.ai

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synth.synthmusic.data.ai.AssistantAvatarStore
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
import com.synth.synthmusic.domain.repository.AiSettingsRepository
import com.synth.synthmusic.domain.repository.AiProviderRepository
import com.synth.synthmusic.domain.usecase.ai.ApprovalBridge
import com.synth.synthmusic.domain.usecase.ai.ChatRunEvent
import com.synth.synthmusic.domain.usecase.ai.ChatTree
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
    val assistantImage: String?,
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
    /** Whether the active model supports vision (image input), including user override. */
    val supportsVision: Boolean = false,
    /** The active branch of the conversation tree, root first. */
    val messages: List<AiChatMessage> = emptyList(),
    /** Variant position of path messages among their siblings, for `< k/n >`. */
    val variants: Map<Long, ChatTree.Variants> = emptyMap(),
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

    /** Attaches images from photo-picker [uris]. */
    data class AttachImages(val uris: List<Uri>) : AiUiEvent()

    /** Removes a pending attachment by index. */
    data class RemoveAttachment(val index: Int) : AiUiEvent()

    /** Cancels the running stream. */
    data object Stop : AiUiEvent()

    /**
     * Regenerates from the given message: a user message re-asks its
     * following answer, an assistant message gets a new sibling. The old
     * branch is kept and reachable via variant switching.
     */
    data class Retry(val messageId: Long) : AiUiEvent()

    /**
     * Edits a user message: appends a sibling with the new text (original
     * image attachments preserved) and runs the turn from there.
     */
    data class EditMessage(val messageId: Long, val newText: String) : AiUiEvent()

    /** Switches the conversation to the branch of the given variant. */
    data class SwitchVariant(val messageId: Long) : AiUiEvent()

    /** Regenerates the latest assistant answer of the open chat. */
    data object Regenerate : AiUiEvent()

    /** Renames the given chat. */
    data class RenameChat(val chatId: Long, val title: String) : AiUiEvent()

    /** Deletes the given chat and returns to a draft if it was open. */
    data class DeleteChat(val chatId: Long) : AiUiEvent()

    /** Deletes a custom assistant persona together with its avatar image. */
    data class DeleteAssistant(val assistantId: Long, val avatarImage: String?) : AiUiEvent()

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
    private val aiSettingsRepository: AiSettingsRepository,
    private val runChatUseCase: RunChatUseCase,
    private val imageAttachmentLoader: ImageAttachmentLoader,
    actionLogRepository: AiActionLogRepository,
    private val aiSettingsDataStore: AiSettingsDataStore,
    private val avatarStore: AssistantAvatarStore
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
                    assistantImage = assistant?.avatarImage,
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
        val variants: Map<Long, ChatTree.Variants>,
        val streaming: Boolean,
        val draftAssistant: Long?
    )

    private val activeSlice = combine(
        chatFlow,
        messagesFlow,
        overrides,
        streamingChats,
        draftAssistantId
    ) { chat, allMessages, allOverrides, streaming, draftAssistant ->
        val merged = allMessages.map { allOverrides[chat?.id]?.get(it.id) ?: it }
        val path = ChatTree.activePath(merged, chat?.activeLeafId)
        ActiveSlice(
            id = chat?.id,
            chat = chat,
            messages = path,
            variants = ChatTree.variantsOf(merged, path.map { it.id }),
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
        val model = resolveModel(active.chat)
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
            supportsVision = model?.effectiveSupportsVision ?: false,
            messages = active.messages,
            variants = active.variants,
            isStreaming = active.streaming,
            pendingAttachments = extra.attachments,
            pendingApproval = extra.approval?.takeIf { it.chatId == active.id }?.request,
            error = extra.error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiUiState())

    // Mirrors RunChatUseCase.resolveTarget: a chat's pinned provider/model wins,
    // otherwise the default from AI settings; feeds the model label and the
    // vision gate on the image-attach button (also on the new-chat draft).
    private suspend fun resolveModel(chat: AiChat?): AiModel? {
        val settings = aiSettingsRepository.current()
        val providerId = chat?.providerId ?: settings.activeProviderId ?: return null
        val modelId = chat?.modelId ?: settings.activeModelId ?: return null
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
            is AiUiEvent.AttachImages -> attachImages(event.uris)
            is AiUiEvent.RemoveAttachment -> {
                pendingAttachments.value =
                    pendingAttachments.value.filterIndexed { i, _ -> i != event.index }
            }
            AiUiEvent.Stop -> stop()
            is AiUiEvent.Retry -> retryMessage(event.messageId)
            is AiUiEvent.EditMessage -> editMessage(event.messageId, event.newText)
            is AiUiEvent.SwitchVariant -> switchVariant(event.messageId)
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
            is AiUiEvent.DeleteAssistant -> viewModelScope.launch {
                assistantRepository.deleteAssistant(event.assistantId)
                avatarStore.delete(event.avatarImage)
                if (draftAssistantId.value == event.assistantId) {
                    draftAssistantId.value = null
                    aiSettingsDataStore.setLastAssistantId(null)
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
                // New messages extend the branch currently being viewed.
                val parentLeaf = ChatTree.activePath(
                    chatRepository.getMessages(chatId),
                    chatRepository.getChat(chatId)?.activeLeafId
                ).lastOrNull()?.id
                val messageId = chatRepository.appendMessage(
                    AiChatMessage(
                        id = 0,
                        chatId = chatId,
                        role = AiRole.USER,
                        parentId = parentLeaf,
                        parts = parts,
                        status = AiMessageStatus.COMPLETE,
                        inputTokens = null,
                        outputTokens = null,
                        createdAt = System.currentTimeMillis()
                    )
                )
                chatRepository.setActiveLeaf(chatId, messageId)
                autoTitle(chatId, text)
                startRun(chatId, messageId)
            }
        }
    }

    private fun attachImages(uris: List<Uri>) {
        viewModelScope.launch {
            try {
                val loaded = uris.map { imageAttachmentLoader.load(it) }
                val room = MAX_IMAGES_PER_MESSAGE - pendingAttachments.value.size
                if (room <= 0) {
                    error.value = "Up to $MAX_IMAGES_PER_MESSAGE images per message"
                    return@launch
                }
                if (loaded.size > room) {
                    error.value = "Up to $MAX_IMAGES_PER_MESSAGE images per message"
                }
                pendingAttachments.value = pendingAttachments.value + loaded.take(room)
            } catch (e: Exception) {
                error.value = e.message ?: "Cannot attach image"
            }
        }
    }

    /** Cancels the generation of the chat that is currently open. */
    private fun stop() {
        activeChatId.value?.let { id -> runJobs[id]?.cancel() }
    }

    private fun retryMessage(messageId: Long) {
        val chatId = activeChatId.value ?: return
        if (chatId in streamingChats.value) return
        viewModelScope.launch {
            val message = chatRepository.getMessage(messageId) ?: return@launch
            when (message.role) {
                // A user message re-asks the answer that followed it.
                AiRole.USER -> startRun(chatId, message.id)
                // An assistant message gets a new sibling under its parent.
                AiRole.ASSISTANT -> {
                    val parentId = message.parentId ?: return@launch
                    startRun(chatId, parentId)
                }
                else -> Unit
            }
        }
    }

    private fun editMessage(messageId: Long, newText: String) {
        val chatId = activeChatId.value ?: return
        if (chatId in streamingChats.value) return
        if (newText.isBlank()) return
        viewModelScope.launch {
            val original = chatRepository.getMessage(messageId) ?: return@launch
            if (original.role != AiRole.USER) return@launch
            // Sibling of the original user message: same parent, images kept.
            val parts = buildList {
                add(AiMessagePart.Text(newText))
                addAll(original.parts.filterIsInstance<AiMessagePart.Image>())
            }
            val newId = chatRepository.appendMessage(
                AiChatMessage(
                    id = 0,
                    chatId = chatId,
                    role = AiRole.USER,
                    parentId = original.parentId,
                    parts = parts,
                    status = AiMessageStatus.COMPLETE,
                    inputTokens = null,
                    outputTokens = null,
                    createdAt = System.currentTimeMillis()
                )
            )
            chatRepository.setActiveLeaf(chatId, newId)
            startRun(chatId, newId)
        }
    }

    private fun switchVariant(messageId: Long) {
        val chatId = activeChatId.value ?: return
        if (chatId in streamingChats.value) return
        viewModelScope.launch {
            val all = chatRepository.getMessages(chatId)
            // Jump to the newest leaf of the chosen branch.
            chatRepository.setActiveLeaf(chatId, ChatTree.newestDescendant(all, messageId))
        }
    }

    private fun regenerate() {
        val chatId = activeChatId.value ?: return
        if (chatId in streamingChats.value) return
        viewModelScope.launch {
            val chat = chatRepository.getChat(chatId) ?: return@launch
            val path = ChatTree.activePath(
                chatRepository.getMessages(chatId),
                chat.activeLeafId
            )
            // Non-destructive retry of the last answer on the visible branch.
            val lastAssistant = path.lastOrNull { it.role == AiRole.ASSISTANT }
                ?: return@launch
            startRun(chatId, lastAssistant.parentId ?: return@launch)
        }
    }

    private fun startRun(chatId: Long, parentMessageId: Long?) {
        if (runJobs[chatId]?.isActive == true) return
        streamingChats.value = streamingChats.value + chatId
        runJobs[chatId] = viewModelScope.launch {
            try {
                runChatUseCase.runTurn(chatId, approvalBridgeFor(chatId), parentMessageId)
                    .collect { event ->
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

        /** Image attachments allowed in one outgoing message. */
        const val MAX_IMAGES_PER_MESSAGE = 8
    }
}
