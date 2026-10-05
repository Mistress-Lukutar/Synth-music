package com.synth.synthmusic.ui.ai.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.synth.synthmusic.R
import com.synth.synthmusic.domain.model.AiChatMessage
import com.synth.synthmusic.domain.model.AiMessagePart
import com.synth.synthmusic.domain.model.AiRole
import com.synth.synthmusic.ui.ai.components.MarkdownText
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Chat screen rendering one conversation: message bubbles, tool activity
 * chips, streaming output and the input bar with image attachments.
 *
 * @param chatId id of the chat to display.
 * @param onNavigateBack pops the back stack.
 * @param modifier the modifier to be applied to the screen.
 * @param viewModel injected by Koin with the [chatId] nav argument.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiChatScreen(
    chatId: Long,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AiChatViewModel = koinViewModel { parametersOf(chatId) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var input by remember { mutableStateOf("") }
    var showMenu by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var showPermissions by remember { mutableStateOf(false) }
    var showActivity by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.onEvent(AiChatUiEvent.AttachImage(it)) }
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onEvent(AiChatUiEvent.ConsumeError)
        }
    }

    // Auto-scroll to the newest message as the transcript grows.
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.chat?.title ?: "")
                        state.modelLabel?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                    }
                    DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ai_chat_rename)) },
                            onClick = {
                                showMenu = false
                                showRename = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ai_chat_clear)) },
                            onClick = {
                                showMenu = false
                                showClearConfirm = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ai_chat_delete)) },
                            onClick = {
                                showMenu = false
                                showDeleteConfirm = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ai_chat_regenerate)) },
                            onClick = {
                                showMenu = false
                                viewModel.onEvent(AiChatUiEvent.Regenerate)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ai_chat_permissions)) },
                            onClick = {
                                showMenu = false
                                showPermissions = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ai_chat_activity)) },
                            onClick = {
                                showMenu = false
                                showActivity = true
                            }
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items = state.messages, key = { it.id }) { message ->
                    MessageBubble(message = message)
                }

                state.pendingApproval?.let { request ->
                    item("approval_${request.toolName}") {
                        ApprovalCard(
                            request = request,
                            onApprove = {
                                viewModel.onEvent(AiChatUiEvent.ResolveApproval(approved = true))
                            },
                            onAlwaysAllow = {
                                viewModel.onEvent(
                                    AiChatUiEvent.ResolveApproval(approved = true, alwaysAllow = true)
                                )
                            },
                            onDeny = {
                                viewModel.onEvent(AiChatUiEvent.ResolveApproval(approved = false))
                            }
                        )
                    }
                }

                state.pendingGrantRequest?.let { missing ->
                    item("grant_request") {
                        GrantRequestCard(
                            missing = missing,
                            onGrant = {
                                viewModel.onEvent(AiChatUiEvent.ResolveGrant(granted = true))
                            },
                            onDeny = {
                                viewModel.onEvent(AiChatUiEvent.ResolveGrant(granted = false))
                            }
                        )
                    }
                }
            }

            // Pending attachment strip
            if (state.pendingAttachments.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.pendingAttachments.forEachIndexed { index, _ ->
                        AssistChip(
                            onClick = {
                                viewModel.onEvent(AiChatUiEvent.RemoveAttachment(index))
                            },
                            label = { Text("Image ${index + 1}") },
                            trailingIcon = {
                                Icon(Icons.Default.Close, contentDescription = "Remove")
                            }
                        )
                    }
                }
            }

            // Input bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(
                    onClick = {
                        photoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    enabled = state.supportsVision
                ) {
                    Icon(
                        Icons.Default.AddPhotoAlternate,
                        contentDescription = "Attach image",
                        tint = if (state.supportsVision) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        }
                    )
                }
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.ai_chat_input_hint)) },
                    maxLines = 5
                )
                if (state.isStreaming) {
                    IconButton(onClick = { viewModel.onEvent(AiChatUiEvent.Stop) }) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "Stop",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                } else {
                    IconButton(
                        onClick = {
                            viewModel.onEvent(AiChatUiEvent.Send(input))
                            input = ""
                        },
                        enabled = input.isNotBlank() || state.pendingAttachments.isNotEmpty()
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }

    if (showRename) {
        var title by remember { mutableStateOf(state.chat?.title ?: "") }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text(stringResource(R.string.ai_chat_rename)) },
            text = {
                OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onEvent(AiChatUiEvent.Rename(title))
                    showRename = false
                }) { Text(stringResource(R.string.ai_provider_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) {
                    Text(stringResource(R.string.ai_provider_cancel))
                }
            }
        )
    }

    if (showDeleteConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.ai_chat_delete),
            text = stringResource(R.string.ai_chat_delete_confirm),
            onConfirm = {
                showDeleteConfirm = false
                viewModel.onEvent(AiChatUiEvent.DeleteChat)
                onNavigateBack()
            },
            onDismiss = { showDeleteConfirm = false }
        )
    }

    if (showClearConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.ai_chat_clear),
            text = stringResource(R.string.ai_chat_clear_confirm),
            onConfirm = {
                showClearConfirm = false
                viewModel.onEvent(AiChatUiEvent.ClearMessages)
            },
            onDismiss = { showClearConfirm = false }
        )
    }

    if (showPermissions) {
        PermissionsDialog(
            grants = state.grants,
            onToggle = { capability, granted ->
                viewModel.onEvent(AiChatUiEvent.SetGrant(capability, granted))
            },
            onDismiss = { showPermissions = false }
        )
    }

    if (showActivity) {
        ActivityLogDialog(
            entries = viewModel.actionLog.collectAsStateWithLifecycle().value,
            onDismiss = { showActivity = false }
        )
    }
}

