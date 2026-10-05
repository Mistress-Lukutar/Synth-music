package com.synth.synthmusic.domain.repository

import com.synth.synthmusic.domain.model.AiAssistant
import kotlinx.coroutines.flow.Flow

/**
 * Domain repository for AI assistant personas.
 */
interface AiAssistantRepository {

    /** Observes all assistants in display order. */
    fun observeAssistants(): Flow<List<AiAssistant>>

    /** Returns all assistants. */
    suspend fun getAssistants(): List<AiAssistant>

    /** Returns an assistant by id, or null. */
    suspend fun getAssistant(assistantId: Long): AiAssistant?

    /** Inserts or updates an assistant and returns its id. */
    suspend fun upsertAssistant(assistant: AiAssistant): Long

    /** Deletes a non-builtin assistant. */
    suspend fun deleteAssistant(assistantId: Long)
}
