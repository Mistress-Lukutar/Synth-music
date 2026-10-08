package com.synth.synthmusic.domain.usecase.ai

import com.synth.synthmusic.data.ai.client.ProviderHttpException
import com.synth.synthmusic.data.ai.client.buildJsonPostRequest
import com.synth.synthmusic.domain.model.AiModel
import com.synth.synthmusic.domain.model.AiProtocol
import com.synth.synthmusic.domain.model.AiProvider
import com.synth.synthmusic.domain.model.ModelLimitsSource
import com.synth.synthmusic.domain.model.guessModelCapabilities
import com.synth.synthmusic.domain.repository.AiModelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Fetches the model list from a provider's model-list endpoint and upserts
 * the results into the local model cache.
 *
 * Per protocol:
 * - OpenAI-compatible: `GET {base}/models` → `data[].id`
 * - Anthropic: `GET {base}/models` → `data[].id` + `display_name`
 * - Gemini: `GET {base}/models` → filter `supportedGenerationMethods` for
 *   `generateContent`
 *
 * @throws ModelFetchException with a user-readable message on failure.
 */
class FetchModelsUseCase {

    /** Error surfaced when the model list cannot be fetched or parsed. */
    class ModelFetchException(message: String, cause: Throwable? = null) :
        Exception(message, cause)

