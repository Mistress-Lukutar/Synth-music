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
 * UI bridge the dispatcher consults for confirmations and missing-grant
 * prompts. Implemented by the chat ViewModel via a CompletableDeferred so
 * the engine suspends until the user resolves the card.
 */
interface ApprovalBridge {

    /**
     * Shows an approval card and suspends until the user approves/denies.
     */
    suspend fun requestConfirmation(request: ConfirmationRequest): Boolean

    /**
     * Asks the user to grant [missing] capabilities for this chat.
     * Implementations must persist the grant (per-chat) before returning true.
     */
    suspend fun requestPermissionGrant(missing: Set<AiCapability>): Boolean

    /** Whether [toolName] was marked "always allow in this chat". */
    fun isAlwaysAllowed(toolName: String): Boolean
}

/**
 * Context passed to a tool execution.
 *
 * @property chatId the chat that triggered the call.
 * @property grants capabilities granted in this chat (already intersected
 * with the global kill-switches).
 * @property approvalBridge UI bridge for confirmations/permission prompts;
 * null in headless contexts (tests), which auto-denies interactive prompts.
 * @property imageSink sink for vision tool results; images appended here are
 * added to the next tool-result message.
 */
data class ToolContext(
    val chatId: Long,
    val grants: Set<AiCapability>,
    val approvalBridge: ApprovalBridge? = null,
    val imageSink: MutableList<AiMessagePart.Image> = mutableListOf()
)

/**
 * Central tool execution facade. Implementations resolve tool
 * specifications, enforce the permission/confirmation policy, execute on
 * background dispatchers and write audit-log entries.
 */
interface ToolDispatcher {

    /**
     * Returns the specs of all tools available to a chat with [grants].
     * Only these are advertised to the model.
     */
    fun specsFor(grants: Set<AiCapability>): List<AiToolSpec>

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

    override fun specsFor(grants: Set<AiCapability>): List<AiToolSpec> = emptyList()

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
