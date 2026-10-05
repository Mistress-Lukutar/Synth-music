package com.synth.synthmusic.ui.ai

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.synth.synthmusic.R
import com.synth.synthmusic.domain.model.AiAssistant
import com.synth.synthmusic.domain.model.AiActionLogEntry
import com.synth.synthmusic.ui.ai.components.AiChatDrawer
import com.synth.synthmusic.ui.ai.components.AiChatInputBar
import com.synth.synthmusic.ui.ai.components.AiChatRowContent
import com.synth.synthmusic.ui.ai.components.ApprovalCard
import com.synth.synthmusic.ui.ai.components.AssistantAvatar
import com.synth.synthmusic.ui.ai.components.AssistantPickerSheet
import com.synth.synthmusic.ui.ai.components.buildChatRows
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * Unified AI surface: the tab always opens on a fresh new-chat draft with
 * the composer at the bottom. Past conversations and the assistant selector
 * live in a side drawer opened by the burger button or a swipe from the
 * left edge. Selecting an assistant always starts a new chat with that
 * persona.
 *
 * @param onNavigateToAiSettings opens the AI settings screen.
 * @param onNavigateToAssistantEditor opens the assistant editor (null = create).
 * @param modifier the modifier to be applied to the screen.
 * @param viewModel injected by Koin.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AiScreen(
    onNavigateToAiSettings: () -> Unit,
    onNavigateToAssistantEditor: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AiViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val actionLog by viewModel.actionLog.collectAsStateWithLifecycle()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val imeVisible = WindowInsets.isImeVisible

    var showAgentPicker by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var showActivity by remember { mutableStateOf(false) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var renameChatId by remember { mutableStateOf<Long?>(null) }
    var renameInitialTitle by remember { mutableStateOf("") }
    var deleteChatId by remember { mutableStateOf<Long?>(null) }

    fun openDrawer() {
        keyboardController?.hide()
        focusManager.clearFocus()
        scope.launch { drawerState.open() }
    }

    fun closeDrawerThen(action: () -> Unit) {
        scope.launch { drawerState.close() }
        action()
    }

    val photoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.onEvent(AiUiEvent.AttachImage(it)) }
    }

    // System back, most specific first: hide the keyboard, then close the
    // drawer, then collapse an open chat to a fresh draft. None of these
    // cancels a running generation — only the Stop button does.
    BackHandler(enabled = !imeVisible && drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }
    BackHandler(enabled = !imeVisible && !drawerState.isOpen && state.activeChatId != null) {
        viewModel.onEvent(AiUiEvent.NewChat)
    }
    BackHandler(enabled = imeVisible) {
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    LaunchedEffect(state.error) {
        state.error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onEvent(AiUiEvent.ConsumeError)
        }
    }

    // Group the flat transcript into visual rows (bubbles + merged tool
    // activity cards) once per transcript emission.
    val chatRows = remember(state.messages) { buildChatRows(state.messages) }

    // Auto-scroll to the newest row as the transcript grows; an approval
    // card sits after the last row, so scroll to it when one appears.
    LaunchedEffect(chatRows.size, state.pendingApproval) {
        if (chatRows.isNotEmpty()) {
            val target = if (state.pendingApproval != null) {
                chatRows.size
            } else {
                chatRows.size - 1
            }
            listState.animateScrollToItem(target)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp),
                drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                AiChatDrawer(
                    chats = state.chats,
                    assistants = state.assistants,
                    activeChatId = state.activeChatId,
                    activeAssistant = state.activeAssistant,
                    selectedAssistantId = state.chat?.assistantId
                        ?: state.activeAssistant?.id,
                    onNewChat = { closeDrawerThen { viewModel.onEvent(AiUiEvent.NewChat) } },
                    onOpenChat = { chatId ->
                        closeDrawerThen { viewModel.onEvent(AiUiEvent.OpenChat(chatId)) }
                    },
                    onSelectAssistant = { assistant ->
                        closeDrawerThen {
                            viewModel.onEvent(AiUiEvent.SelectAssistant(assistant))
                        }
                    },
                    onRenameChat = { chat ->
                        renameInitialTitle = chat.title
                        renameChatId = chat.id
                    },
                    onDeleteChat = { chat -> deleteChatId = chat.id },
                    onCreateAssistant = {
                        closeDrawerThen { onNavigateToAssistantEditor(null) }
                    },
                    onNavigateToAiSettings = {
                        closeDrawerThen { onNavigateToAiSettings() }
                    }
                )
            }
        }
    ) {
        Scaffold(
            modifier = modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    navigationIcon = {
                        IconButton(onClick = { openDrawer() }) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = stringResource(R.string.cd_open_drawer)
                            )
                        }
                    },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(enabled = state.hasProvider) {
                                    focusManager.clearFocus()
                                    showAgentPicker = true
                                }
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            AssistantAvatar(
                                iconName = state.activeAssistant?.avatarIcon,
                                colorIndex = state.activeAssistant?.avatarColorIndex,
                                contentDescription = state.activeAssistant?.name,
                                size = 32.dp
                            )
                            Column(
                                modifier = Modifier
                                    .weight(1f, fill = false)
                                    .padding(horizontal = 10.dp)
                            ) {
                                Text(
                                    text = state.activeAssistant?.name
                                        ?: stringResource(R.string.ai_agent_general),
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                state.modelLabel?.let {
                                    Text(
                                        text = it,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            Icon(
                                imageVector = Icons.Default.ExpandMore,
                                contentDescription = stringResource(
                                    R.string.cd_choose_assistant
                                ),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.onEvent(AiUiEvent.NewChat) }) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.cd_new_chat)
                            )
                        }
                        if (state.activeChatId != null) {
                            Box {
                                IconButton(onClick = { showMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = stringResource(
                                            R.string.cd_chat_menu
                                        )
                                    )
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.ai_chat_rename)) },
                                        onClick = {
                                            showMenu = false
                                            renameInitialTitle = state.chat?.title ?: ""
                                            renameChatId = state.activeChatId
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
                                            deleteChatId = state.activeChatId
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(stringResource(R.string.ai_chat_regenerate)) },
                                        onClick = {
                                            showMenu = false
                                            viewModel.onEvent(AiUiEvent.Regenerate)
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
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    when {
                        !state.hasProvider -> SetupHint(
                            onNavigateToAiSettings = onNavigateToAiSettings
                        )

                        state.messages.isEmpty() -> NewChatHint(
                            assistant = state.activeAssistant,
                            onSuggestion = {
                                viewModel.onEvent(AiUiEvent.InputChanged(it))
                            }
                        )

                        else -> LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(items = chatRows, key = { it.key }) { row ->
                                AiChatRowContent(row)
                            }

                            state.pendingApproval?.let { request ->
                                item("approval_${request.toolName}_${request.summary.hashCode()}") {
                                    ApprovalCard(
                                        request = request,
                                        onApprove = {
                                            viewModel.onEvent(
                                                AiUiEvent.ResolveApproval(approved = true)
                                            )
                                        },
                                        onAlwaysAllow = {
                                            viewModel.onEvent(
                                                AiUiEvent.ResolveApproval(
                                                    approved = true,
                                                    alwaysAllow = true
                                                )
                                            )
                                        },
                                        onDeny = {
                                            viewModel.onEvent(
                                                AiUiEvent.ResolveApproval(approved = false)
                                            )
                                        }
                                    )
                                }
                            }
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
                                    viewModel.onEvent(AiUiEvent.RemoveAttachment(index))
                                },
                                label = { Text("Image ${index + 1}") },
                                trailingIcon = {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = stringResource(
                                            R.string.cd_remove_attachment
                                        )
                                    )
                                }
                            )
                        }
                    }
                }

                AiChatInputBar(
                    value = state.draftInput,
                    onValueChange = { viewModel.onEvent(AiUiEvent.InputChanged(it)) },
                    onSend = { viewModel.onEvent(AiUiEvent.Send(state.draftInput)) },
                    onStop = { viewModel.onEvent(AiUiEvent.Stop) },
                    onAttachImage = {
                        photoPicker.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly
                            )
                        )
                    },
                    isStreaming = state.isStreaming,
                    canAttach = state.supportsVision,
                    canSend = state.draftInput.isNotBlank() ||
                        state.pendingAttachments.isNotEmpty(),
                    enabled = state.hasProvider
                )
            }
        }
    }

    if (showAgentPicker) {
        AssistantPickerSheet(
            assistants = state.assistants,
            selectedAssistantId = state.chat?.assistantId ?: state.activeAssistant?.id,
            onAssistantSelected = { assistant ->
                showAgentPicker = false
                viewModel.onEvent(AiUiEvent.SelectAssistant(assistant))
            },
            onCreateCustom = {
                showAgentPicker = false
                onNavigateToAssistantEditor(null)
            },
            onDismiss = { showAgentPicker = false }
        )
    }

    renameChatId?.let { chatId ->
        RenameChatDialog(
            initialTitle = renameInitialTitle,
            onConfirm = { title ->
                viewModel.onEvent(AiUiEvent.RenameChat(chatId, title))
                renameChatId = null
            },
            onDismiss = { renameChatId = null }
        )
    }

    deleteChatId?.let { chatId ->
        ConfirmDialog(
            title = stringResource(R.string.ai_chat_delete),
            text = stringResource(R.string.ai_chat_delete_confirm),
            onConfirm = {
                deleteChatId = null
                viewModel.onEvent(AiUiEvent.DeleteChat(chatId))
            },
            onDismiss = { deleteChatId = null }
        )
    }

    if (showClearConfirm) {
        ConfirmDialog(
            title = stringResource(R.string.ai_chat_clear),
            text = stringResource(R.string.ai_chat_clear_confirm),
            onConfirm = {
                showClearConfirm = false
                viewModel.onEvent(AiUiEvent.ClearMessages)
            },
            onDismiss = { showClearConfirm = false }
        )
    }

    if (showActivity) {
        ActivityLogDialog(
            entries = actionLog,
            onDismiss = { showActivity = false }
        )
    }
}

