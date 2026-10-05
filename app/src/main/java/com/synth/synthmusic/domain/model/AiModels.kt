package com.synth.synthmusic.domain.model

import kotlinx.serialization.Serializable

/**
 * Wire protocol spoken by an AI provider.
 *
 * @property defaultBaseUrl base URL pre-filled in the provider editor.
 */
enum class AiProtocol(val defaultBaseUrl: String) {
    /** OpenAI and OpenAI-compatible endpoints (OpenRouter, Groq, LM Studio, Ollama, ...). */
    OPENAI_COMPATIBLE("https://api.openai.com/v1"),

    /** Anthropic Messages API. */
    ANTHROPIC("https://api.anthropic.com/v1"),

    /** Google Gemini API. */
    GOOGLE_GEMINI("https://generativelanguage.googleapis.com/v1beta")
}

/**
 * Domain model of a user-configured AI provider.
 *
 * @property apiKeyEncrypted encrypted payload produced by ApiKeyCipher; the
 * plaintext key is resolved only when building requests.
 */
data class AiProvider(
    val id: Long,
    val label: String,
    val protocol: AiProtocol,
    val baseUrl: String,
    val apiKeyEncrypted: String,
    val createdAt: Long
)

/**
 * Domain model of a model offered by a provider.
 */
data class AiModel(
    val providerId: Long,
    val modelId: String,
    val displayName: String,
    val supportsTools: Boolean,
    val supportsVision: Boolean,
    val isPinned: Boolean
)

/**
 * Capabilities an assistant/chat may be granted. Tool advertisement and
 * execution are filtered through these; the effective grants are the
 * assistant's default grants (all capabilities when no assistant) intersected
 * with the global kill-switches in AI settings.
 */
enum class AiCapability {
    READ_LIBRARY,
    WRITE_METADATA,
    MANAGE_FILES,
    INTERNET
}

/**
 * Generic web-search backends usable by the optional `web_search` tool.
 */
enum class WebSearchProvider {
    NONE,
    BRAVE,
    TAVILY
}

/**
 * Global AI settings persisted in DataStore.
 */
data class AiSettings(
    val activeProviderId: Long? = null,
    val activeModelId: String? = null,
    val capabilityGrants: Map<AiCapability, Boolean> = mapOf(
        AiCapability.READ_LIBRARY to true,
        AiCapability.WRITE_METADATA to true,
        AiCapability.MANAGE_FILES to false,
        AiCapability.INTERNET to false
    ),
    /**
     * When true, edit-capable tools show an approval card in the chat before
     * executing. When false they run silently; [ConfirmationLevel]
     * ALWAYS_CONFIRM tools (file deletion) still ask regardless.
     */
    val confirmEdits: Boolean = true,
    val webSearchProvider: WebSearchProvider = WebSearchProvider.NONE,
    val webSearchKeyEncrypted: String? = null
) {
    /**
     * Returns whether a capability is globally enabled (defaults apply when
     * the map has no explicit entry).
     */
    fun isCapabilityEnabled(capability: AiCapability): Boolean =
        capabilityGrants[capability] ?: DEFAULT_CAPABILITY_GRANTS.getValue(capability)

    companion object {
        /** Default kill-switch state per capability when unset. */
        val DEFAULT_CAPABILITY_GRANTS: Map<AiCapability, Boolean> = mapOf(
            AiCapability.READ_LIBRARY to true,
            AiCapability.WRITE_METADATA to true,
            AiCapability.MANAGE_FILES to false,
            AiCapability.INTERNET to false
        )
    }
}

/**
 * Heuristically guesses tool/vision support from a model identifier.
 *
 * @param modelId raw model id as reported by the provider.
 */
fun guessModelCapabilities(modelId: String): Pair<Boolean, Boolean> {
    val id = modelId.lowercase()
    val vision = listOf(
        "gpt-4o", "gpt-4.1", "gpt-4-turbo", "o3", "o4", "claude-3", "claude-4",
        "gemini", "vision", "vl", "llama-4", "pixtral", "kimi"
    ).any { id.contains(it) }
    val tools = vision || listOf(
        "gpt", "claude", "gemini", "deepseek", "qwen", "mistral", "grok",
        "command-r", "llama-3", "llama-4", "phi", "glm", "kimi", "k3"
    ).any { id.contains(it) }
    return tools to vision
}

/**
 * Lightweight reference to the model a chat should use.
 */
@Serializable
data class ActiveModelRef(
    val providerId: Long,
    val modelId: String
)
