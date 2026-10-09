package com.synth.synthmusic.ui.ai.assistant

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.synth.synthmusic.R
import com.synth.synthmusic.domain.model.AiCapability
import com.synth.synthmusic.domain.model.AiAvatarIcon
import com.synth.synthmusic.domain.usecase.ai.AiToolDescriptor
import com.synth.synthmusic.ui.ai.components.avatarIcon
import com.synth.synthmusic.ui.ai.components.avatarImageFile
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Assistant persona editor: name, icon or custom image, description, system
 * prompt and default per-tool grants. Built-ins are duplicated rather than
 * edited.
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
    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(viewModel::onImagePicked) }

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
                avatarImage = state.avatarImage,
                onSelectIcon = viewModel::selectPresetIcon,
                onPickImage = {
                    pickImage.launch(
                        PickVisualMediaRequest(
                            ActivityResultContracts.PickVisualMedia.ImageOnly
                        )
                    )
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
                text = stringResource(R.string.assistant_default_tools),
                style = MaterialTheme.typography.titleSmall
            )
            AiCapability.entries.forEach { capability ->
                ToolGroup(
                    capability = capability,
                    tools = viewModel.toolCatalog,
                    selectedTools = state.selectedTools,
                    onToggleGroup = { enabled -> viewModel.toggleGroup(capability, enabled) },
                    onToggleTool = viewModel::toggleTool
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
    avatarImage: String?,
    onSelectIcon: (AiAvatarIcon) -> Unit,
    onPickImage: () -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(6),
        modifier = Modifier
            .fillMaxWidth()
            .size(width = 0.dp, height = 168.dp)
    ) {
        item {
            val hasImage = avatarImage != null
            Box(
                modifier = Modifier
                    .padding(6.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (hasImage) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    .then(
                        if (hasImage) {
                            Modifier.border(
                                width = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape
                            )
                        } else {
                            Modifier
                        }
                    )
                    .clickable(onClick = onPickImage),
                contentAlignment = Alignment.Center
            ) {
                if (avatarImage != null) {
                    AsyncImage(
                        model = avatarImageFile(LocalContext.current, avatarImage),
                        contentDescription = stringResource(R.string.assistant_custom_image),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AddPhotoAlternate,
                        contentDescription = stringResource(R.string.assistant_custom_image),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        items(items = AiAvatarIcon.entries.toList()) { icon ->
            val isSelected = avatarImage == null && icon == selected
            Box(
                modifier = Modifier
                    .padding(6.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (isSelected) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                    .clickable { onSelectIcon(icon) },
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
 * One capability group of the per-tool grant switches: a tri-state header
 * (all/none/some) followed by a checkbox row per tool. Tools appear under
 * the first capability they require, so every tool is listed exactly once.
 */
@Composable
private fun ToolGroup(
    capability: AiCapability,
    tools: List<AiToolDescriptor>,
    selectedTools: Set<String>,
    onToggleGroup: (Boolean) -> Unit,
    onToggleTool: (String, Boolean) -> Unit
) {
    val groupTools = remember(tools) {
        tools.filter { tool ->
            AiCapability.entries.firstOrNull { it in tool.requiredGrants } == capability
        }
    }
    if (groupTools.isEmpty()) return
    val selectedCount = groupTools.count { it.name in selectedTools }
    val triState = when (selectedCount) {
        0 -> ToggleableState.Off
        groupTools.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onToggleGroup(triState != ToggleableState.On) }
                .padding(vertical = 2.dp)
        ) {
            TriStateCheckbox(
                state = triState,
                onClick = { onToggleGroup(triState != ToggleableState.On) }
            )
            Text(
                text = capabilityLabel(capability),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        groupTools.forEach { tool ->
            ToolRow(
                tool = tool,
                selected = tool.name in selectedTools,
                onToggle = onToggleTool
            )
        }
    }
}

/**
 * One per-tool grant row: checkbox, tool name and its model-facing
 * description.
 */
@Composable
private fun ToolRow(
    tool: AiToolDescriptor,
    selected: Boolean,
    onToggle: (String, Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(tool.name, !selected) }
            .padding(start = 12.dp)
    ) {
        Checkbox(
            checked = selected,
            onCheckedChange = { onToggle(tool.name, it) }
        )
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(
                text = tool.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = tool.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
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
