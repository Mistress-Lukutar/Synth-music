package com.synth.synthmusic.ui.ai.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.synth.synthmusic.R
import com.synth.synthmusic.domain.model.AiChatMessage
import com.synth.synthmusic.domain.model.AiMessagePart
import com.synth.synthmusic.domain.model.AiMessageStatus
import com.synth.synthmusic.domain.model.AiRole
import com.synth.synthmusic.domain.usecase.ai.ChatTree
import com.synth.synthmusic.domain.usecase.ai.ConfirmationLevel
import com.synth.synthmusic.domain.usecase.ai.ConfirmationRequest

/**
 * One visual row of the chat transcript. Consecutive tool messages are
 * merged so each invocation is displayed exactly once, paired with its
 * result.
 */
sealed interface AiChatRow {

    /** Stable [androidx.compose.foundation.lazy.LazyColumn] item key. */
    val key: String

    /** A right-aligned user bubble. */
    data class User(val message: AiChatMessage) : AiChatRow {
        override val key: String get() = "m_${message.id}"
    }

    /** A left-aligned assistant bubble with markdown text. */
    data class Assistant(val message: AiChatMessage) : AiChatRow {
        override val key: String get() = "m_${message.id}"
    }

    /**
     * A compact tool-activity card anchored to the assistant message that
     * requested the calls.
     *
     * @param anchorId id of the assistant message that requested the calls.
     * @param calls one entry per requested call, paired with its result.
     */
    data class ToolActivity(
        val anchorId: Long,
        val calls: List<ToolCallUi>
    ) : AiChatRow {
        override val key: String get() = "t_$anchorId"
    }
}

/**
 * UI model pairing a requested tool call with its result; [result] is null
 * while the call is still running.
 */
data class ToolCallUi(
    val name: String,
    val argumentsJson: String,
    val result: AiMessagePart.ToolResult?
)

/**
 * Groups the flat message list into visual rows: user bubbles, assistant
 * text bubbles and tool-activity cards that merge each assistant tool-call
 * message with the TOOL results that follow it, so a call is never rendered
 * twice and empty bubbles disappear.
 *
 * @param messages the chat transcript in chronological order.
 * @return rows ready to be rendered by [AiChatRowContent].
 */
fun buildChatRows(messages: List<AiChatMessage>): List<AiChatRow> {
    val rows = mutableListOf<AiChatRow>()
    var i = 0
    while (i < messages.size) {
        val message = messages[i]
        when (message.role) {
            AiRole.USER -> rows += AiChatRow.User(message)

            AiRole.ASSISTANT -> {
                val calls = message.parts.filterIsInstance<AiMessagePart.ToolCall>()
                // Render a text bubble only when there is visible content;
                // streaming progress is signaled by the input bar and, once
                // reasoning starts, by the reasoning spoiler.
                val hasContent = message.parts.any { part ->
                    (part is AiMessagePart.Text && part.text.isNotBlank()) ||
                        (part is AiMessagePart.Reasoning && part.text.isNotBlank())
                }
                if (hasContent) {
                    rows += AiChatRow.Assistant(message)
                }
                if (calls.isNotEmpty()) {
                    val results = mutableListOf<AiMessagePart.ToolResult>()
                    var j = i + 1
                    while (j < messages.size && messages[j].role == AiRole.TOOL) {
                        results += messages[j].parts.filterIsInstance<AiMessagePart.ToolResult>()
                        j++
                    }
                    rows += AiChatRow.ToolActivity(
                        anchorId = message.id,
                        calls = calls.map { call ->
                            ToolCallUi(
                                name = call.name,
                                argumentsJson = call.argumentsJson,
                                result = results.firstOrNull { it.toolCallId == call.toolCallId }
                            )
                        }
                    )
                    i = j
                    continue
                }
            }

            AiRole.TOOL -> {
                // Orphan results (e.g. after transcript trimming) stay visible.
                val results = message.parts.filterIsInstance<AiMessagePart.ToolResult>()
                if (results.isNotEmpty()) {
                    rows += AiChatRow.ToolActivity(
                        anchorId = message.id,
                        calls = results.map { ToolCallUi(it.name, "", it) }
                    )
                }
            }

            AiRole.SYSTEM -> Unit
        }
        i++
    }
    return rows
}

/**
 * Renders a single transcript row produced by [buildChatRows].
 *
 * @param row the row to render.
 * @param variants variant positions of visible messages among their siblings.
 * @param onRetry regenerates the answer around the given message.
 * @param onEdit opens the edit flow for the given user message.
 * @param onSwitchVariant shows the branch of the given sibling instead.
 */
