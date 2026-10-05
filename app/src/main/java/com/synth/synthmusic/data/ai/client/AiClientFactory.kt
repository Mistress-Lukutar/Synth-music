package com.synth.synthmusic.data.ai.client

import com.synth.synthmusic.domain.model.AiProtocol

/**
 * Resolves the [AiChatClient] implementation for a wire protocol.
 */
class AiClientFactory(
    private val openAiClient: OpenAiClient,
    private val anthropicClient: AnthropicClient,
    private val geminiClient: GeminiClient
) {

    /**
     * Returns the client implementing [protocol].
     */
    fun clientFor(protocol: AiProtocol): AiChatClient = when (protocol) {
        AiProtocol.OPENAI_COMPATIBLE -> openAiClient
        AiProtocol.ANTHROPIC -> anthropicClient
        AiProtocol.GOOGLE_GEMINI -> geminiClient
    }
}
