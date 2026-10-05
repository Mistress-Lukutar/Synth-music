package com.synth.synthmusic.ui.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.synth.synthmusic.R
import org.koin.androidx.compose.koinViewModel
import java.util.concurrent.TimeUnit

/**
 * AI chat home: search, new chat action and the recent chats list. Shows a
 * setup hint when no provider is configured yet.
 *
 * @param onNavigateToAiSettings opens the AI settings screen.
 * @param onOpenChat navigates to the chat screen for the given chat id.
 * @param onNavigateToAssistantEditor opens the assistant editor (null = create).
 * @param modifier the modifier to be applied to the screen.
 * @param viewModel injected by Koin.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiScreen(
    onNavigateToAiSettings: () -> Unit,
    onOpenChat: (Long) -> Unit,
    onNavigateToAssistantEditor: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AiHomeViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val createdChatId by viewModel.createdChatId.collectAsStateWithLifecycle()
    var showAssistantPicker by remember { mutableStateOf(false) }

    LaunchedEffect(createdChatId) {
        createdChatId?.let { id ->
            viewModel.consumeCreatedChat()
            onOpenChat(id)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("AI") },
                actions = {
                    IconButton(onClick = onNavigateToAiSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAssistantPicker = true }) {
                Icon(Icons.Default.Add, contentDescription = "New chat")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text(stringResource(R.string.ai_home_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            if (!state.hasProvider) {
                SetupHint(onNavigateToAiSettings = onNavigateToAiSettings)
            } else if (state.chats.isEmpty()) {
                EmptyChats()
            } else {
                LazyColumn {
                    items(items = state.chats, key = { it.id }) { chat ->
                        ListItem(
                            headlineContent = { Text(chat.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = {
                                Text(
                                    listOfNotNull(
                                        chat.assistantName,
                                        relativeTime(chat.updatedAt)
                                    ).joinToString(" • "),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingContent = {
                                Icon(
                                    Icons.AutoMirrored.Filled.Chat,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            },
                            modifier = Modifier.clickable { onOpenChat(chat.id) }
                        )
                    }
                }
            }
        }
    }

    if (showAssistantPicker) {
        AssistantPickerSheet(
            assistants = state.assistants,
            onAssistantSelected = { assistant ->
                showAssistantPicker = false
                viewModel.createChat(assistant)
            },
            onCreateCustom = {
                showAssistantPicker = false
                onNavigateToAssistantEditor(null)
            },
            onDismiss = { showAssistantPicker = false }
        )
    }
}

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

@Composable
private fun EmptyChats() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.ai_home_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssistantPickerSheet(
    assistants: List<com.synth.synthmusic.domain.model.AiAssistant>,
    onAssistantSelected: (com.synth.synthmusic.domain.model.AiAssistant?) -> Unit,
    onCreateCustom: () -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.ai_home_pick_assistant),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        assistants.forEach { assistant ->
            ListItem(
                headlineContent = { Text(assistant.name) },
                supportingContent = { Text(assistant.description, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                leadingContent = {
                    com.synth.synthmusic.ui.ai.components.AssistantAvatar(assistant)
                },
                modifier = Modifier.clickable { onAssistantSelected(assistant) }
            )
        }
        ListItem(
            headlineContent = { Text(stringResource(R.string.ai_home_create_assistant)) },
            leadingContent = {
                Icon(Icons.Default.Add, contentDescription = null)
            },
            modifier = Modifier.clickable onCreate@{
                onCreateCustom()
            }
        )
    }
}

/**
 * Formats a timestamp as a short relative label ("now", "5m", "3h", "2d").
 */
private fun relativeTime(timestamp: Long): String {
    val diffMinutes = TimeUnit.MILLISECONDS.toMinutes(System.currentTimeMillis() - timestamp)
    return when {
        diffMinutes < 1 -> "now"
        diffMinutes < 60 -> "${diffMinutes}m"
        diffMinutes < 24 * 60 -> "${diffMinutes / 60}h"
        else -> "${diffMinutes / (24 * 60)}d"
    }
}
