package com.synth.synthmusic.ui.ai.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.synth.synthmusic.R
import com.synth.synthmusic.domain.model.AiAssistant
import com.synth.synthmusic.ui.ai.ChatListItem
import java.util.concurrent.TimeUnit

/**
 * Content of the AI side drawer: assistant selector, new-chat action, chat
 * history and a link to the AI settings.
 *
 * @param chats the chat history, newest first.
 * @param assistants the available assistant personas.
 * @param activeChatId the currently open chat, null for a fresh draft.
 * @param activeAssistant the assistant shown in the header (chat's or draft's).
 * @param selectedAssistantId the assistant marked selected in the list.
 * @param onNewChat starts a fresh draft.
 * @param onOpenChat opens a history chat.
 * @param onSelectAssistant selects the assistant for the next chat.
 * @param onRenameChat requests renaming the given history chat.
 * @param onDeleteChat requests deleting the given history chat.
 * @param onCreateAssistant opens the assistant editor.
 * @param onNavigateToAiSettings opens the AI settings screen.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AiChatDrawer(
    chats: List<ChatListItem>,
    assistants: List<AiAssistant>,
    activeChatId: Long?,
    activeAssistant: AiAssistant?,
    selectedAssistantId: Long?,
    onNewChat: () -> Unit,
    onOpenChat: (Long) -> Unit,
    onSelectAssistant: (AiAssistant?) -> Unit,
    onRenameChat: (ChatListItem) -> Unit,
    onDeleteChat: (ChatListItem) -> Unit,
    onCreateAssistant: () -> Unit,
    onNavigateToAiSettings: () -> Unit
) {
    var agentsExpanded by remember { mutableStateOf(false) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (agentsExpanded) 180f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "AgentChevronRotation"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(vertical = 8.dp)
    ) {
        AgentHeader(
            assistant = activeAssistant,
            expanded = agentsExpanded,
            chevronRotation = chevronRotation,
            onToggle = { agentsExpanded = !agentsExpanded }
        )
        AnimatedVisibility(
            visible = agentsExpanded,
            enter = expandVertically(tween(durationMillis = 200)) + fadeIn(tween(200)),
            exit = shrinkVertically(tween(durationMillis = 200)) + fadeOut(tween(200))
        ) {
            Column {
                AgentRow(
                    name = stringResource(R.string.ai_agent_general),
                    iconName = null,
                    colorIndex = null,
                    selected = selectedAssistantId == null,
                    onClick = { onSelectAssistant(null) }
                )
                assistants.forEach { assistant ->
                    AgentRow(
                        name = assistant.name,
                        iconName = assistant.avatarIcon,
                        colorIndex = assistant.avatarColorIndex,
                        selected = selectedAssistantId == assistant.id,
                        onClick = { onSelectAssistant(assistant) }
                    )
                }
                AgentRow(
                    name = stringResource(R.string.ai_home_create_assistant),
                    iconName = null,
                    colorIndex = null,
                    selected = false,
                    onClick = onCreateAssistant
                )
            }
        }

        Button(
            onClick = onNewChat,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.ai_drawer_new_chat))
        }

        Text(
            text = stringResource(R.string.ai_drawer_chats),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 4.dp)
        )

        LazyColumn(modifier = Modifier.weight(1f)) {
            if (chats.isEmpty()) {
                item("no_chats") {
                    Text(
                        text = stringResource(R.string.ai_drawer_no_chats),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
            }
            items(items = chats, key = { it.id }) { chat ->
                var menuExpanded by remember { mutableStateOf(false) }
                Box {
                    val isActive = chat.id == activeChatId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 2.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (isActive) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    Color.Transparent
                                }
                            )
                            .combinedClickable(
                                onClick = { onOpenChat(chat.id) },
                                onLongClick = { menuExpanded = true }
                            )
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AssistantAvatar(
                            iconName = chat.assistantIcon,
                            colorIndex = chat.assistantColorIndex,
                            contentDescription = chat.assistantName,
                            size = 32.dp
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = chat.title,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = relativeTime(chat.updatedAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ai_chat_rename)) },
                            onClick = {
                                menuExpanded = false
                                onRenameChat(chat)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.ai_chat_delete)) },
                            onClick = {
                                menuExpanded = false
                                onDeleteChat(chat)
                            }
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNavigateToAiSettings)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.ai_drawer_settings),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

/**
 * Drawer header showing the active assistant; tapping expands the selector.
 */
@Composable
private fun AgentHeader(
    assistant: AiAssistant?,
    expanded: Boolean,
    chevronRotation: Float,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AssistantAvatar(
            iconName = assistant?.avatarIcon,
            colorIndex = assistant?.avatarColorIndex,
            contentDescription = assistant?.name,
            size = 40.dp
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = assistant?.name ?: stringResource(R.string.ai_agent_general),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = assistant?.description
                    ?: stringResource(R.string.ai_agent_general_description),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = Icons.Default.ExpandMore,
            contentDescription = stringResource(
                if (expanded) R.string.cd_hide_assistants else R.string.cd_choose_assistant
            ),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.rotate(chevronRotation)
        )
    }
}

/**
 * One selectable row of the assistant list.
 */
@Composable
private fun AgentRow(
    name: String,
    iconName: String?,
    colorIndex: Int?,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AssistantAvatar(
            iconName = iconName,
            colorIndex = colorIndex,
            contentDescription = name,
            size = 32.dp
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (selected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
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