@Composable
fun AiChatRowContent(
    row: AiChatRow,
    variants: Map<Long, ChatTree.Variants> = emptyMap(),
    onRetry: (Long) -> Unit = {},
    onEdit: (Long) -> Unit = {},
    onSwitchVariant: (Long) -> Unit = {}
) {
    when (row) {
        is AiChatRow.User -> UserBubble(
            message = row.message,
            variants = variants[row.message.id],
            onEdit = onEdit,
            onSwitchVariant = onSwitchVariant
        )
        is AiChatRow.Assistant -> AssistantBubble(
            message = row.message,
            variants = variants[row.message.id],
            onRetry = onRetry,
            onSwitchVariant = onSwitchVariant
        )
        is AiChatRow.ToolActivity -> ToolActivityCard(row.calls)
    }
}

/**
 * Data URL for an inline base64 image part, understood by Coil out of the box.
 */
private fun imageDataUrl(image: AiMessagePart.Image): String =
    "data:${image.mimeType};base64,${image.base64}"

/**
 * Decoded preview of an image attachment.
 *
 * @param image the image part to render.
 */
@Composable
private fun ImageThumbnail(image: AiMessagePart.Image) {
    AsyncImage(
        model = imageDataUrl(image),
        contentDescription = stringResource(R.string.ai_chat_image_attachment),
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(96.dp)
            .clip(RoundedCornerShape(12.dp))
    )
}

/**
 * `< k/n >` switcher shown above a bubble that has sibling variants
 * (regenerate/edit branches).
 *
 * @param variants the variant position of the message.
 * @param onSelect shows the branch of the sibling with the given id.
 */
@Composable
private fun VariantSwitcher(
    variants: ChatTree.Variants,
    onSelect: (Long) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        IconButton(
            onClick = { onSelect(variants.siblingIds[variants.index - 1]) },
            enabled = variants.index > 0,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowLeft,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = "${variants.index + 1}/${variants.count}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        IconButton(
            onClick = { onSelect(variants.siblingIds[variants.index + 1]) },
            enabled = variants.index < variants.count - 1,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Right-aligned user bubble with text parts and image thumbnails. Long-press
 * opens the edit/copy menu.
 *
 * @param message the user message to render.
 * @param variants variant position of the message, if it has siblings.
 * @param onEdit opens the edit dialog for the message.
 * @param onSwitchVariant shows the branch of the given sibling instead.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun UserBubble(
    message: AiChatMessage,
    variants: ChatTree.Variants?,
    onEdit: (Long) -> Unit,
    onSwitchVariant: (Long) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        if (variants != null) {
            VariantSwitcher(variants = variants, onSelect = onSwitchVariant)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Box {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp),
                    modifier = Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = { menuOpen = true }
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 300.dp)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        message.parts.forEach { part ->
                            when (part) {
                                is AiMessagePart.Text -> Text(part.text)
                                is AiMessagePart.Image -> ImageThumbnail(part)
                                else -> Unit
                            }
                        }
                    }
                }
                MessageActionMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    actions = listOf(
                        MessageAction(stringResource(R.string.ai_chat_edit)) {
                            menuOpen = false
                            onEdit(message.id)
                        },
                        MessageAction(stringResource(R.string.ai_chat_copy)) {
                            menuOpen = false
                            clipboard.setText(AnnotatedString(message.text))
                        }
                    )
                )
            }
        }
    }
}

