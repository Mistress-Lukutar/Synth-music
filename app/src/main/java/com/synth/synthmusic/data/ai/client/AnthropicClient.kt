package com.synth.synthmusic.data.ai.client

import com.synth.synthmusic.domain.model.AiChatRequest
import com.synth.synthmusic.domain.model.AiMessagePart
import com.synth.synthmusic.domain.model.AiRole
import com.synth.synthmusic.domain.model.AiToolCall
import com.synth.synthmusic.domain.model.AiTurn
import com.synth.synthmusic.domain.model.ChatStreamEvent
import com.synth.synthmusic.domain.model.StopReason
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Chat client for the Anthropic Messages API.
 *
 * Anthropic requires strictly alternating user/assistant messages and routes
 * tool results as `tool_result` content blocks inside a user message, so the
 * turn list is normalized accordingly before serialization.
 */
class AnthropicClient(
    httpClient: OkHttpClient,
    json: Json
) : OkHttpChatClient(httpClient, json) {

    override fun buildRequest(request: AiChatRequest): Request {
        val body = buildJsonObject {
            put("model", request.endpoint.modelId)
            put("stream", true)
            put("max_tokens", request.maxTokens)
            put("temperature", request.temperature)
            request.systemPrompt?.let { put("system", it) }
            putJsonArray("messages") {
                normalizeTurns(request.turns).forEach { turn ->
                    add(buildJsonObject {
                        put(
                            "role",
                            if (turn.role == AiRole.ASSISTANT) "assistant" else "user"
                        )
                        put("content", turn.toAnthropicBlocks())
                    })
                }
            }
            if (request.tools.isNotEmpty()) {
                putJsonArray("tools") {
                    request.tools.forEach { tool ->
                        add(
                            buildJsonObject {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("input_schema", tool.paramsSchema)
                            }
                        )
                    }
                }
            }
        }.toString()

        return buildJsonPostRequest(
            url = request.endpoint.baseUrl.trimEnd('/') + "/messages",
            body = body,
            headers = mapOf(
                "x-api-key" to request.endpoint.apiKey,
                "anthropic-version" to ANTHROPIC_VERSION,
                "Accept" to "text/event-stream"
            )
        )
    }

    override fun isTerminal(frame: SseFrame): Boolean = frame.event == "message_stop"

    override fun parseFrame(
        frame: SseFrame,
        state: MutableMap<String, Any?>
    ): List<ChatStreamEvent> {
        val root = runCatching { json.parseToJsonElement(frame.data).jsonObject }.getOrNull()
            ?: return emptyList()

        when (frame.event ?: "data") {
            "error" -> {
                val message = (root["error"] as? JsonObject)
                    ?.get("message")?.jsonPrimitive?.content ?: frame.data
                return listOf(ChatStreamEvent.Failed(RuntimeException(message)))
            }
            "ping" -> return emptyList()
        }

        val events = mutableListOf<ChatStreamEvent>()
        when (frame.event) {
            "message_start" -> {
                val input = root["message"]?.jsonObject
                    ?.get("usage")?.jsonObject
                    ?.get("input_tokens")?.jsonPrimitive?.content?.toIntOrNull() ?: 0
                state[KEY_INPUT_TOKENS] = input
            }
            "content_block_start" -> {
                val block = root["content_block"]?.jsonObject
                if (block?.get("type")?.jsonPrimitive?.content == "tool_use") {
                    state[KEY_PENDING_TOOL] = PendingToolCall(
                        id = block["id"]?.jsonPrimitive?.content.orEmpty(),
                        name = block["name"]?.jsonPrimitive?.content.orEmpty(),
                        json = StringBuilder()
                    )
                }
            }
            "content_block_delta" -> {
                val delta = root["delta"]?.jsonObject
                when (delta?.get("type")?.jsonPrimitive?.content) {
                    "text_delta" -> delta["text"]?.jsonPrimitive?.content?.let { text ->
                        if (text.isNotEmpty()) events.add(ChatStreamEvent.TextDelta(text))
                    }
                    "input_json_delta" -> {
                        val pending = state[KEY_PENDING_TOOL] as? PendingToolCall
                        pending?.json?.append(
                            delta["partial_json"]?.jsonPrimitive?.content.orEmpty()
                        )
                    }
                }
            }
            "content_block_stop" -> {
                val pending = state[KEY_PENDING_TOOL] as? PendingToolCall
                if (pending != null) {
                    events.add(
                        ChatStreamEvent.ToolCallReceived(
                            AiToolCall(
                                id = pending.id,
                                name = pending.name,
                                argumentsJson = pending.json.toString().ifEmpty { "{}" }
                            )
                        )
                    )
                    state[KEY_PENDING_TOOL] = null
                }
            }
            "message_delta" -> {
                root["delta"]?.jsonObject
                    ?.get("stop_reason")?.jsonPrimitive?.content
                    ?.let { reason ->
                        events.add(ChatStreamEvent.Completed(mapAnthropicStopReason(reason)))
                    }
                root["usage"]?.jsonObject
                    ?.get("output_tokens")?.jsonPrimitive?.content?.toIntOrNull()
                    ?.let { output ->
                        val input = state[KEY_INPUT_TOKENS] as? Int ?: 0
                        events.add(ChatStreamEvent.Usage(input, output))
                    }
            }
        }
        return events
    }

    /**
     * Normalizes the transcript into strictly alternating user/assistant
     * messages, folding tool results into user turns as required.
     */
    private fun normalizeTurns(turns: List<AiTurn>): List<AiTurn> {
        val mapped = turns.mapNotNull { turn ->
            when (turn.role) {
                AiRole.SYSTEM -> null
                AiRole.USER, AiRole.TOOL -> AiTurn(AiRole.USER, turn.parts)
                AiRole.ASSISTANT -> turn
            }
        }
        val merged = mutableListOf<AiTurn>()
        for (turn in mapped) {
            val previous = merged.lastOrNull()
            if (previous != null && previous.role == turn.role) {
                merged[merged.size - 1] = AiTurn(turn.role, previous.parts + turn.parts)
            } else {
                merged.add(turn)
            }
        }
        return merged
    }

    private fun AiTurn.toAnthropicBlocks(): kotlinx.serialization.json.JsonArray =
        buildJsonArray {
            parts.forEach { part ->
                when (part) {
                    is AiMessagePart.Text -> add(
                        buildJsonObject {
                            put("type", "text")
                            put("text", part.text)
                        }
                    )
                    is AiMessagePart.Image -> add(
                        buildJsonObject {
                            put("type", "image")
                            putJsonObject("source") {
                                put("type", "base64")
                                put("media_type", part.mimeType)
                                put("data", part.base64)
                            }
                        }
                    )
                    is AiMessagePart.ToolCall -> add(
                        buildJsonObject {
                            put("type", "tool_use")
                            put("id", part.toolCallId)
                            put("name", part.name)
                            put("input", parseArgsOrEmpty(part.argumentsJson))
                        }
                    )
                    is AiMessagePart.ToolResult -> add(
                        buildJsonObject {
                            put("type", "tool_result")
                            put("tool_use_id", part.toolCallId)
                            put("is_error", part.isError)
                            put("content", part.content)
                        }
                    )
                }
            }
        }

    private data class PendingToolCall(
        val id: String,
        val name: String,
        val json: StringBuilder
    )

    private companion object {
        const val ANTHROPIC_VERSION = "2023-06-01"
        const val KEY_INPUT_TOKENS = "input_tokens"
        const val KEY_PENDING_TOOL = "pending_tool"

        fun mapAnthropicStopReason(raw: String): StopReason = when (raw) {
            "end_turn", "stop_sequence" -> StopReason.END_TURN
            "tool_use" -> StopReason.TOOL_USE
            "max_tokens" -> StopReason.LENGTH
            "refusal" -> StopReason.CONTENT_FILTER
            else -> StopReason.OTHER
        }
    }
}
