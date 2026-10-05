package com.synth.synthmusic.data.ai.client

import com.synth.synthmusic.domain.model.AiChatRequest
import com.synth.synthmusic.domain.model.ChatStreamEvent
import kotlinx.coroutines.flow.Flow

/**
 * Protocol-agnostic streaming chat client. One implementation per wire
 * protocol; implementations must perform all I/O off the main dispatcher and
 * map protocol-specific payloads onto [ChatStreamEvent].
 */
interface AiChatClient {

    /**
     * Streams a chat completion for [request].
     *
     * The returned flow emits [ChatStreamEvent.TextDelta] incrementally,
     * zero or more [ChatStreamEvent.ToolCallReceived], optional
     * [ChatStreamEvent.Usage], and always terminates with either
     * [ChatStreamEvent.Completed] or [ChatStreamEvent.Failed].
     */
    fun streamChat(request: AiChatRequest): Flow<ChatStreamEvent>
}