/**
 * Left-aligned assistant bubble with markdown text and a collapsible
 * reasoning spoiler; tool calls are rendered separately as
 * [ToolActivityCard]. Long-press opens the retry/copy menu.
 *
 * @param message the assistant message to render.
 * @param variants variant position of the message, if it has siblings.
 * @param onRetry regenerates this answer as a new sibling branch.
 * @param onSwitchVariant shows the branch of the given sibling instead.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AssistantBubble(
    message: AiChatMessage,
    variants: ChatTree.Variants?,
    onRetry: (Long) -> Unit,
    onSwitchVariant: (Long) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    var menuOpen by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        if (variants != null) {
            VariantSwitcher(variants = variants, onSelect = onSwitchVariant)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Start
        ) {
            Box {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp),
                    modifier = Modifier.combinedClickable(
                        onClick = {},
                        onLongClick = { menuOpen = true }
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 320.dp)
                            .padding(12.dp)
                    ) {
                        val reasoning = message.parts
                            .filterIsInstance<AiMessagePart.Reasoning>()
                            .joinToString("") { it.text }
                        // Reasoning is live only while it is the latest step: once the
                        // model starts emitting text or tool calls the trace is done,
                        // even though the message as a whole is still streaming.
                        val reasoningStreaming = message.status == AiMessageStatus.STREAMING &&
                            message.text.isBlank() &&
                            message.parts.none { it is AiMessagePart.ToolCall }
                        if (reasoning.isNotBlank()) {
                            ReasoningSection(
                                reasoning = reasoning,
                                isStreaming = reasoningStreaming
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                        if (message.text.isNotBlank()) {
                            MarkdownText(
                                markdown = message.text,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
                MessageActionMenu(
                    expanded = menuOpen,
                    onDismiss = { menuOpen = false },
                    actions = listOf(
                        MessageAction(stringResource(R.string.ai_chat_retry)) {
                            menuOpen = false
                            onRetry(message.id)
                        },
                        MessageAction(stringResource(R.string.ai_chat_copy)) {
                            menuOpen = false
                            clipboard.setText(AnnotatedString(message.text))
                        }
                    )
                )
            }
        }
    }
}

/**
 * One entry of a message long-press menu.
 */
private data class MessageAction(val label: String, val onClick: () -> Unit)

/**
 * Small dropdown menu anchored to a message bubble.
 *
 * @param expanded whether the menu is visible.
 * @param onDismiss closes the menu.
 * @param actions the menu entries.
 */
@Composable
private fun MessageActionMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    actions: List<MessageAction>
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        actions.forEach { action ->
            DropdownMenuItem(
                text = { Text(action.label) },
                onClick = action.onClick
            )
        }
    }
}

/**
 * Collapsed-by-default spoiler showing the model's reasoning trace. While
 * the message is still streaming the header shows a spinner.
 *
 * @param reasoning the concatenated reasoning text.
 * @param isStreaming whether the owning message is still being generated.
 */
@Composable
private fun ReasoningSection(reasoning: String, isStreaming: Boolean) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .clickable { expanded = !expanded }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (expanded) {
                    Icons.Default.KeyboardArrowUp
                } else {
                    Icons.Default.KeyboardArrowDown
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = stringResource(R.string.ai_chat_reasoning),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (isStreaming) {
                CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp
                )
            }
        }
        if (expanded) {
            Text(
                text = reasoning,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

/**
 * Compact card listing the tool calls of one assistant turn, each with a
 * live status icon: a spinner while running, a check mark on success and a
 * warning on error. Tapping a row expands its arguments and result content.
 *
 * @param calls the calls requested in one assistant message.
 */
@Composable
private fun ToolActivityCard(calls: List<ToolCallUi>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            shape = RoundedCornerShape(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 320.dp)
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                calls.forEach { call -> ToolCallRow(call) }
            }
        }
    }
}

/**
 * One expandable row inside a [ToolActivityCard].
 *
 * @param item the call/result pair to render.
 */
@Composable
private fun ToolCallRow(item: ToolCallUi) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { expanded = !expanded }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Build,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = item.name,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.weight(1f, fill = false)
            )
            Spacer(Modifier.width(8.dp))
            when {
                item.result == null -> CircularProgressIndicator(
                    modifier = Modifier.size(12.dp),
                    strokeWidth = 1.5.dp
                )
                item.result.isError -> Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp)
                )
                else -> Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
        if (expanded) {
            if (item.argumentsJson.isNotBlank()) {
                Text(
                    item.argumentsJson,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            item.result?.let { result ->
                Text(
                    result.content.take(2000),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (result.isError) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

/**
 * Card asking the user to approve a sensitive tool action.
 *
 * @param request the pending confirmation request.
 * @param onApprove approves this single action.
 * @param onAlwaysAllow approves and remembers the decision for this chat.
 * @param onDeny denies the action.
 */
@Composable
fun ApprovalCard(
    request: ConfirmationRequest,
    onApprove: () -> Unit,
    onAlwaysAllow: () -> Unit,
    onDeny: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.ai_chat_approval_title),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = request.summary,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 6.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onApprove) {
                    Text(stringResource(R.string.ai_chat_approve))
                }
                if (request.level == ConfirmationLevel.CONFIRM) {
                    TextButton(onClick = onAlwaysAllow) {
                        Text(stringResource(R.string.ai_chat_always_allow))
                    }
                }
                TextButton(onClick = onDeny) {
                    Text(stringResource(R.string.ai_chat_deny))
                }
            }
        }
    }
}
