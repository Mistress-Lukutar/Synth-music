package com.synth.synthmusic.domain.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Role of a message in an AI conversation.
 */
enum class AiRole {
    USER,
    ASSISTANT,
    TOOL,
    SYSTEM
}

/**
 * Persisted status of a chat message.
 */
enum class AiMessageStatus {
    COMPLETE,
    STREAMING,
    ERROR,
    AWAITING_APPROVAL
}

/**
 * Outcome of an audited AI tool action.
 */
enum class AiActionOutcome {
    APPROVED,
    DENIED,
    EXECUTED,
    FAILED
}

/**
 * One content part of an AI message. Parts are stored serialized in Room so
 * ordering of text, tool calls/results and image attachments is preserved.
 */
@Serializable
sealed interface AiMessagePart {

    /** A segment of plain text. */
    @Serializable
    @SerialName("text")
    data class Text(val text: String) : AiMessagePart

    /** An inline base64-encoded image (attachment or vision result). */
    @Serializable
    @SerialName("image")
    data class Image(val mimeType: String, val base64: String) : AiMessagePart

    /** A tool invocation requested by the model. */
    @Serializable
    @SerialName("tool_call")
    data class ToolCall(
        val toolCallId: String,
        val name: String,
        val argumentsJson: String
    ) : AiMessagePart

    /** The app's result for a previously requested tool call. */
    @Serializable
    @SerialName("tool_result")
    data class ToolResult(
        val toolCallId: String,
        val name: String,
        val content: String,
        val isError: Boolean = false
    ) : AiMessagePart

    /**
     * Model reasoning (thinking) content. Displayed as a collapsed spoiler in
     * the chat transcript and never sent back to the provider.
     */
    @Serializable
    @SerialName("reasoning")
    data class Reasoning(val text: String) : AiMessagePart
}

/**
 * A single conversation turn sent to an AI provider.
 */
data class AiTurn(
    val role: AiRole,
    val parts: List<AiMessagePart>
) {
    /** Concatenated text of all [AiMessagePart.Text] parts. */
    val text: String
        get() = parts.filterIsInstance<AiMessagePart.Text>().joinToString("") { it.text }

    /** All image parts carried by this turn. */
    val images: List<AiMessagePart.Image>
        get() = parts.filterIsInstance<AiMessagePart.Image>()

    /** All tool-call parts carried by this turn. */
    val toolCalls: List<AiMessagePart.ToolCall>
        get() = parts.filterIsInstance<AiMessagePart.ToolCall>()

    /** All tool-result parts carried by this turn. */
    val toolResults: List<AiMessagePart.ToolResult>
        get() = parts.filterIsInstance<AiMessagePart.ToolResult>()
}

/**
 * Schema-driven description of a tool advertised to the model.
 */
data class AiToolSpec(
    val name: String,
    val description: String,
    val paramsSchema: kotlinx.serialization.json.JsonObject
)

/**
 * A tool invocation requested by the model during a stream.
 */
data class AiToolCall(
    val id: String,
    val name: String,
    val argumentsJson: String
)

/**
 * Terminal reason reported by a provider when generation stops.
 */
enum class StopReason {
    END_TURN,
    TOOL_USE,
    LENGTH,
    CONTENT_FILTER,
    ERROR,
    OTHER
}

/**
 * Endpoint + credentials resolved for a single chat request.
 */
data class AiEndpoint(
    val baseUrl: String,
    val apiKey: String,
    val modelId: String,
    val protocol: AiProtocol
)

/**
 * Protocol-agnostic chat completion request.
 */
data class AiChatRequest(
    val endpoint: AiEndpoint,
    val systemPrompt: String?,
    val turns: List<AiTurn>,
    val tools: List<AiToolSpec>,
    val temperature: Double = 0.7,
    val maxTokens: Int = 4096
)

/**
 * Events streamed back by an [com.synth.synthmusic.data.ai.client.AiChatClient].
 */
sealed interface ChatStreamEvent {

    /** Incremental text output. */
    data class TextDelta(val text: String) : ChatStreamEvent

    /** Incremental reasoning (thinking) output. */
    data class ReasoningDelta(val text: String) : ChatStreamEvent

    /** The model requested a tool call (models may batch several). */
    data class ToolCallReceived(val call: AiToolCall) : ChatStreamEvent

    /** Token usage reported by the provider. */
    data class Usage(val inputTokens: Int, val outputTokens: Int) : ChatStreamEvent

    /** Generation finished with a terminal reason. */
    data class Completed(val stopReason: StopReason) : ChatStreamEvent

    /** Transport or protocol failure. */
    data class Failed(val throwable: Throwable, val httpCode: Int? = null) : ChatStreamEvent
}
