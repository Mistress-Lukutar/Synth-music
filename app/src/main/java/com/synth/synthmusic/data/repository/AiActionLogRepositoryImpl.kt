package com.synth.synthmusic.data.repository

import com.synth.synthmusic.data.local.database.AiActionLogDao
import com.synth.synthmusic.data.local.database.AiActionLogEntity
import com.synth.synthmusic.data.local.database.toDomain
import com.synth.synthmusic.domain.model.AiActionLogEntry
import com.synth.synthmusic.domain.model.AiActionOutcome
import com.synth.synthmusic.domain.repository.AiActionLogRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed [AiActionLogRepository].
 */
class AiActionLogRepositoryImpl(
    private val actionLogDao: AiActionLogDao
) : AiActionLogRepository {

    override fun observeAll(): Flow<List<AiActionLogEntry>> =
        actionLogDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeForChat(chatId: Long): Flow<List<AiActionLogEntry>> =
        actionLogDao.observeForChat(chatId).map { list -> list.map { it.toDomain() } }

    override suspend fun log(
        chatId: Long?,
        toolName: String,
        argsJson: String,
        outcome: AiActionOutcome,
        summary: String
    ) {
        actionLogDao.insert(
            AiActionLogEntity(
                chatId = chatId,
                timestamp = System.currentTimeMillis(),
                toolName = toolName,
                argsJson = argsJson,
                outcome = outcome.name,
                summary = summary
            )
        )
    }
}
