package com.synth.synthmusic.ui.ai.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.synth.synthmusic.R
import com.synth.synthmusic.domain.model.AiChatMessage
import com.synth.synthmusic.domain.model.AiMessagePart
import com.synth.synthmusic.domain.model.AiMessageStatus
import com.synth.synthmusic.domain.model.AiRole
import com.synth.synthmusic.domain.usecase.ai.ConfirmationLevel
import com.synth.synthmusic.domain.usecase.ai.ConfirmationRequest

/**
 * Renders a single chat message: right-aligned user bubble, left-aligned
 * assistant bubble with markdown, or expandable tool-activity chips.
 *
 * @param message the message to render.
 */
@Composable
fun AiMessageBubble(message: AiChatMessage) {
    when (message.role) {
        AiRole.USER -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.End
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(16.dp, 4.dp, 16.dp, 16.dp)
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
                                is AiMessagePart.Image -> AssistChip(
                                    onClick = {},
                                    label = { Text(stringResource(R.string.ai_chat_image_attachment)) }
                                )
                                else -> Unit
                            }
                        }
                    }
                }
            }
        }

        AiRole.ASSISTANT -> {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.Start
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    shape = RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 320.dp)
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        MarkdownText(
                            markdown = message.text,
                            modifier = Modifier.fillMaxWidth()
                        )
                        message.parts.filterIsInstance<AiMessagePart.ToolCall>().forEach { call ->
                            ToolCallChip(call)
                        }
                        if (message.status == AiMessageStatus.STREAMING) {
                            Text(
                                "▍",
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        AiRole.TOOL -> {
            message.parts.filterIsInstance<AiMessagePart.ToolResult>().forEach { result ->
                ToolResultChip(result)
            }
        }

        AiRole.SYSTEM -> Unit
    }
}

/**
 * Expandable chip describing a tool invocation made by the assistant.
 *
 * @param call the tool call to render.
 */
@Composable
private fun ToolCallChip(call: AiMessagePart.ToolCall) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        AssistChip(
            onClick = { expanded = !expanded },
            label = { Text("🔧 ${call.name}") }
        )
        if (expanded) {
            Text(
                call.argumentsJson,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(8.dp)
            )
        }
    }
}

/**
 * Expandable chip describing a finished tool run (success or error).
 *
 * @param result the tool result to render.
 */
@Composable
private fun ToolResultChip(result: AiMessagePart.ToolResult) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        AssistChip(
            onClick = { expanded = !expanded },
            label = {
                Text(
                    if (result.isError) "⚠ ${result.name}" else "🔧 ${result.name}"
                )
            }
        )
        if (expanded) {
            Text(
                result.content.take(2000),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(8.dp)
            )
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
