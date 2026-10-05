package com.synth.synthmusic.domain.usecase.ai

import com.synth.synthmusic.data.ai.client.AiClientFactory
import com.synth.synthmusic.domain.model.AiCapability
import com.synth.synthmusic.domain.model.AiChat
import com.synth.synthmusic.domain.model.AiChatMessage
import com.synth.synthmusic.domain.model.AiEndpoint
import com.synth.synthmusic.domain.model.AiMessagePart
import com.synth.synthmusic.domain.model.AiMessageStatus
import com.synth.synthmusic.domain.model.AiRole
import com.synth.synthmusic.domain.model.AiToolCall
import com.synth.synthmusic.domain.model.AiTurn
import com.synth.synthmusic.domain.model.ChatStreamEvent
import com.synth.synthmusic.domain.model.StopReason
import com.synth.synthmusic.domain.repository.AiAssistantRepository
import com.synth.synthmusic.domain.repository.AiChatRepository
import com.synth.synthmusic.domain.repository.AiProviderRepository
import com.synth.synthmusic.domain.repository.AiSettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

/** Maximum tool round-trips per user turn before the engine gives up. */
const val MAX_TOOL_ITERATIONS = 8

/** Rough char-per-token ratio used for context budgeting. */
private const val CHARS_PER_TOKEN = 4

/** Transcript cap (in messages) applied before each request. */
private const val MAX_TRANSCRIPT_MESSAGES = 30

/**
 * Events emitted while a chat turn runs.
 */
sealed interface ChatRunEvent {

    /** The assistant message row changed (streaming text, tool parts). */
    data class MessageUpdated(val message: AiChatMessage) : ChatRunEvent

    /** The turn finished; [error] is non-null on failure. */
    data class TurnFinished(val error: String?) : ChatRunEvent
}

/**
 * Chat engine: assembles context, streams model responses, executes tool
 * calls through the [ToolDispatcher] and persists every state transition so
 * the transcript survives anywhere in the UI.
 */
