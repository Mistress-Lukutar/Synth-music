package com.synth.synthmusic.data.ai.client

import com.synth.synthmusic.domain.model.AiChatRequest
import com.synth.synthmusic.domain.model.ChatStreamEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Chat client for OpenAI and OpenAI-compatible endpoints (OpenRouter, Groq,
 * LM Studio, Ollama via custom base URL, ...).
 */
class OpenAiClient(
    httpClient: OkHttpClient,
    json: Json
) : OkHttpChatClient(httpClient, json) {

    override fun buildRequest(request: AiChatRequest): Request {
        val body = buildJsonObject {
            put("model", request.endpoint.modelId)
            put("stream", true)
            put("temperature", request.temperature)
            // Omitted when unset: the provider then applies its own maximum,
            // and newer OpenAI reasoning models reject max_tokens anyway.
            request.maxTokens?.let { put("max_tokens", it) }
            putJsonArray("messages") {
                request.systemPrompt?.let { system ->
                    add(
                        buildJsonObject {
                            put("role", "system")
                            put("content", system)
                        }
                    )
                }
                request.turns.forEach { turn ->
                    turn.toOpenAiMessages().forEach { add(it) }
                }
            }
            if (request.tools.isNotEmpty()) {
                put("tools", toolsToOpenAiJson(request.tools))
            }
        }.toString()

        return buildJsonPostRequest(
            url = request.endpoint.baseUrl.trimEnd('/') + "/chat/completions",
            body = body,
            headers = mapOf(
                "Authorization" to "Bearer ${request.endpoint.apiKey}",
                "Accept" to "text/event-stream"
            )
        )
    }

    override fun isTerminal(frame: SseFrame): Boolean = frame.data.trim() == "[DONE]"

    override fun parseFrame(
        frame: SseFrame,
        state: MutableMap<String, Any?>
    ): List<ChatStreamEvent> =
        parseOpenAiChunk(frame.data, json)
}
