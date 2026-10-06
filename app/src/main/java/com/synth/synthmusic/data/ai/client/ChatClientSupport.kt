package com.synth.synthmusic.data.ai.client

import com.synth.synthmusic.domain.model.AiMessagePart
import com.synth.synthmusic.domain.model.AiRole
import com.synth.synthmusic.domain.model.AiToolCall
import com.synth.synthmusic.domain.model.AiToolSpec
import com.synth.synthmusic.domain.model.AiTurn
import com.synth.synthmusic.domain.model.ChatStreamEvent
import com.synth.synthmusic.domain.model.StopReason
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * JSON media type used for all AI provider request bodies.
 */
val AI_JSON_MEDIA_TYPE = "application/json".toMediaType()

/**
 * Builds a POST [Request] with a JSON body and the given headers.
 */
fun buildJsonPostRequest(url: String, body: String, headers: Map<String, String>): Request =
    Request.Builder()
        .url(url)
        .post(body.toRequestBody(AI_JSON_MEDIA_TYPE))
        .apply { headers.forEach { (name, value) -> header(name, value) } }
        .build()

/**
 * Parses a JSON string into a [JsonObject], falling back to an empty object
 * for malformed or empty payloads.
 */
fun parseArgsOrEmpty(argumentsJson: String): JsonObject = runCatching {
    Json.parseToJsonElement(argumentsJson).jsonObject
}.getOrDefault(buildJsonObject { })

/**
 * Extracts a human-readable `error.message` field from a provider JSON error
 * payload, or null when the body is not parseable.
 */
fun extractErrorMessage(body: String, json: Json): String? = runCatching {
    val obj = json.parseToJsonElement(body).jsonObject
    obj["error"]?.let { err ->
        (err as? JsonObject)?.get("message")?.jsonPrimitive?.content
            ?: err.jsonPrimitive.content
    }
}.getOrNull()

/**
 * Converts tool specs to the OpenAI `tools` array format. Many
 * OpenAI-compatible third parties share this shape.
 */
fun toolsToOpenAiJson(tools: List<AiToolSpec>): kotlinx.serialization.json.JsonArray =
    buildJsonArray {
        tools.forEach { tool ->
            add(
                buildJsonObject {
                    put("type", "function")
                    putJsonObject("function") {
                        put("name", tool.name)
                        put("description", tool.description)
                        put("parameters", tool.paramsSchema)
                    }
                }
            )
        }
    }

/**
 * Expands a turn into OpenAI wire messages. Tool results each become their
 * own `role:"tool"` message, as required by the protocol.
 */
fun AiTurn.toOpenAiMessages(): List<JsonObject> = when (role) {
    AiRole.SYSTEM -> listOf(
        buildJsonObject {
            put("role", "system")
            put("content", textOrEmpty())
        }
    )

    AiRole.USER -> {
        if (images.isEmpty()) {
            listOf(
                buildJsonObject {
                    put("role", "user")
                    put("content", textOrEmpty())
                }
            )
        } else {
            listOf(
                buildJsonObject {
                    put("role", "user")
                    putJsonArray("content") {
                        add(
                            buildJsonObject {
                                put("type", "text")
                                put("text", textOrEmpty())
                            }
                        )
                        images.forEach { image ->
                            add(
                                buildJsonObject {
                                    put("type", "image_url")
                                    putJsonObject("image_url") {
                                        put("url", "data:${image.mimeType};base64,${image.base64}")
                                    }
                                }
                            )
                        }
                    }
                }
            )
        }
    }

    AiRole.ASSISTANT -> {
        if (toolCalls.isEmpty()) {
            listOf(
                buildJsonObject {
                    put("role", "assistant")
                    put("content", textOrEmpty())
                }
            )
        } else {
            listOf(
                buildJsonObject {
                    put("role", "assistant")
                    put("content", textOrEmpty().ifEmpty { null })
                    putJsonArray("tool_calls") {
                        toolCalls.forEach { call ->
                            add(
                                buildJsonObject {
                                    put("id", call.toolCallId)
                                    put("type", "function")
                                    putJsonObject("function") {
                                        put("name", call.name)
                                        put("arguments", call.argumentsJson)
                                    }
                                }
                            )
                        }
                    }
                }
            )
        }
    }

    AiRole.TOOL -> toolResults.map { result ->
        buildJsonObject {
            put("role", "tool")
            put("tool_call_id", result.toolCallId)
            put("content", result.content)
        }
    }
}

/**
 * Concatenates the text of all [AiMessagePart.Text] parts in [turn].
 */
fun AiTurn.textOrEmpty(): String =
    parts.filterIsInstance<AiMessagePart.Text>().joinToString("") { it.text }

/**
 * Parses one OpenAI SSE chunk payload into stream events.
 */
fun parseOpenAiChunk(data: String, json: Json): List<ChatStreamEvent> {
    val root = runCatching { json.parseToJsonElement(data).jsonObject }.getOrNull()
        ?: return listOf(
            ChatStreamEvent.Failed(IllegalStateException("Malformed SSE payload"))
        )

    root["error"]?.let { err ->
        val message = (err as? JsonObject)?.get("message")?.jsonPrimitive?.content ?: data
        return listOf(ChatStreamEvent.Failed(RuntimeException(message)))
    }

    val events = mutableListOf<ChatStreamEvent>()
    val choice = (root["choices"] as? kotlinx.serialization.json.JsonArray)
        ?.firstOrNull()?.jsonObject
    if (choice != null) {
        val delta = choice["delta"]?.jsonObject
        // Reasoning models expose thinking text either as `reasoning_content`
        // (DeepSeek-style) or `reasoning` (OpenRouter-style).
        val reasoning = delta?.get("reasoning_content")?.jsonPrimitive?.contentOrNull
            ?: delta?.get("reasoning")?.jsonPrimitive?.contentOrNull
        if (!reasoning.isNullOrEmpty()) {
            events.add(ChatStreamEvent.ReasoningDelta(reasoning))
        }
        delta?.get("content")?.jsonPrimitive?.content?.let { text ->
            if (text.isNotEmpty()) events.add(ChatStreamEvent.TextDelta(text))
        }
        delta?.get("tool_calls")?.jsonArray?.forEach { rawCall ->
            val callObj = rawCall.jsonObject
            val function = callObj["function"]?.jsonObject
            val id = callObj["id"]?.jsonPrimitive?.content
            val name = function?.get("name")?.jsonPrimitive?.content
            val args = function?.get("arguments")?.jsonPrimitive?.content
            if (id != null && name != null) {
                events.add(
                    ChatStreamEvent.ToolCallReceived(
                        AiToolCall(id, name, args ?: "{}")
                    )
                )
            }
        }
        choice["finish_reason"]?.jsonPrimitive?.content?.let { finish ->
            if (finish != "null") {
                events.add(ChatStreamEvent.Completed(mapOpenAiStopReason(finish)))
            }
        }
    }
    root["usage"]?.jsonObject?.let { usage ->
        val input = usage["prompt_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        val output = usage["completion_tokens"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0
        if (input > 0 || output > 0) events.add(ChatStreamEvent.Usage(input, output))
    }
    return events
}

private fun mapOpenAiStopReason(raw: String): StopReason = when (raw) {
    "stop" -> StopReason.END_TURN
    "tool_calls", "function_call" -> StopReason.TOOL_USE
    "length" -> StopReason.LENGTH
    "content_filter" -> StopReason.CONTENT_FILTER
    else -> StopReason.OTHER
}
