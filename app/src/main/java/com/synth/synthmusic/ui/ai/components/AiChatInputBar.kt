package com.synth.synthmusic.ui.ai.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.synth.synthmusic.R

/**
 * Bottom composer of the AI surface: a rounded pill containing the image
 * attach button, the message field and a circular send/stop button.
 *
 * The field is a compact [BasicTextField] (44 dp single-line height) so the
 * pill hugs its content and the send button sits visually centered in the
 * single-line state.
 *
 * @param value current draft text.
 * @param onValueChange called on every keystroke.
 * @param onSend sends the draft.
 * @param onStop cancels the running stream.
 * @param onAttachImage opens the system photo picker.
 * @param isStreaming true while an answer is streaming.
 * @param canAttach whether the active model supports vision input.
 * @param canSend whether there is something to send (text or attachments).
 * @param enabled false while no AI provider is configured.
 * @param modifier the modifier to be applied to the bar.
 */
@Composable
fun AiChatInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    onAttachImage: () -> Unit,
    isStreaming: Boolean,
    canAttach: Boolean,
    canSend: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            modifier = Modifier.padding(4.dp)
        ) {
            IconButton(
                onClick = onAttachImage,
                enabled = canAttach && enabled,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AddPhotoAlternate,
                    contentDescription = stringResource(R.string.cd_attach_image),
                    tint = if (canAttach && enabled) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                    }
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = stringResource(
                            if (enabled) R.string.ai_chat_input_hint
                            else R.string.ai_chat_needs_provider
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (enabled) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = if (enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                        }
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { onSend() })
                )
            }
            if (isStreaming) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 2.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .clickable(onClick = onStop),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = stringResource(R.string.cd_stop),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            } else {
                val sendable = canSend && enabled
                Box(
                    modifier = Modifier
                        .padding(bottom = 2.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (sendable) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            }
                        )
                        .clickable(enabled = sendable, onClick = onSend),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.cd_send),
                        tint = if (sendable) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        }
                    )
                }
            }
        }
    }
}
