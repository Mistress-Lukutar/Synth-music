package com.synth.synthmusic.data.ai.client

import com.synth.synthmusic.domain.model.AiChatRequest
import com.synth.synthmusic.domain.model.ChatStreamEvent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

/**
 * Shared plumbing for OkHttp-backed streaming chat clients: request
 * execution, cancellation wiring and SSE frame → [ChatStreamEvent] mapping.
 *
 * Subclasses provide protocol-specific request building and event parsing.
 */
abstract class OkHttpChatClient(
    protected val httpClient: OkHttpClient,
    protected val json: Json
) : AiChatClient {

    /** Builds the protocol-specific HTTP request for [request]. */
    protected abstract fun buildRequest(request: AiChatRequest): Request

    /**
     * Parses one SSE frame into zero or more stream events.
     *
     * @param frame the raw frame from the response body.
     * @param state mutable per-stream scratch state for protocol parsing.
     */
    protected abstract fun parseFrame(
        frame: SseFrame,
        state: MutableMap<String, Any?>
    ): List<ChatStreamEvent>

    /** Returns true when this frame terminates the stream (`[DONE]` etc.). */
    protected abstract fun isTerminal(frame: SseFrame): Boolean

    final override fun streamChat(request: AiChatRequest): Flow<ChatStreamEvent> = flow {
        val call = httpClient.newCall(buildRequest(request))
        // Cancel the in-flight call when the collecting coroutine is cancelled
        // so the Stop button interrupts blocking reads immediately.
        val cancelHandle = currentCoroutineContext()[Job]
            ?.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = runCatching { response.body?.string() }.getOrNull()
                    emit(
                        ChatStreamEvent.Failed(
                            throwable = ProviderHttpException(response.code, errorBody),
                            httpCode = response.code
                        )
                    )
                    return@flow
                }
                val source = response.body?.source()
                if (source == null) {
                    emit(ChatStreamEvent.Failed(IllegalStateException("Empty response body")))
                    return@flow
                }
                val state = mutableMapOf<String, Any?>()
                var completed = false
                for (frame in source.inputStream().bufferedReader().lineSequence().asSseFrames()) {
                    if (isTerminal(frame)) break
                    for (event in parseFrame(frame, state)) {
                        if (event is ChatStreamEvent.Completed) completed = true
                        emit(event)
                    }
                }
                if (!completed) {
                    emit(ChatStreamEvent.Failed(IllegalStateException("Stream ended unexpectedly")))
                }
            }
        } finally {
            cancelHandle?.dispose()
        }
    }.flowOn(Dispatchers.IO)

    /** Executes a blocking request and returns the parsed JSON body. */
    protected fun executeJson(request: Request): kotlinx.serialization.json.JsonObject =
        httpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw ProviderHttpException(response.code, body)
            }
            json.parseToJsonElement(body).jsonObject
        }
}

/**
 * Error thrown when a provider answers with a non-2xx HTTP status.
 *
 * @property code HTTP status code.
 * @property body raw response body, truncated for safety by callers.
 */
class ProviderHttpException(
    val code: Int,
    val body: String?
) : RuntimeException("HTTP $code${body?.take(300)?.let { ": $it" } ?: ""}")
