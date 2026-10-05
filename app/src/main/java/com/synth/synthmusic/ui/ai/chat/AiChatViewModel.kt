package com.synth.synthmusic.ui.ai.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synth.synthmusic.data.ai.ImageAttachmentLoader
import com.synth.synthmusic.domain.model.AiCapability
import com.synth.synthmusic.domain.model.AiChat
import com.synth.synthmusic.domain.model.AiChatMessage
import com.synth.synthmusic.domain.model.AiMessagePart
import com.synth.synthmusic.domain.model.AiMessageStatus
import com.synth.synthmusic.domain.model.AiRole
import com.synth.synthmusic.domain.model.AiModel
import com.synth.synthmusic.domain.repository.AiAssistantRepository
import com.synth.synthmusic.domain.repository.AiChatRepository
import com.synth.synthmusic.domain.repository.AiModelRepository
import com.synth.synthmusic.domain.usecase.ai.ApprovalBridge
import com.synth.synthmusic.domain.usecase.ai.ChatRunEvent
import com.synth.synthmusic.domain.usecase.ai.ConfirmationRequest
import com.synth.synthmusic.domain.usecase.ai.RunChatUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * UI state of a single chat screen.
 */
data class AiChatUiState(
    val chat: AiChat? = null,
    val assistantName: String? = null,
    val modelLabel: String? = null,
    val supportsVision: Boolean = false,
    val messages: List<AiChatMessage> = emptyList(),
    val isStreaming: Boolean = false,
    val pendingAttachments: List<AiMessagePart.Image> = emptyList(),
    val pendingApproval: ConfirmationRequest? = null,
    val pendingGrantRequest: Set<AiCapability>? = null,
    val grants: Map<AiCapability, Boolean> = emptyMap(),
    val error: String? = null
)

/**
 * Events the chat screen can send to its ViewModel.
 */
sealed class AiChatUiEvent {
    /** Sends a user message with the current pending attachments. */
    data class Send(val text: String) : AiChatUiEvent()

    /** Attaches an image from a photo-picker [uri]. */
    data class AttachImage(val uri: Uri) : AiChatUiEvent()

    /** Removes a pending attachment by index. */
    data class RemoveAttachment(val index: Int) : AiChatUiEvent()

    /** Cancels the running stream. */
    data object Stop : AiChatUiEvent()

    /** Deletes the last assistant answer and re-runs the turn. */
    data object Regenerate : AiChatUiEvent()

    /** Renames the chat. */
    data class Rename(val title: String) : AiChatUiEvent()

    /** Deletes the whole chat. */
    data object DeleteChat : AiChatUiEvent()

    /** Clears all messages in the chat. */
    data object ClearMessages : AiChatUiEvent()

    /** Consumes the transient error. */
    data object ConsumeError : AiChatUiEvent()

    /** Resolves the pending approval card ("always allow" remembers in-chat). */
    data class ResolveApproval(val approved: Boolean, val alwaysAllow: Boolean = false) :
        AiChatUiEvent()

    /** Resolves the pending permission-grant prompt. */
    data class ResolveGrant(val granted: Boolean) : AiChatUiEvent()

    /** Toggles an explicit per-chat capability grant from the menu dialog. */
    data class SetGrant(val capability: AiCapability, val granted: Boolean) : AiChatUiEvent()
}

/**
 * ViewModel for the chat screen: message list observation, sending,
 * streaming, stop and regenerate.
 */