class RunChatUseCase(
    private val chatRepository: AiChatRepository,
    private val providerRepository: AiProviderRepository,
    private val settingsRepository: AiSettingsRepository,
    private val assistantRepository: AiAssistantRepository,
    private val toolDispatcher: ToolDispatcher,
    private val clientFactory: AiClientFactory,
    private val libraryContextProvider: LibraryContextProvider
) {

    /**
     * Runs one assistant turn for [chatId]: loads history, streams the
     * response, executes tool calls and follows up until the model answers
     * without tool requests or [MAX_TOOL_ITERATIONS] is reached.
     *
     * Emits [ChatRunEvent.MessageUpdated] as the assistant message evolves.
     * Cancellation (Stop button) persists the partial answer with status
     * [AiMessageStatus.ERROR] and a "(stopped)" marker.
     */
    fun runTurn(
        chatId: Long,
        approvalBridge: ApprovalBridge? = null
    ): Flow<ChatRunEvent> = flow {
        val chat = chatRepository.getChat(chatId)
        if (chat == null) {
            emit(ChatRunEvent.TurnFinished("Chat not found"))
            return@flow
        }
        val endpoint = resolveEndpoint(chat)
        if (endpoint == null) {
            emit(ChatRunEvent.TurnFinished("No AI provider configured"))
            return@flow
        }
        val systemPrompt = buildSystemPrompt(chat)
        val grants = chatGrants(chat)
        val toolSpecs = toolDispatcher.specsFor(grants)
        android.util.Log.d(
            "SynthAI",
            "Advertising ${toolSpecs.size} tools (grants=$grants)"
        )

        try {
            var iterations = 0
            while (iterations < MAX_TOOL_ITERATIONS) {
                iterations++
                val history = chatRepository.getMessages(chatId)
                    .takeLast(MAX_TRANSCRIPT_MESSAGES)
                val request = com.synth.synthmusic.domain.model.AiChatRequest(
                    endpoint = endpoint,
                    systemPrompt = systemPrompt,
                    turns = history.map { it.toTurn() },
                    tools = toolSpecs
                )
                val client = clientFactory.clientFor(endpoint.protocol)

                val message = createStreamingMessage(chatId)
                var text = StringBuilder()
                var tokensIn: Int? = null
                var tokensOut: Int? = null
                val toolCalls = mutableListOf<AiToolCall>()
                var stopReason: StopReason? = null

                val events = client.streamChat(request)
                var streamError: Throwable? = null
                try {
                    events.collect { event ->
                        when (event) {
                            is ChatStreamEvent.TextDelta -> {
                                text.append(event.text)
                                val updated = message.withParts(
                                    listOf(AiMessagePart.Text(text.toString()))
                                )
                                emit(ChatRunEvent.MessageUpdated(updated))
                            }
                            is ChatStreamEvent.ToolCallReceived -> {
                                toolCalls.add(event.call)
                                emit(
                                    ChatRunEvent.MessageUpdated(
                                        message.withParts(
                                            partsFor(text.toString(), toolCalls)
                                        )
                                    )
                                )
                            }
                            is ChatStreamEvent.Usage -> {
                                tokensIn = event.inputTokens
                                tokensOut = event.outputTokens
                            }
                            is ChatStreamEvent.Completed -> stopReason = event.stopReason
                            is ChatStreamEvent.Failed -> streamError = event.throwable
                        }
                    }
                } catch (e: CancellationException) {
                    persistPartial(chatRepository, message.id, text.toString(), toolCalls, true)
                    throw e
                }

                if (streamError != null) {
                    persistPartial(chatRepository, message.id, text.toString(), toolCalls, false)
                    emit(ChatRunEvent.TurnFinished(streamError?.message ?: "Stream failed"))
                    return@flow
                }

                if (toolCalls.isEmpty()) {
                    finalizeMessage(
                        message.id, text.toString(), toolCalls,
                        tokensIn, tokensOut, stopReason
                    )
                    emit(
                        ChatRunEvent.TurnFinished(
                            when (stopReason) {
                                StopReason.LENGTH -> "Response cut off by length limit"
                                StopReason.CONTENT_FILTER -> "Response blocked by content filter"
                                else -> null
                            }
                        )
                    )
                    return@flow
                }

                // Persist the assistant message with its tool calls, execute
                // each call, and append the tool results as a TOOL message.
                finalizeMessage(
                    message.id, text.toString(), toolCalls, tokensIn, tokensOut, StopReason.TOOL_USE
                )
                val imageSink = mutableListOf<AiMessagePart.Image>()
                val results = toolCalls.map { call ->
                    toolDispatcher.execute(
                        call, ToolContext(chatId, grants, approvalBridge, imageSink)
                    )
                }
                chatRepository.appendMessage(
                    AiChatMessage(
                        id = 0,
                        chatId = chatId,
                        role = AiRole.TOOL,
                        parts = results + imageSink,
                        status = AiMessageStatus.COMPLETE,
                        inputTokens = null,
                        outputTokens = null,
                        createdAt = System.currentTimeMillis()
                    )
                )
                chatRepository.touch(chatId)
                emit(ChatRunEvent.MessageUpdated(message.withParts(partsFor(text.toString(), toolCalls))))
            }
            emit(
                ChatRunEvent.TurnFinished(
                    "Stopped after $MAX_TOOL_ITERATIONS tool iterations"
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emit(ChatRunEvent.TurnFinished(e.message ?: "Unexpected error"))
        }
    }

    private suspend fun resolveEndpoint(chat: AiChat): AiEndpoint? {
        val settings = settingsRepository.current()
        val providerId = chat.providerId ?: settings.activeProviderId ?: return null
        val modelId = chat.modelId ?: settings.activeModelId ?: return null
        val provider = providerRepository.getProvider(providerId) ?: return null
        val apiKey = providerRepository.getApiKey(providerId) ?: return null
        return AiEndpoint(provider.baseUrl, apiKey, modelId, provider.protocol)
    }

    private suspend fun buildSystemPrompt(chat: AiChat): String {
        val assistant = chat.assistantId?.let { assistantRepository.getAssistant(it) }
        val base = assistant?.systemPrompt
            ?: "You are a helpful assistant embedded in the Synth Music offline music player."
        return listOfNotNull(base, libraryContextProvider.contextBlock())
            .joinToString("\n\n")
    }

    /**
     * Effective capability grants: the assistant's default grants (all
     * capabilities for chats without an assistant) intersected with the
     * global kill-switches, so nothing can exceed what AI settings allow.
     */
    private suspend fun chatGrants(chat: AiChat): Set<AiCapability> {
        val settings = settingsRepository.current()
        val globalEnabled = AiCapability.entries.filter { settings.isCapabilityEnabled(it) }.toSet()
        val assistantDefaults = chat.assistantId
            ?.let { assistantRepository.getAssistant(it)?.defaultGrants }
            ?: AiCapability.entries.toSet()
        return assistantDefaults intersect globalEnabled
    }

    private suspend fun createStreamingMessage(chatId: Long): AiChatMessage {
        val id = chatRepository.appendMessage(
            AiChatMessage(
                id = 0,
                chatId = chatId,
                role = AiRole.ASSISTANT,
                parts = emptyList(),
                status = AiMessageStatus.STREAMING,
                inputTokens = null,
                outputTokens = null,
                createdAt = System.currentTimeMillis()
            )
        )
        return AiChatMessage(
            id = id,
            chatId = chatId,
            role = AiRole.ASSISTANT,
            parts = emptyList(),
            status = AiMessageStatus.STREAMING,
            inputTokens = null,
            outputTokens = null,
            createdAt = System.currentTimeMillis()
        )
    }

    private suspend fun finalizeMessage(
        messageId: Long,
        text: String,
        toolCalls: List<AiToolCall>,
        tokensIn: Int?,
        tokensOut: Int?,
        stopReason: StopReason?
    ) {
        chatRepository.getMessage(messageId)?.let { stored ->
            chatRepository.updateMessage(
                stored.copy(
                    parts = partsFor(text, toolCalls),
                    status = AiMessageStatus.COMPLETE,
                    inputTokens = tokensIn,
                    outputTokens = tokensOut
                )
            )
        }
    }

    private fun partsFor(text: String, toolCalls: List<AiToolCall>): List<AiMessagePart> =
        buildList {
            if (text.isNotEmpty()) add(AiMessagePart.Text(text))
            toolCalls.forEach { call ->
                add(
                    AiMessagePart.ToolCall(
                        toolCallId = call.id,
                        name = call.name,
                        argumentsJson = call.argumentsJson
                    )
                )
            }
        }
}

/**
 * Converts a stored message into a protocol-agnostic turn.
 */
fun AiChatMessage.toTurn(): AiTurn = AiTurn(role, parts)

/**
 * Persists a partially streamed answer after stop/error.
 */
internal suspend fun persistPartial(
    chatRepository: AiChatRepository,
    messageId: Long,
    text: String,
    toolCalls: List<AiToolCall>,
    stopped: Boolean
) {
    chatRepository.getMessage(messageId)?.let { stored ->
        val parts = buildList {
            if (text.isNotEmpty()) add(AiMessagePart.Text(text + if (stopped) " (stopped)" else ""))
            toolCalls.forEach { call ->
                add(
                    AiMessagePart.ToolCall(
                        toolCallId = call.id,
                        name = call.name,
                        argumentsJson = call.argumentsJson
                    )
                )
            }
        }
        chatRepository.updateMessage(
            stored.copy(
                parts = parts,
                status = if (stopped) AiMessageStatus.ERROR else AiMessageStatus.ERROR
            )
        )
    }
}
