package com.synth.synthmusic.ui.ai.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import com.synth.synthmusic.domain.model.AiCapability
import com.synth.synthmusic.domain.model.AiAvatarIcon
import com.synth.synthmusic.ui.ai.components.avatarIcon
import com.synth.synthmusic.ui.settings.components.SettingSwitch
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Assistant persona editor: name, icon, description, system prompt and
 * default capability grants. Built-ins are duplicated rather than edited.
 *
 * @param assistantId id of the persona to edit, null to create a new one.
 * @param onNavigateBack pops the back stack.
 * @param modifier the modifier to be applied to the screen.
 * @param viewModel injected by Koin with the [assistantId] nav argument.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantEditorScreen(
    assistantId: Long?,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AssistantEditorViewModel = koinViewModel { parametersOf(assistantId) }
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val finished by viewModel.finished.collectAsStateWithLifecycle()
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(finished) {
        if (finished) {
            viewModel.consumeFinished()
            onNavigateBack()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (state.assistantId == null) R.string.assistant_editor_new
                            else R.string.assistant_editor_title
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.assistantId != null && !state.isBuiltin) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.isBuiltin) {
                Text(
                    text = stringResource(R.string.assistant_builtin_notice),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            OutlinedTextField(
                value = state.name,
                onValueChange = { name -> viewModel.update { it.copy(name = name) } },
                label = { Text(stringResource(R.string.assistant_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = state.description,
                onValueChange = { description ->
                    viewModel.update { it.copy(description = description) }
                },
                label = { Text(stringResource(R.string.assistant_description)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = stringResource(R.string.assistant_icon),
                style = MaterialTheme.typography.titleSmall
            )
            IconGrid(
                selected = state.avatarIcon,
                colorIndex = state.avatarColorIndex,
                onSelect = { icon ->
                    viewModel.update {
                        it.copy(avatarIcon = icon, avatarColorIndex = icon.ordinal % 6)
                    }
                }
            )

            OutlinedTextField(
                value = state.systemPrompt,
                onValueChange = { prompt -> viewModel.update { it.copy(systemPrompt = prompt) } },
                label = { Text(stringResource(R.string.assistant_system_prompt)) },
                placeholder = { Text(stringResource(R.string.assistant_system_prompt_hint)) },
                minLines = 4,
                maxLines = 10,
                modifier = Modifier.fillMaxWidth()
            )

            Text(
                text = stringResource(R.string.assistant_default_grants),
                style = MaterialTheme.typography.titleSmall
            )
            AiCapability.entries.forEach { capability ->
                SettingSwitch(
                    title = capabilityLabel(capability),
                    description = "",
                    checked = capability in state.grants,
                    onCheckedChange = { viewModel.toggleGrant(capability, it) }
                )
            }

            Button(
                onClick = { viewModel.save() },
                enabled = state.name.isNotBlank() && state.isLoaded,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    stringResource(
                        if (state.isBuiltin) R.string.assistant_save_copy
                        else R.string.assistant_save
                    )
                )
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.assistant_delete)) },
            text = { Text(stringResource(R.string.assistant_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    viewModel.delete()
                }) { Text(stringResource(R.string.assistant_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.ai_provider_cancel))
                }
            }
        )
    }
}

@Composable
private fun IconGrid(
    selected: AiAvatarIcon,
    colorIndex: Int,
    onSelect: (AiAvatarIcon) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        modifier = Modifier
            .fillMaxWidth()
            .size(width = 0.dp, height = 168.dp)
    ) {
        items(items = AiAvatarIcon.entries.toList()) { icon ->
            val isSelected = icon == selected
            Box(
                modifier = Modifier
                    .padding(6.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    .clickable { onSelect(icon) },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = avatarIcon(icon.name),
                    contentDescription = icon.name,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Returns the localized label of a capability for the editor switches.
 */
@Composable
fun capabilityLabel(capability: AiCapability): String = when (capability) {
    AiCapability.READ_LIBRARY -> stringResource(R.string.ai_capability_read_library)
    AiCapability.WRITE_METADATA -> stringResource(R.string.ai_capability_write_metadata)
    AiCapability.MANAGE_FILES -> stringResource(R.string.ai_capability_manage_files)
    AiCapability.INTERNET -> stringResource(R.string.ai_capability_internet)
}
