package com.synth.synthmusic.data.repository

import com.synth.synthmusic.data.local.database.AiActionLogDao
import com.synth.synthmusic.data.local.database.AiChatDao
import com.synth.synthmusic.data.local.database.AiChatEntity
import com.synth.synthmusic.data.local.database.AiChatMessageDao
import com.synth.synthmusic.data.local.database.AiChatMessageEntity
import com.synth.synthmusic.data.local.database.AiChatToolGrantDao
import com.synth.synthmusic.data.local.database.AiChatToolGrantEntity
import com.synth.synthmusic.domain.model.AiCapability
import com.synth.synthmusic.domain.model.AiChat
import com.synth.synthmusic.domain.model.AiChatMessage
import com.synth.synthmusic.domain.model.AiMessagePart
import com.synth.synthmusic.domain.model.AiMessageStatus
import com.synth.synthmusic.domain.model.AiRole
import com.synth.synthmusic.domain.repository.AiChatRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * Room-backed [AiChatRepository]. Message parts are serialized to JSON for
 * storage so ordering of text, tool activity and image parts is preserved.
 */
class AiChatRepositoryImpl(
    private val chatDao: AiChatDao,
    private val messageDao: AiChatMessageDao,
    private val grantDao: AiChatToolGrantDao,
    private val actionLogDao: AiActionLogDao,
    private val json: Json
) : AiChatRepository {

    private val partsSerializer = ListSerializer(AiMessagePart.serializer())

    override fun observeChats(): Flow<List<AiChat>> =
        chatDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeChat(chatId: Long): Flow<AiChat?> =
        chatDao.observeById(chatId).map { it?.toDomain() }

    override suspend fun getChat(chatId: Long): AiChat? =
        chatDao.getById(chatId)?.toDomain()

    override suspend fun createChat(
        title: String,
        assistantId: Long?,
        providerId: Long?,
        modelId: String?
    ): Long {
        val now = System.currentTimeMillis()
        return chatDao.insert(
            AiChatEntity(
                title = title,
                assistantId = assistantId,
                providerId = providerId,
                modelId = modelId,
                createdAt = now,
                updatedAt = now,
                isArchived = false
            )
        )
    }

    override suspend fun updateTitle(chatId: Long, title: String) {
        chatDao.updateTitle(chatId, title)
    }

    override suspend fun touch(chatId: Long) {
        chatDao.touch(chatId, System.currentTimeMillis())
    }

    override suspend fun deleteChat(chatId: Long) {
        messageDao.deleteForChat(chatId)
        grantDao.deleteForChat(chatId)
        actionLogDao.deleteForChat(chatId)
        chatDao.deleteById(chatId)
    }

    override suspend fun clearMessages(chatId: Long) {
        messageDao.deleteForChat(chatId)
    }

    override suspend fun deleteFrom(chatId: Long, messageId: Long) {
        messageDao.deleteFrom(chatId, messageId)
    }

    override fun observeMessages(chatId: Long): Flow<List<AiChatMessage>> =
        messageDao.observeForChat(chatId).map { list -> list.map { it.toDomain() } }

    override suspend fun getMessages(chatId: Long): List<AiChatMessage> =
        messageDao.getForChat(chatId).map { it.toDomain() }

    override suspend fun getMessage(messageId: Long): AiChatMessage? =
        messageDao.getById(messageId)?.toDomain()

    override suspend fun appendMessage(message: AiChatMessage): Long =
        messageDao.insert(message.toEntity())

    override suspend fun updateMessage(message: AiChatMessage) {
        messageDao.getById(message.id)?.let { messageDao.update(message.toEntity()) }
    }

    override suspend fun updateMessageStatus(messageId: Long, status: AiMessageStatus) {
        messageDao.updateStatus(messageId, status.name)
    }

    override suspend fun getGrants(chatId: Long): Map<AiCapability, Boolean> =
        grantDao.getForChat(chatId).mapNotNull { entity ->
            val capability = runCatching { AiCapability.valueOf(entity.capability) }
                .getOrNull() ?: return@mapNotNull null
            capability to entity.granted
        }.toMap()

    override suspend fun setGrant(chatId: Long, capability: AiCapability, granted: Boolean) {
        grantDao.upsert(
            AiChatToolGrantEntity(
                chatId = chatId,
                capability = capability.name,
                granted = granted,
                grantedAt = System.currentTimeMillis()
            )
        )
    }

    private fun AiChatEntity.toDomain(): AiChat = AiChat(
        id = id,
        title = title,
        assistantId = assistantId,
        providerId = providerId,
        modelId = modelId,
        createdAt = createdAt,
        updatedAt = updatedAt,
        isArchived = isArchived
    )

    private fun AiChatMessage.toEntity(): AiChatMessageEntity = AiChatMessageEntity(
        id = id,
        chatId = chatId,
        role = role.name,
        contentJson = json.encodeToString(partsSerializer, parts),
        status = status.name,
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        createdAt = createdAt
    )

    private fun AiChatMessageEntity.toDomain(): AiChatMessage = AiChatMessage(
        id = id,
        chatId = chatId,
        role = runCatching { AiRole.valueOf(role) }.getOrDefault(AiRole.ASSISTANT),
        parts = parseParts(contentJson),
        status = runCatching { AiMessageStatus.valueOf(status) }
            .getOrDefault(AiMessageStatus.COMPLETE),
        inputTokens = inputTokens,
        outputTokens = outputTokens,
        createdAt = createdAt
    )

    private fun parseParts(contentJson: String): List<AiMessagePart> = try {
        json.decodeFromString(partsSerializer, contentJson)
    } catch (e: SerializationException) {
        // Legacy/malformed payloads degrade to plain text instead of crashing.
        listOf(AiMessagePart.Text(contentJson))
    } catch (e: IllegalArgumentException) {
        listOf(AiMessagePart.Text(contentJson))
    }
}
