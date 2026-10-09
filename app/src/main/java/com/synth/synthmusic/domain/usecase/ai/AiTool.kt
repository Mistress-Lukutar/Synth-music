package com.synth.synthmusic.domain.usecase.ai

import com.synth.synthmusic.domain.model.AiCapability
import com.synth.synthmusic.domain.model.AiToolSpec
import kotlinx.serialization.json.JsonObject

/** Hard cap for a single tool result payload returned to the model. */
const val MAX_TOOL_RESULT_CHARS = 8192

/** Marker appended when a tool result was truncated. */
const val TRUNCATION_MARKER = "\n…truncated"

/**
 * Outcome of a tool execution; returned to the model as a tool result
 * message. Failures are values, never exceptions. Vision tools may attach
 * an [image] that is appended to the conversation next to the text result.
 */
data class ToolOutcome(
    val content: String,
    val isError: Boolean = false,
    val image: com.synth.synthmusic.domain.model.AiMessagePart.Image? = null
)

/**
 * Confirmation policy of a tool.
 *
 * [NONE] executes silently; [CONFIRM] shows an approval card per action
 * (user may allow always-in-chat); [ALWAYS_CONFIRM] can never be
 * auto-approved (e.g. file deletion).
 */
enum class ConfirmationLevel {
    NONE,
    CONFIRM,
    ALWAYS_CONFIRM
}

/**
 * A callable tool the AI model can invoke. Implementations must be safe to
 * execute on Dispatchers.IO and validate their arguments strictly.
 */
interface AiTool {

    /** Unique snake_case tool name advertised to the model. */
    val name: String

    /** Model-facing description with usage guidance (English). */
    val description: String

    /** JSON Schema object describing the parameters. */
    val paramsSchema: JsonObject

    /** Capabilities required before this tool is advertised/executed. */
    val requiredGrants: Set<AiCapability>

    /**
     * Whether the tool is usable with the current app configuration, e.g.
     * web_search without a configured search provider and API key.
     * Unavailable tools are never advertised to the model. This is a
     * configuration check — per-chat permission gating happens via
     * [requiredGrants]. Must not throw for domain conditions; report
     * `false` instead.
     */
    suspend fun isAvailable(): Boolean = true

    /** Confirmation policy applied by the dispatcher. */
    val confirmationLevel: ConfirmationLevel
        get() = ConfirmationLevel.NONE

    /**
     * Human-readable summary of this specific invocation for the approval
     * card, e.g. "Change title: 'track_01' → 'Nightcall'".
     */
    fun summarize(args: JsonObject): String = args.toString().take(200)

    /**
     * Executes the tool; must not throw for domain failures — return
     * [ToolOutcome(isError = true)] instead.
     */
    suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome
}

/**
 * Builds the protocol-agnostic [AiToolSpec] for this tool.
 */
fun AiTool.toSpec(): AiToolSpec = AiToolSpec(
    name = name,
    description = description,
    paramsSchema = paramsSchema
)

/**
 * Truncates a tool result to [MAX_TOOL_RESULT_CHARS], appending
 * [TRUNCATION_MARKER] when content was cut.
 */
fun truncateToolResult(content: String): String =
    if (content.length <= MAX_TOOL_RESULT_CHARS) {
        content
    } else {
        content.take(MAX_TOOL_RESULT_CHARS) + TRUNCATION_MARKER
    }
