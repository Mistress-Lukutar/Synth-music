package com.synth.synthmusic.ui.ai.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.synth.synthmusic.R
import com.synth.synthmusic.domain.model.AiAssistant

/**
 * Bottom sheet listing the available assistants plus the neutral General
 * entry and a shortcut to create a custom one. Selecting an assistant
 * always starts a new chat draft with that persona. Long-pressing an
 * assistant opens a management menu: custom assistants can be edited or
 * deleted; built-ins can only be duplicated via the editor.
 *
 * @param assistants the available assistant personas.
 * @param selectedAssistantId the assistant pre-selected for new chats.
 * @param onAssistantSelected called with the chosen assistant (null = General).
 * @param onEditAssistant opens the assistant editor for the given assistant.
 * @param onDeleteAssistant asks the caller to confirm deleting the assistant.
 * @param onCreateCustom opens the assistant editor.
 * @param onDismiss closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantPickerSheet(
    assistants: List<AiAssistant>,
    selectedAssistantId: Long?,
    onAssistantSelected: (AiAssistant?) -> Unit,
    onEditAssistant: (AiAssistant) -> Unit,
    onDeleteAssistant: (AiAssistant) -> Unit,
    onCreateCustom: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            text = stringResource(R.string.ai_home_pick_assistant),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        AssistantSheetRow(
            name = stringResource(R.string.ai_agent_general),
            description = stringResource(R.string.ai_agent_general_description),
            iconName = null,
            colorIndex = null,
            selected = selectedAssistantId == null,
            onClick = { onAssistantSelected(null) }
        )
        assistants.forEach { assistant ->
            AssistantSheetRow(
                name = assistant.name,
                description = assistant.description,
                iconName = assistant.avatarIcon,
                colorIndex = assistant.avatarColorIndex,
                selected = selectedAssistantId == assistant.id,
                onClick = { onAssistantSelected(assistant) },
                avatarImage = assistant.avatarImage,
                assistant = assistant,
                onEditAssistant = onEditAssistant,
                onDeleteAssistant = onDeleteAssistant
            )
        }
        ListItem(
            headlineContent = { Text(stringResource(R.string.ai_home_create_assistant)) },
            leadingContent = {
                Icon(Icons.Default.Add, contentDescription = null)
            },
            modifier = Modifier.clickable(onClick = onCreateCustom)
        )
    }
}

/**
 * One selectable assistant row of the picker sheet. Non-null [assistant]
 * enables the long-press management menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AssistantSheetRow(
    name: String,
    description: String,
    iconName: String?,
    colorIndex: Int?,
    selected: Boolean,
    onClick: () -> Unit,
    avatarImage: String? = null,
    assistant: AiAssistant? = null,
    onEditAssistant: ((AiAssistant) -> Unit)? = null,
    onDeleteAssistant: ((AiAssistant) -> Unit)? = null
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        ListItem(
            headlineContent = { Text(name) },
            supportingContent = {
                Text(description, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            leadingContent = {
                AssistantAvatar(
                    iconName = iconName,
                    colorIndex = colorIndex,
                    contentDescription = name,
                    avatarImage = avatarImage
                )
            },
            trailingContent = if (selected) {
                {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                null
            },
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = if (assistant != null) {
                        { menuExpanded = true }
                    } else {
                        null
                    }
                )
        )
        if (assistant != null) {
            AssistantMenu(
                assistant = assistant,
                expanded = menuExpanded,
                onDismiss = { menuExpanded = false },
                onEditAssistant = onEditAssistant,
                onDeleteAssistant = onDeleteAssistant
            )
        }
    }
}

/**
 * Long-press menu of one assistant row: edit and delete for custom
 * assistants, duplicate-only for built-ins (the editor then saves a copy).
 */
@Composable
fun AssistantMenu(
    assistant: AiAssistant,
    expanded: Boolean,
    onDismiss: () -> Unit,
    onEditAssistant: ((AiAssistant) -> Unit)?,
    onDeleteAssistant: ((AiAssistant) -> Unit)?
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        if (assistant.isBuiltin) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.ai_agent_duplicate)) },
                onClick = {
                    onDismiss()
                    onEditAssistant?.invoke(assistant)
                }
            )
        } else {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.ai_agent_edit)) },
                onClick = {
                    onDismiss()
                    onEditAssistant?.invoke(assistant)
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.ai_agent_delete)) },
                onClick = {
                    onDismiss()
                    onDeleteAssistant?.invoke(assistant)
                }
            )
        }
    }
}
