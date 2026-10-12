package com.synth.synthmusic.domain.usecase.ai

import com.synth.synthmusic.domain.model.AiCapability
import com.synth.synthmusic.domain.model.AiMessagePart
import com.synth.synthmusic.domain.model.AiToolCall
import com.synth.synthmusic.domain.model.AiToolSpec

/**
 * A confirmation prompt shown to the user as an approval card.
 *
 * @property toolName the tool asking for approval.
 * @property summary human-readable action summary (per-tool formatter).
 * @property level confirmation level (CONFIRM allows "always in this chat").
 */
data class ConfirmationRequest(
    val toolName: String,
    val summary: String,
    val level: ConfirmationLevel
)

/**
 * UI bridge the dispatcher consults for confirmations. Implemented by the
 * chat ViewModel via a CompletableDeferred so the engine suspends until the
 * user resolves the card.
 */
interface ApprovalBridge {

    /**
     * Shows an approval card and suspends until the user approves/denies.
     */
    suspend fun requestConfirmation(request: ConfirmationRequest): Boolean

    /** Whether [toolName] was marked "always allow in this chat". */
    fun isAlwaysAllowed(toolName: String): Boolean
}

/**
 * Context passed to a tool execution.
 *
 * @property chatId the chat that triggered the call.
 * @property grants capabilities granted in this chat (assistant defaults
 * intersected with the global kill-switches).
 * @property allowedTools explicit per-tool allow-list of the chat's assistant;
 * `null` means unrestricted (capability policy only). Calls outside the list
 * are denied.
 * @property approvalBridge UI bridge for confirmations; null in headless
 * contexts (tests), which auto-denies interactive prompts.
 * @property imageSink sink for vision tool results; images appended here are
 * added to the next tool-result message.
 */
data class ToolContext(
    val chatId: Long,
    val grants: Set<AiCapability>,
    val allowedTools: Set<String>? = null,
    val approvalBridge: ApprovalBridge? = null,
    val imageSink: MutableList<AiMessagePart.Image> = mutableListOf()
)

/**
 * Editor-facing description of a registered tool: what the assistant editor
 * needs to render per-tool grant switches. Not advertised to the model.
 *
 * @property name unique snake_case tool name.
 * @property description model-facing usage guidance (English).
 * @property requiredGrants capabilities this tool depends on.
 * @property confirmationLevel confirmation policy applied on execution.
 */
data class AiToolDescriptor(
    val name: String,
    val description: String,
    val requiredGrants: Set<AiCapability>,
    val confirmationLevel: ConfirmationLevel
)

/**
 * Central tool execution facade. Implementations resolve tool
 * specifications, enforce the permission/confirmation policy, execute on
 * background dispatchers and write audit-log entries.
 */
interface ToolDispatcher {

    /**
     * Returns the specs of all tools available to a chat with [grants],
     * restricted to [allowedTools] when it is non-null. Only these are
     * advertised to the model; tools whose [AiTool.isAvailable] reports
     * `false` (e.g. web_search without a configured key) are omitted.
     */
    suspend fun specsFor(grants: Set<AiCapability>, allowedTools: Set<String>? = null): List<AiToolSpec>

    /**
     * Descriptors of every registered tool (built-in and user-imported
     * custom) for the assistant editor's per-tool grant switches.
     */
    suspend fun allTools(): List<AiToolDescriptor>

    /**
     * Executes [call]; never throws — failures are returned as an error
     * [AiMessagePart.ToolResult] so the model can react.
     */
    suspend fun execute(call: AiToolCall, context: ToolContext): AiMessagePart.ToolResult
}

/**
 * M3 placeholder dispatcher with no tools registered; replaced by the full
 * registry in the tool-framework phase.
 */
class EmptyToolDispatcher : ToolDispatcher {

    override suspend fun specsFor(
        grants: Set<AiCapability>,
        allowedTools: Set<String>?
    ): List<AiToolSpec> = emptyList()

    override suspend fun allTools(): List<AiToolDescriptor> = emptyList()

    override suspend fun execute(
        call: AiToolCall,
        context: ToolContext
    ): AiMessagePart.ToolResult = AiMessagePart.ToolResult(
        toolCallId = call.id,
        name = call.name,
        content = "Unknown tool: ${call.name}",
        isError = true
    )
}