@Composable
private fun PermissionsDialog(
    grants: Map<com.synth.synthmusic.domain.model.AiCapability, Boolean>,
    onToggle: (com.synth.synthmusic.domain.model.AiCapability, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_chat_permissions)) },
        text = {
            Column {
                com.synth.synthmusic.domain.model.AiCapability.entries.forEach { capability ->
                    val explicit = grants[capability]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(capability, explicit == false) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        androidx.compose.material3.Checkbox(
                            checked = explicit == true,
                            onCheckedChange = { onToggle(capability, it) }
                        )
                        Text(
                            capability.name.lowercase().replace('_', ' ')
                                .replaceFirstChar { it.uppercase() }
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.ai_chat_permissions_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ai_provider_cancel))
            }
        }
    )
}

@Composable
private fun ActivityLogDialog(
    entries: List<com.synth.synthmusic.domain.model.AiActionLogEntry>,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_chat_activity)) },
        text = {
            if (entries.isEmpty()) {
                Text(stringResource(R.string.ai_chat_activity_empty))
            } else {
                LazyColumn(modifier = Modifier.height(320.dp)) {
                    items(items = entries, key = { it.id }) { entry ->
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(
                                "${entry.toolName} • ${entry.outcome.name.lowercase()}",
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                entry.summary,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ai_provider_cancel))
            }
        }
    )
}

@Composable
private fun MessageBubble(message: AiChatMessage) {
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
                        if (message.status == com.synth.synthmusic.domain.model.AiMessageStatus.STREAMING) {
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
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(8.dp)
            )
        }
    }
}

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

@Composable
private fun ApprovalCard(
    request: com.synth.synthmusic.domain.usecase.ai.ConfirmationRequest,
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
                if (request.level == com.synth.synthmusic.domain.usecase.ai.ConfirmationLevel.CONFIRM) {
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

@Composable
private fun GrantRequestCard(
    missing: Set<com.synth.synthmusic.domain.model.AiCapability>,
    onGrant: () -> Unit,
    onDeny: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stringResource(R.string.ai_chat_grant_title),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = missing.joinToString { it.name },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(vertical = 6.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onGrant) {
                    Text(stringResource(R.string.ai_chat_grant_for_chat))
                }
                TextButton(onClick = onDeny) {
                    Text(stringResource(R.string.ai_provider_cancel))
                }
            }
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.ai_chat_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ai_provider_cancel))
            }
        }
    )
}