class AiChatViewModel(
    private val chatId: Long,
    private val chatRepository: AiChatRepository,
    private val assistantRepository: AiAssistantRepository,
    private val modelRepository: AiModelRepository,
    private val runChatUseCase: RunChatUseCase,
    private val imageAttachmentLoader: ImageAttachmentLoader,
    actionLogRepository: com.synth.synthmusic.domain.repository.AiActionLogRepository
) : ViewModel() {

    private val overrides = MutableStateFlow<Map<Long, AiChatMessage>>(emptyMap())
    private val isStreaming = MutableStateFlow(false)
    private val pendingAttachments = MutableStateFlow<List<AiMessagePart.Image>>(emptyList())
    private val error = MutableStateFlow<String?>(null)
    private val pendingApproval = MutableStateFlow<ConfirmationRequest?>(null)
    private val pendingGrantRequest = MutableStateFlow<Set<AiCapability>?>(null)
    private val grantsState = MutableStateFlow<Map<AiCapability, Boolean>>(emptyMap())

    // Session-scoped "always allow" decisions; never persisted across chats.
    private val alwaysAllowed = mutableSetOf<String>()

    // Single consumer bridges the dispatcher to the approval cards.
    private var confirmationDeferred: CompletableDeferred<Boolean>? = null
    private var grantDeferred: CompletableDeferred<Boolean>? = null

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

        override suspend fun requestPermissionGrant(missing: Set<AiCapability>): Boolean {
            val deferred = CompletableDeferred<Boolean>()
            grantDeferred?.cancel()
            grantDeferred = deferred
            pendingGrantRequest.value = missing
            try {
                return deferred.await()
            } finally {
                grantDeferred = null
                pendingGrantRequest.value = null
            }
        }

        override fun isAlwaysAllowed(toolName: String): Boolean =
            toolName in alwaysAllowed
    }

    init {
        viewModelScope.launch { grantsState.value = chatRepository.getGrants(chatId) }
    }

    /** Audit log of this chat ("AI activity" menu entry). */
    val actionLog: StateFlow<List<com.synth.synthmusic.domain.model.AiActionLogEntry>> =
        actionLogRepository.observeForChat(chatId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var runJob: Job? = null

    /**
     * Aggregate UI state combining the chat, messages, streaming overlay and
     * attachments.
     */
    val uiState: StateFlow<AiChatUiState> = combine(
        chatRepository.observeChat(chatId),
        chatRepository.observeMessages(chatId),
        overrides,
        isStreaming,
        combine(
            pendingAttachments,
            error,
            pendingApproval,
            pendingGrantRequest,
            grantsState
        ) { attachments, err, approval, grantReq, grants ->
            CombinedUi(attachments, err, approval, grantReq, grants)
        }
    ) { chat, messages, localOverrides, streaming, extra ->
        val effective = messages.map { localOverrides[it.id] ?: it }
        val model = chat?.let { resolveModel(it) }
        AiChatUiState(
            chat = chat,
            assistantName = chat?.assistantId?.let { id ->
                assistantRepository.getAssistant(id)?.name
            },
            modelLabel = model?.displayName,
            supportsVision = model?.supportsVision ?: false,
            messages = effective,
            isStreaming = streaming,
            pendingAttachments = extra.attachments,
            pendingApproval = extra.approval,
            pendingGrantRequest = extra.grantRequest,
            grants = extra.grants,
            error = extra.error
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AiChatUiState())

    private data class CombinedUi(
        val attachments: List<AiMessagePart.Image>,
        val error: String?,
        val approval: ConfirmationRequest?,
        val grantRequest: Set<AiCapability>?,
        val grants: Map<AiCapability, Boolean>
    )

    private suspend fun resolveModel(chat: AiChat): AiModel? {
        val providerId = chat.providerId ?: return null
        val modelId = chat.modelId ?: return null
        return modelRepository.getModel(providerId, modelId)
    }

    /**
     * Handles all chat screen events.
     */
    fun onEvent(event: AiChatUiEvent) {
        when (event) {
            is AiChatUiEvent.Send -> send(event.text)
            is AiChatUiEvent.AttachImage -> attachImage(event.uri)
            is AiChatUiEvent.RemoveAttachment -> {
                pendingAttachments.value =
                    pendingAttachments.value.filterIndexed { i, _ -> i != event.index }
            }
            AiChatUiEvent.Stop -> stop()
            AiChatUiEvent.Regenerate -> regenerate()
            is AiChatUiEvent.Rename -> viewModelScope.launch {
                chatRepository.updateTitle(chatId, event.title)
            }
            AiChatUiEvent.DeleteChat -> viewModelScope.launch {
                chatRepository.deleteChat(chatId)
            }
            AiChatUiEvent.ClearMessages -> viewModelScope.launch {
                chatRepository.clearMessages(chatId)
            }
            AiChatUiEvent.ConsumeError -> error.value = null
            is AiChatUiEvent.ResolveApproval -> {
                if (event.approved && event.alwaysAllow) {
                    pendingApproval.value?.toolName?.let { alwaysAllowed.add(it) }
                }
                confirmationDeferred?.complete(event.approved)
            }
            is AiChatUiEvent.ResolveGrant -> {
                grantDeferred?.complete(event.granted)
            }
            is AiChatUiEvent.SetGrant -> viewModelScope.launch {
                chatRepository.setGrant(chatId, event.capability, event.granted)
                grantsState.value = chatRepository.getGrants(chatId)
            }
        }
    }

    private fun send(text: String) {
        if (text.isBlank() && pendingAttachments.value.isEmpty()) return
        if (isStreaming.value) return
        viewModelScope.launch {
            val parts = buildList {
                if (text.isNotBlank()) add(AiMessagePart.Text(text))
                addAll(pendingAttachments.value)
            }
            pendingAttachments.value = emptyList()
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
            autoTitle(text)
            startRun()
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
        viewModelScope.launch {
            val messages = chatRepository.getMessages(chatId)
            val lastUser = messages.lastOrNull { it.role == AiRole.USER } ?: return@launch
            // Drop everything after the last user message (assistant answer
            // and tool activity) so the engine regenerates from the same input.
            val firstAfterUser = messages.firstOrNull { it.id > lastUser.id } ?: return@launch
            chatRepository.deleteFrom(chatId, firstAfterUser.id)
            startRun()
        }
    }
    private fun startRun() {
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

    private suspend fun autoTitle(firstUserText: String) {
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
