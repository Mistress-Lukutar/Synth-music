package com.synth.synthmusic.domain.usecase.ai

import com.synth.synthmusic.domain.model.AiActionOutcome
import com.synth.synthmusic.domain.model.AiCapability
import com.synth.synthmusic.domain.model.AiMessagePart
import com.synth.synthmusic.domain.model.AiToolCall
import com.synth.synthmusic.domain.model.AiToolSpec
import com.synth.synthmusic.data.ai.client.parseArgsOrEmpty
import com.synth.synthmusic.domain.repository.AiActionLogRepository
import com.synth.synthmusic.domain.repository.AiSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Production [ToolDispatcher]: resolves tools from the registered set,
 * enforces the capability policy (missing grants are denied — tools outside
 * the effective grants are never advertised in the first place), executes on
 * [Dispatchers.IO], truncates oversized results and writes an audit entry for
 * every call — including denied and failed ones.
 */
class ToolDispatcherImpl(
    private val tools: List<AiTool>,
    private val actionLogRepository: AiActionLogRepository,
    private val settingsRepository: AiSettingsRepository
) : ToolDispatcher {

    private val toolsByName: Map<String, AiTool> = tools.associateBy { it.name }

    /**
     * Returns specs of all registered tools whose required grants are
     * satisfied by [grants].
     */
    override fun specsFor(grants: Set<AiCapability>): List<AiToolSpec> =
        tools.filter { tool -> tool.requiredGrants.all { it in grants } }
            .map { it.toSpec() }

    /**
     * Whether a call to [toolName] requires an approval card in the chat.
     */
    fun requiresConfirmation(toolName: String): ConfirmationLevel =
        toolsByName[toolName]?.confirmationLevel ?: ConfirmationLevel.NONE

    override suspend fun execute(
        call: AiToolCall,
        context: ToolContext
    ): AiMessagePart.ToolResult {
        val tool = toolsByName[call.name]
        if (tool == null) {
            audit(context.chatId, call, AiActionOutcome.FAILED, "Unknown tool")
            return errorResult(call, "Unknown tool: ${call.name}")
        }

        // --- Grant policy ---
        val missing = tool.requiredGrants.filter { it !in context.grants }
        if (missing.isNotEmpty()) {
            val names = missing.joinToString(", ") { it.name.lowercase() }
            audit(context.chatId, call, AiActionOutcome.DENIED, "Missing grants: $names")
            return errorResult(
                call,
                "User denied: this action requires the $names capability " +
                    "(enable it in AI settings)"
            )
        }

        // --- Confirmation policy ---
        when (tool.confirmationLevel) {
            ConfirmationLevel.NONE -> Unit
            ConfirmationLevel.CONFIRM -> {
                val confirmEdits = runCatching {
                    settingsRepository.current().confirmEdits
                }.getOrDefault(true)
                val autoAllowed = !confirmEdits ||
                    (context.approvalBridge?.isAlwaysAllowed(call.name) ?: false)
                if (!autoAllowed) {
                    val approved = context.approvalBridge?.requestConfirmation(
                        ConfirmationRequest(
                            call.name,
                            tool.summarize(parseArgsOrEmpty(call.argumentsJson)),
                            tool.confirmationLevel
                        )
                    ) ?: false
                    if (!approved) {
                        audit(context.chatId, call, AiActionOutcome.DENIED, "User denied")
                        return errorResult(call, "User denied the action")
                    }
                }
            }
            ConfirmationLevel.ALWAYS_CONFIRM -> {
                val approved = context.approvalBridge?.requestConfirmation(
                    ConfirmationRequest(
                        call.name,
                        tool.summarize(parseArgsOrEmpty(call.argumentsJson)),
                        tool.confirmationLevel
                    )
                ) ?: false
                if (!approved) {
                    audit(context.chatId, call, AiActionOutcome.DENIED, "User denied")
                    return errorResult(call, "User denied the action")
                }
            }
        }

        return try {
            val outcome = withContext(Dispatchers.IO) {
                tool.execute(parseArgsOrEmpty(call.argumentsJson), context)
            }
            outcome.image?.let { context.imageSink.add(it) }
            audit(
                context.chatId, call,
                if (outcome.isError) AiActionOutcome.FAILED else AiActionOutcome.EXECUTED,
                outcome.content.take(SUMMARY_MAX_CHARS)
            )
            AiMessagePart.ToolResult(
                toolCallId = call.id,
                name = call.name,
                content = truncateToolResult(outcome.content),
                isError = outcome.isError
            )
        } catch (e: Exception) {
            audit(
                context.chatId, call, AiActionOutcome.FAILED,
                "Exception: ${e.message?.take(SUMMARY_MAX_CHARS)}"
            )
            errorResult(call, e.message ?: "Tool execution failed")
        }
    }

    private suspend fun audit(
        chatId: Long,
        call: AiToolCall,
        outcome: AiActionOutcome,
        summary: String
    ) {
        runCatching {
            actionLogRepository.log(
                chatId = chatId,
                toolName = call.name,
                argsJson = call.argumentsJson.take(MAX_ARGS_LOG_CHARS),
                outcome = outcome,
                summary = summary
            )
        }
        // Audit failures must never break tool execution.
    }

    private fun errorResult(call: AiToolCall, message: String) = AiMessagePart.ToolResult(
        toolCallId = call.id,
        name = call.name,
        content = message,
        isError = true
    )

    private companion object {
        const val SUMMARY_MAX_CHARS = 200
        const val MAX_ARGS_LOG_CHARS = 2000
    }
}
