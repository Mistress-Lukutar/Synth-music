package com.synth.synthmusic.ui.ai.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.synth.synthmusic.R
import com.synth.synthmusic.domain.model.AiAssistant

/**
 * Bottom sheet listing the available assistants plus the neutral General
 * entry and a shortcut to create a custom one. Selecting an assistant
 * always starts a new chat draft with that persona.
 *
 * @param assistants the available assistant personas.
 * @param selectedAssistantId the assistant pre-selected for new chats.
 * @param onAssistantSelected called with the chosen assistant (null = General).
 * @param onCreateCustom opens the assistant editor.
 * @param onDismiss closes the sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantPickerSheet(
    assistants: List<AiAssistant>,
    selectedAssistantId: Long?,
    onAssistantSelected: (AiAssistant?) -> Unit,
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
                onClick = { onAssistantSelected(assistant) }
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
 * One selectable assistant row of the picker sheet.
 */
@Composable
private fun AssistantSheetRow(
    name: String,
    description: String,
    iconName: String?,
    colorIndex: Int?,
    selected: Boolean,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(name) },
        supportingContent = {
            Text(description, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingContent = {
            AssistantAvatar(
                iconName = iconName,
                colorIndex = colorIndex,
                contentDescription = name
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
            .clickable(onClick = onClick)
    )
}
