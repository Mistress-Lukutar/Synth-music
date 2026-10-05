package com.synth.synthmusic.domain.usecase.ai

import com.synth.synthmusic.data.ai.client.AiChatClient
import com.synth.synthmusic.domain.model.AiChatRequest
import com.synth.synthmusic.domain.model.AiEndpoint
import com.synth.synthmusic.domain.model.AiProtocol
import com.synth.synthmusic.domain.model.AiRole
import com.synth.synthmusic.domain.model.AiTurn
import com.synth.synthmusic.domain.model.ChatStreamEvent
import kotlinx.coroutines.flow.toList

/**
 * Sends a minimal 1-token chat request to verify provider credentials and
 * reachability from the AI settings screen.
 *
 * @return null when the connection works, or a user-readable error message.
 */
class TestConnectionUseCase {

    /**
     * Pings [endpoint] through the [client] matching its protocol.
     */
    suspend operator fun invoke(
        endpoint: AiEndpoint,
        protocol: AiProtocol,
        client: AiChatClient
    ): String? {
        return try {
            val events = client.streamChat(
                AiChatRequest(
                    endpoint = endpoint,
                    systemPrompt = null,
                    turns = listOf(
                        AiTurn(AiRole.USER, listOf(com.synth.synthmusic.domain.model.AiMessagePart.Text("ping")))
                    ),
                    tools = emptyList(),
                    maxTokens = 1
                )
            ).toList()
            val failure = events.filterIsInstance<ChatStreamEvent.Failed>().firstOrNull()
            failure?.throwable?.message ?: failure?.let { "Connection failed" }
                ?: if (events.any { it is ChatStreamEvent.Completed }) {
                    null
                } else {
                    "Provider closed the stream without a response"
                }
        } catch (e: Exception) {
            e.message ?: "Connection failed"
        }
    }
}
