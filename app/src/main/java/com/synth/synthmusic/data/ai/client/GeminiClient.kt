package com.synth.synthmusic.data.ai.client

import com.synth.synthmusic.domain.model.AiChatRequest
import com.synth.synthmusic.domain.model.AiMessagePart
import com.synth.synthmusic.domain.model.AiRole
import com.synth.synthmusic.domain.model.AiTurn
import com.synth.synthmusic.domain.model.ChatStreamEvent
import com.synth.synthmusic.domain.model.StopReason
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Chat client for the Google Gemini API (streaming via
 * `streamGenerateContent?alt=sse`, auth via `x-goog-api-key` header).
 *
 * Gemini function calls carry no id, so stable per-stream ids are synthesized.
 */
class GeminiClient(
    httpClient: OkHttpClient,
    json: Json
) : OkHttpChatClient(httpClient, json) {

    override fun buildRequest(request: AiChatRequest): Request {
        val body = buildJsonObject {
            request.systemPrompt?.let { system ->
                putJsonObject("systemInstruction") {
                    putJsonArray("parts") {
                        add(buildJsonObject { put("text", system) })
                    }
                }
            }
            putJsonArray("contents") {
                request.turns.forEach { turn ->
                    add(
                        buildJsonObject {
                            put(
                                "role",
                                if (turn.role == AiRole.ASSISTANT) "model" else "user"
                            )
                            put("parts", turn.toGeminiParts())
                        }
                    )
                }
            }
            if (request.tools.isNotEmpty()) {
                putJsonArray("tools") {
                    add(
                        buildJsonObject {
                            putJsonArray("functionDeclarations") {
                                request.tools.forEach { tool ->
                                    add(
                                        buildJsonObject {
                                            put("name", tool.name)
                                            put("description", tool.description)
                                            put("parameters", tool.paramsSchema)
                                        }
                                    )
                                }
                            }
                        }
                    )
                }
            }
            putJsonObject("generationConfig") {
                put("temperature", request.temperature)
                request.maxTokens?.let { put("maxOutputTokens", it) }
            }
        }.toString()

        val url = request.endpoint.baseUrl.trimEnd('/') +
            "/models/" + request.endpoint.modelId + ":streamGenerateContent?alt=sse"
        return buildJsonPostRequest(
            url = url,
            body = body,
            headers = mapOf(
                "x-goog-api-key" to request.endpoint.apiKey,
                "Accept" to "text/event-stream"
            )
        )
    }

    override fun isTerminal(frame: SseFrame): Boolean = false

    override fun parseFrame(
        frame: SseFrame,
        state: MutableMap<String, Any?>
    ): List<ChatStreamEvent> {
        val root = runCatching { json.parseToJsonElement(frame.data).jsonObject }.getOrNull()
            ?: return emptyList()

        root["error"]?.let { err ->
            val message = (err as? JsonObject)?.get("message")?.jsonPrimitive?.content
                ?: frame.data
            return listOf(ChatStreamEvent.Failed(RuntimeException(message)))
        }

        val events = mutableListOf<ChatStreamEvent>()
        val candidate = (root["candidates"] as? kotlinx.serialization.json.JsonArray)
            ?.firstOrNull()?.jsonObject
        candidate?.get("content")?.jsonObject
            ?.get("parts")?.jsonArray
            ?.forEach { rawPart ->
                val part = rawPart.jsonObject
                val isThought = (part["thought"] as? kotlinx.serialization.json.JsonPrimitive)
                    ?.content == "true"
                part["text"]?.jsonPrimitive?.content?.let { text ->
                    if (text.isNotEmpty()) {
                        events.add(
                            if (isThought) ChatStreamEvent.ReasoningDelta(text)
                            else ChatStreamEvent.TextDelta(text)
                        )
                    }
                }
                part["functionCall"]?.jsonObject?.let { call ->
                    val name = call["name"]?.jsonPrimitive?.content ?: return@forEach
                    val counter = ((state[KEY_CALL_COUNTER] as? Int) ?: 0) + 1
                    state[KEY_CALL_COUNTER] = counter
                    events.add(
                        ChatStreamEvent.ToolCallReceived(
                            com.synth.synthmusic.domain.model.AiToolCall(
                                id = "call_$counter",
                                name = name,
                                argumentsJson = (call["args"] as? JsonObject)
                                    ?.toString() ?: "{}"
                            )
                        )
                    )
                }
            }
        candidate?.get("finishReason")?.jsonPrimitive?.content?.let { reason ->
            events.add(ChatStreamEvent.Completed(mapGeminiStopReason(reason)))
        }
        root["usageMetadata"]?.jsonObject?.let { usage ->
            val input = usage["promptTokenCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            val output = usage["candidatesTokenCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
            if (input > 0 || output > 0) events.add(ChatStreamEvent.Usage(input, output))
        }
        return events
    }

    private fun AiTurn.toGeminiParts(): kotlinx.serialization.json.JsonArray =
        buildJsonArray {
            parts.forEach { part ->
                when (part) {
                    is AiMessagePart.Text -> add(
                        buildJsonObject { put("text", part.text) }
                    )
                    is AiMessagePart.Image -> add(
                        buildJsonObject {
                            putJsonObject("inlineData") {
                                put("mimeType", part.mimeType)
                                put("data", part.base64)
                            }
                        }
                    )
                    is AiMessagePart.ToolCall -> add(
                        buildJsonObject {
                            putJsonObject("functionCall") {
                                put("name", part.name)
                                put("args", parseArgsOrEmpty(part.argumentsJson))
                            }
                        }
                    )
                    is AiMessagePart.ToolResult -> add(
                        buildJsonObject {
                            putJsonObject("functionResponse") {
                                put("name", part.name)
                                putJsonObject("response") {
                                    put("result", part.content)
                                }
                            }
                        }
                    )
                    // Reasoning is display-only and never replayed to the API.
                    is AiMessagePart.Reasoning -> Unit
                }
            }
        }

    private companion object {
        const val KEY_CALL_COUNTER = "call_counter"

        fun mapGeminiStopReason(raw: String): StopReason = when (raw) {
            "STOP" -> StopReason.END_TURN
            "MAX_TOKENS" -> StopReason.LENGTH
            "SAFETY", "PROHIBITED_CONTENT", "BLOCKLIST" -> StopReason.CONTENT_FILTER
            else -> StopReason.OTHER
        }
    }
}
