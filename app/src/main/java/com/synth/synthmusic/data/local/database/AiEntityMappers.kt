package com.synth.synthmusic.data.local.database

import com.synth.synthmusic.domain.model.AiActionLogEntry
import com.synth.synthmusic.domain.model.AiActionOutcome
import com.synth.synthmusic.domain.model.AiAssistant
import com.synth.synthmusic.domain.model.AiCapability
import com.synth.synthmusic.domain.model.AiModel
import com.synth.synthmusic.domain.model.AiProtocol
import com.synth.synthmusic.domain.model.AiProvider
import com.synth.synthmusic.domain.model.WebSearchProvider
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

// --- Entity → Domain ---

/**
 * Maps [AiProviderEntity] to its domain model.
 */
fun AiProviderEntity.toDomain(): AiProvider = AiProvider(
    id = id,
    label = label,
    protocol = runCatching { AiProtocol.valueOf(protocol) }.getOrDefault(
        AiProtocol.OPENAI_COMPATIBLE
    ),
    baseUrl = baseUrl,
    apiKeyEncrypted = apiKeyEncrypted,
    createdAt = createdAt
)

/**
 * Maps [AiModelEntity] to its domain model.
 */
fun AiModelEntity.toDomain(): AiModel = AiModel(
    providerId = providerId,
    modelId = modelId,
    displayName = displayName,
    supportsTools = supportsTools,
    supportsVision = supportsVision,
    isPinned = isPinned
)

/**
 * Maps [AiAssistantEntity] to its domain model.
 */
fun AiAssistantEntity.toDomain(): AiAssistant = AiAssistant(
    id = id,
    builtinKey = builtinKey,
    name = name,
    description = description,
    systemPrompt = systemPrompt,
    avatarIcon = avatarIcon,
    avatarColorIndex = avatarColorIndex,
    defaultGrants = parseGrants(defaultGrantsJson),
    isBuiltin = isBuiltin,
    sortOrder = sortOrder
)

/**
 * Maps [AiActionLogEntity] to its domain model.
 */
fun AiActionLogEntity.toDomain(): AiActionLogEntry = AiActionLogEntry(
    id = id,
    chatId = chatId,
    timestamp = timestamp,
    toolName = toolName,
    argsJson = argsJson,
    outcome = runCatching { AiActionOutcome.valueOf(outcome) }
        .getOrDefault(AiActionOutcome.FAILED),
    summary = summary
)

// --- Domain → Entity ---

/**
 * Maps an [AiProvider] domain model to its entity row.
 */
fun AiProvider.toEntity() = AiProviderEntity(
    id = id,
    label = label,
    protocol = protocol.name,
    baseUrl = baseUrl,
    apiKeyEncrypted = apiKeyEncrypted,
    createdAt = createdAt
)

/**
 * Maps an [AiModel] domain model to its entity row.
 */
fun AiModel.toEntity() = AiModelEntity(
    providerId = providerId,
    modelId = modelId,
    displayName = displayName,
    supportsTools = supportsTools,
    supportsVision = supportsVision,
    isPinned = isPinned
)

/**
 * Maps an [AiAssistant] domain model to its entity row.
 */
fun AiAssistant.toEntity() = AiAssistantEntity(
    id = id,
    builtinKey = builtinKey,
    name = name,
    description = description,
    systemPrompt = systemPrompt,
    avatarIcon = avatarIcon,
    avatarColorIndex = avatarColorIndex,
    defaultGrantsJson = serializeGrants(defaultGrants),
    isBuiltin = isBuiltin,
    sortOrder = sortOrder
)

// --- Grant JSON helpers ---

/**
 * Serializable wrapper for the JSON-encoded capability grant set stored in
 * [AiAssistantEntity.defaultGrantsJson].
 */
@Serializable
private data class GrantsPayload(val grants: List<String>)

/**
 * Serializes a capability set to its JSON storage form.
 */
fun serializeGrants(grants: Set<AiCapability>): String {
    val payload = GrantsPayload(grants = grants.map { it.name })
    return Json.encodeToString(GrantsPayload.serializer(), payload)
}

/**
 * Parses a capability set from its JSON storage form, tolerating unknown
 * capability names from newer app versions.
 */
fun parseGrants(json: String): Set<AiCapability> = runCatching {
    val payload = Json.decodeFromString(GrantsPayload.serializer(), json)
    payload.grants.mapNotNull { name ->
        runCatching { AiCapability.valueOf(name) }.getOrNull()
    }.toSet()
}.getOrDefault(emptySet())

/**
 * Parses a web-search provider name defensively, defaulting to [WebSearchProvider.NONE].
 */
fun parseWebSearchProvider(raw: String): WebSearchProvider =
    runCatching { WebSearchProvider.valueOf(raw) }.getOrDefault(WebSearchProvider.NONE)
