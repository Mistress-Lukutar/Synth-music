package com.synth.synthmusic.data.repository

import com.synth.synthmusic.data.local.database.AiAssistantDao
import com.synth.synthmusic.data.local.database.toDomain
import com.synth.synthmusic.data.local.database.toEntity
import com.synth.synthmusic.domain.model.AiAssistant
import com.synth.synthmusic.domain.repository.AiAssistantRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Room-backed [AiAssistantRepository].
 */
class AiAssistantRepositoryImpl(
    private val assistantDao: AiAssistantDao
) : AiAssistantRepository {

    override fun observeAssistants(): Flow<List<AiAssistant>> =
        assistantDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getAssistants(): List<AiAssistant> =
        assistantDao.getAll().map { it.toDomain() }

    override suspend fun getAssistant(assistantId: Long): AiAssistant? =
        assistantDao.getById(assistantId)?.toDomain()

    override suspend fun upsertAssistant(assistant: AiAssistant): Long =
        assistantDao.insert(assistant.toEntity())

    override suspend fun deleteAssistant(assistantId: Long) {
        assistantDao.deleteById(assistantId)
    }
}