/**
 * Centered invitation shown while a chat has no messages: active assistant
 * avatar, greeting and suggestion chips that prefill the composer.
 *
 * @param assistant the assistant of the current chat draft.
 * @param onSuggestion prefill the composer with the tapped suggestion.
 */
@Composable
private fun NewChatHint(
    assistant: AiAssistant?,
    onSuggestion: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AssistantAvatar(
            iconName = assistant?.avatarIcon,
            colorIndex = assistant?.avatarColorIndex,
            contentDescription = assistant?.name,
            size = 72.dp
        )
        Text(
            text = stringResource(R.string.ai_empty_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = assistant?.description ?: stringResource(R.string.ai_empty_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 24.dp)
        ) {
            listOf(
                R.string.ai_suggestion_missing_tags,
                R.string.ai_suggestion_duplicates,
                R.string.ai_suggestion_playlist
            ).forEach { res ->
                val label = stringResource(res)
                AssistChip(
                    onClick = { onSuggestion(label) },
                    label = { Text(label) }
                )
            }
        }
    }
}

/**
 * Setup prompt shown when no AI provider is configured yet.
 *
 * @param onNavigateToAiSettings opens the AI settings screen.
 */
@Composable
private fun SetupHint(onNavigateToAiSettings: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp)
        )
        Text(
            text = stringResource(R.string.ai_home_setup_title),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 16.dp)
        )
        Text(
            text = stringResource(R.string.ai_home_setup_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp)
        )
        Button(
            onClick = onNavigateToAiSettings,
            modifier = Modifier.padding(top = 16.dp)
        ) {
            Text(stringResource(R.string.ai_home_setup_action))
        }
    }
}

/**
 * Dialog renaming a chat from the drawer or the chat menu.
 *
 * @param initialTitle the current chat title.
 * @param onConfirm applies the new title.
 * @param onDismiss closes the dialog without changes.
 */
@Composable
private fun RenameChatDialog(
    initialTitle: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var title by remember { mutableStateOf(initialTitle) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_chat_rename)) },
        text = {
            OutlinedTextField(value = title, onValueChange = { title = it }, singleLine = true)
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(title) }) {
                Text(stringResource(R.string.ai_provider_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ai_provider_cancel))
            }
        }
    )
}

/**
 * Audit log dialog listing tool activity of the active chat.
 *
 * @param entries the recorded action log entries.
 * @param onDismiss closes the dialog.
 */
@Composable
private fun ActivityLogDialog(
    entries: List<AiActionLogEntry>,
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
                                overflow = TextOverflow.Ellipsis
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

/**
 * Generic confirmation dialog with a confirm/cancel pair.
 *
 * @param title the dialog title.
 * @param text the confirmation message.
 * @param onConfirm performs the action.
 * @param onDismiss closes the dialog without action.
 */
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
