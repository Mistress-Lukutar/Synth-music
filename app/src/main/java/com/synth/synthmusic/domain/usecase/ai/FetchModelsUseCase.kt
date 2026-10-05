package com.synth.synthmusic.domain.usecase.ai

import com.synth.synthmusic.data.ai.client.ProviderHttpException
import com.synth.synthmusic.data.ai.client.buildJsonPostRequest
import com.synth.synthmusic.domain.model.AiModel
import com.synth.synthmusic.domain.model.AiProtocol
import com.synth.synthmusic.domain.model.AiProvider
import com.synth.synthmusic.domain.model.guessModelCapabilities
import com.synth.synthmusic.domain.repository.AiModelRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
        modelRepository.upsertModels(models)
        models
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
                        isPinned = false
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
                        isPinned = false
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
                        isPinned = false
                    )
                }
            }
        }
    }
}