    /**
     * Fetches models for [provider] using its decrypted [apiKey] and persists
     * them via [modelRepository].
     *
     * @return the upserted model list.
     */
    suspend operator fun invoke(
        provider: AiProvider,
        apiKey: String,
        httpClient: OkHttpClient,
        json: Json,
        modelRepository: AiModelRepository
    ): List<AiModel> = withContext(Dispatchers.IO) {
        val url = when (provider.protocol) {
            AiProtocol.OPENAI_COMPATIBLE, AiProtocol.ANTHROPIC ->
                provider.baseUrl.trimEnd('/') + "/models"
            AiProtocol.GOOGLE_GEMINI ->
                provider.baseUrl.trimEnd('/') + "/models"
        }
        val builder = Request.Builder().url(url)
        when (provider.protocol) {
            AiProtocol.OPENAI_COMPATIBLE ->
                builder.header("Authorization", "Bearer $apiKey")
            AiProtocol.ANTHROPIC -> {
                builder.header("x-api-key", apiKey)
                builder.header("anthropic-version", "2023-06-01")
            }
            AiProtocol.GOOGLE_GEMINI ->
                builder.header("x-goog-api-key", apiKey)
        }
        val request = builder.build()

        val body = try {
            httpClient.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw ModelFetchException(
                        "HTTP ${response.code}: ${text.take(200)}",
                        ProviderHttpException(response.code, text)
                    )
                }
                text
            }
        } catch (e: ModelFetchException) {
            throw e
        } catch (e: Exception) {
            throw ModelFetchException(
                e.message ?: "Network error while fetching models", e
            )
        }

        val models = try {
            parseModels(body, provider.protocol, json)
        } catch (e: Exception) {
            throw ModelFetchException("Unexpected response format", e)
        }
        // parseModels leaves providerId unset (0) for testability; scope the
        // results to the actual provider before persisting.
        val scoped = models.map { it.copy(providerId = provider.id) }
        modelRepository.upsertModels(scoped)
        scoped
    }

    /**
     * Pure parsing step, visible for unit tests.
     */
    fun parseModels(
        body: String,
        protocol: AiProtocol,
        json: Json
    ): List<AiModel> {
        val root = json.parseToJsonElement(body).jsonObject
        val data = root["models"]?.jsonArray
            ?: root["data"]?.jsonArray
            ?: emptyList<kotlinx.serialization.json.JsonElement>()
        return data.mapNotNull { element ->
            val obj = element.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content ?: obj["name"]?.jsonPrimitive?.content
                ?: return@mapNotNull null
            val cleanId = id.removePrefix("models/")
            val (contextTokens, maxOutputTokens, limitsSource) = parseModelLimits(obj, protocol)
            when (protocol) {
                AiProtocol.ANTHROPIC -> {
                    val displayName = obj["display_name"]?.jsonPrimitive?.content ?: cleanId
                    val (tools, vision) = guessModelCapabilities(cleanId)
                    AiModel(
                        providerId = 0,
                        modelId = cleanId,
                        displayName = displayName,
                        supportsTools = tools,
                        supportsVision = vision,
                        isPinned = false,
                        contextTokens = contextTokens,
                        maxOutputTokens = maxOutputTokens,
                        limitsSource = limitsSource
                    )
                }
                AiProtocol.GOOGLE_GEMINI -> {
                    val methods = obj["supportedGenerationMethods"]?.jsonArray
                        ?.map { it.jsonPrimitive.content } ?: emptyList()
                    if (methods.isNotEmpty() && "generateContent" !in methods) {
                        return@mapNotNull null
                    }
                    val displayName = obj["displayName"]?.jsonPrimitive?.content ?: cleanId
                    val (tools, vision) = guessModelCapabilities(cleanId)
                    AiModel(
                        providerId = 0,
                        modelId = cleanId,
                        displayName = displayName,
                        supportsTools = tools,
                        supportsVision = vision,
                        isPinned = false,
                        contextTokens = contextTokens,
                        maxOutputTokens = maxOutputTokens,
                        limitsSource = limitsSource
                    )
                }
                AiProtocol.OPENAI_COMPATIBLE -> {
                    val (tools, vision) = guessModelCapabilities(cleanId)
                    AiModel(
                        providerId = 0,
                        modelId = cleanId,
                        displayName = cleanId,
                        supportsTools = tools,
                        supportsVision = vision,
                        isPinned = false,
                        contextTokens = contextTokens,
                        maxOutputTokens = maxOutputTokens,
                        limitsSource = limitsSource
                    )
                }
            }
        }
    }

    /**
     * Extracts capability limits from one model entry, per protocol.
     * Returns (contextTokens, maxOutputTokens, source); the source is
     * [ModelLimitsSource.PROVIDER] when at least one limit was found.
     *
     * - Gemini: `inputTokenLimit` / `outputTokenLimit`.
     * - OpenAI-compatible: OpenRouter-style `context_length` /
     *   `top_provider.context_length` / `max_model_len` for the context
     *   window and `max_completion_tokens` / `top_provider.max_completion_tokens`
     *   for the output cap. Plain OpenAI responses carry none of these.
     * - Anthropic: the model-list endpoint reports no limits.
     */
    internal fun parseModelLimits(
        obj: JsonObject,
        protocol: AiProtocol
    ): Triple<Int?, Int?, ModelLimitsSource?> {
        fun JsonObject.objAt(key: String): JsonObject? =
            get(key)?.let { element ->
                runCatching { element.jsonObject }.getOrNull()
            }
        return when (protocol) {
            AiProtocol.ANTHROPIC -> Triple(null, null, null)
            AiProtocol.GOOGLE_GEMINI -> {
                val context = obj["inputTokenLimit"]?.jsonPrimitive?.intOrNull
                val output = obj["outputTokenLimit"]?.jsonPrimitive?.intOrNull
                Triple(
                    context,
                    output,
                    if (context != null || output != null) ModelLimitsSource.PROVIDER else null
                )
            }
            AiProtocol.OPENAI_COMPATIBLE -> {
                val context = obj["context_length"]?.jsonPrimitive?.intOrNull
                    ?: obj.objAt("top_provider")
                        ?.get("context_length")?.jsonPrimitive?.intOrNull
                    ?: obj["max_model_len"]?.jsonPrimitive?.intOrNull
                val output = obj["max_completion_tokens"]?.jsonPrimitive?.intOrNull
                    ?: obj.objAt("top_provider")
                        ?.get("max_completion_tokens")?.jsonPrimitive?.intOrNull
                Triple(
                    context,
                    output,
                    if (context != null || output != null) ModelLimitsSource.PROVIDER else null
                )
            }
        }
    }
}
