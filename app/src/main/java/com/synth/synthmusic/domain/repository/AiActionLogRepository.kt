package com.synth.synthmusic.domain.repository

import com.synth.synthmusic.domain.model.AiActionLogEntry
import kotlinx.coroutines.flow.Flow

/**
 * Domain repository for the AI action audit log — one entry per tool call,
 * including denied ones. This is the trust backbone of the tool system.
 */
interface AiActionLogRepository {

    /** Observes the global log, newest first. */
    fun observeAll(): Flow<List<AiActionLogEntry>>

    /** Observes the log of one chat, newest first. */
    fun observeForChat(chatId: Long): Flow<List<AiActionLogEntry>>

    /** Appends an audit entry. */
    suspend fun log(
        chatId: Long?,
        toolName: String,
        argsJson: String,
        outcome: com.synth.synthmusic.domain.model.AiActionOutcome,
        summary: String
    )
}
