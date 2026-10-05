package com.synth.synthmusic.domain.model

/**
 * Domain model of an AI assistant persona: a named system instruction with an
 * avatar and default capability grants applied to new chats.
 */
data class AiAssistant(
    val id: Long,
    val builtinKey: String?,
    val name: String,
    val description: String,
    val systemPrompt: String,
    val avatarIcon: String,
    val avatarColorIndex: Int,
    val defaultGrants: Set<AiCapability>,
    val isBuiltin: Boolean,
    val sortOrder: Int
)

/**
 * Domain model of one audited AI action (tool call) from the global log.
 */
data class AiActionLogEntry(
    val id: Long,
    val chatId: Long?,
    val timestamp: Long,
    val toolName: String,
    val argsJson: String,
    val outcome: AiActionOutcome,
    val summary: String
)
