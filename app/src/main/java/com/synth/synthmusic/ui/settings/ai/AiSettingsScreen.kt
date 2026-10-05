package com.synth.synthmusic.ui.settings.ai

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.ModelTraining
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.synth.synthmusic.R
import com.synth.synthmusic.domain.model.AiCapability
import com.synth.synthmusic.domain.model.AiProtocol
import com.synth.synthmusic.domain.model.WebSearchProvider
import com.synth.synthmusic.ui.settings.components.SettingSwitch
import com.synth.synthmusic.ui.settings.components.SettingsGroupCard
import org.koin.androidx.compose.koinViewModel

/**
 * AI settings screen: providers, default model, capability kill-switches,
 * optional web search configuration and privacy note.
 *
 * @param onNavigateBack pops the back stack.
 * @param modifier the modifier to be applied to the screen.
 * @param viewModel injected by Koin.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AiSettingsViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val busyProviderId by viewModel.busyProvider.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var editingProvider by remember { mutableStateOf<ProviderUi?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var deletingProvider by remember { mutableStateOf<ProviderUi?>(null) }
    var showModelPicker by remember { mutableStateOf(false) }
    var showWebSearchKeyDialog by remember { mutableStateOf(false) }

    LaunchedEffect(events) {
        events?.let { event ->
            when (event) {
                is AiSettingsEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
            viewModel.consumeEvent()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ai_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- Providers ---
            SettingsGroupCard(
                title = stringResource(R.string.ai_providers_section),
                icon = Icons.Default.AutoAwesome
            ) {
                if (state.providers.isEmpty()) {
                    Text(
                        text = stringResource(R.string.ai_provider_add_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                state.providers.forEach { provider ->
                    ListItem(
                        headlineContent = { Text(provider.label) },
                        supportingContent = {
                            Text(
                                "${provider.protocol.name.replace('_', ' ').lowercase()
                                    .replaceFirstChar { it.uppercase() }} • " +
                                    stringResource(
                                        R.string.ai_provider_models_count, provider.modelCount
                                    )
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Default.Language, contentDescription = null)
                        },
                        trailingContent = {
                            Row {
                                IconButton(onClick = { editingProvider = provider }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                                }
                                IconButton(onClick = { deletingProvider = provider }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete")
                                }
                            }
                        },
                        modifier = Modifier.clickable { editingProvider = provider }
                    )
                }
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.ai_provider_add))
                    },
                    leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                    modifier = Modifier.clickable { showAddDialog = true }
                )
            }

            // --- Default model ---
            SettingsGroupCard(
                title = stringResource(R.string.ai_default_model_section),
                icon = Icons.Default.ModelTraining
            ) {
                val activeLabel = state.models.firstOrNull {
                    it.providerId == state.activeModel?.providerId &&
                        it.model.modelId == state.activeModel?.modelId
                }
                ListItem(
                    headlineContent = {
                        Text(
                            activeLabel?.let { "${it.providerLabel} • ${it.model.displayName}" }
                                ?: stringResource(R.string.ai_default_model_none)
                        )
                    },
                    supportingContent = {
                        Text(stringResource(R.string.ai_default_model_hint))
                    },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.clickable {
                        if (state.models.isNotEmpty()) showModelPicker = true
                    }
                )
            }

            // --- Capabilities ---
            SettingsGroupCard(
                title = stringResource(R.string.ai_capabilities_section),
                icon = Icons.Default.Shield
            ) {
                CapabilityRow(
                    title = stringResource(R.string.ai_capability_read_library),
                    description = stringResource(R.string.ai_capability_read_library_desc),
                    checked = state.capabilities[AiCapability.READ_LIBRARY] ?: true,
                    onCheckedChange = { enabled ->
                        viewModel.onEvent(
                            AiSettingsUiEvent.ToggleCapability(AiCapability.READ_LIBRARY, enabled)
                        )
                    }
                )
                CapabilityRow(
                    title = stringResource(R.string.ai_capability_write_metadata),
                    description = stringResource(R.string.ai_capability_write_metadata_desc),
                    checked = state.capabilities[AiCapability.WRITE_METADATA] ?: true,
                    onCheckedChange = { enabled ->
                        viewModel.onEvent(
                            AiSettingsUiEvent.ToggleCapability(
                                AiCapability.WRITE_METADATA, enabled
                            )
                        )
                    }
                )
                CapabilityRow(
                    title = stringResource(R.string.ai_capability_manage_files),
                    description = stringResource(R.string.ai_capability_manage_files_desc),
                    checked = state.capabilities[AiCapability.MANAGE_FILES] ?: false,
                    onCheckedChange = { enabled ->
                        viewModel.onEvent(
                            AiSettingsUiEvent.ToggleCapability(AiCapability.MANAGE_FILES, enabled)
                        )
                    }
                )
                CapabilityRow(
                    title = stringResource(R.string.ai_capability_internet),
                    description = stringResource(R.string.ai_capability_internet_desc),
                    checked = state.capabilities[AiCapability.INTERNET] ?: false,
                    onCheckedChange = { enabled ->
                        viewModel.onEvent(
                            AiSettingsUiEvent.ToggleCapability(AiCapability.INTERNET, enabled)
                        )
                    }
                )
                Text(
                    text = stringResource(R.string.ai_capabilities_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // --- Web search ---
            SettingsGroupCard(
                title = stringResource(R.string.ai_web_search_section),
                icon = Icons.Default.Public
            ) {
                WebSearchProviderRow(
                    selected = state.webSearchProvider,
                    onSelected = {
                        viewModel.onEvent(AiSettingsUiEvent.SetWebSearchProvider(it))
                    }
                )
                ListItem(
                    headlineContent = {
                        Text(
                            if (state.webSearchKeyConfigured) {
                                stringResource(R.string.ai_web_search_key_set)
                            } else {
                                stringResource(R.string.ai_web_search_key)
                            }
                        )
                    },
                    leadingContent = { Icon(Icons.Default.Key, contentDescription = null) },
                    modifier = Modifier.clickable { showWebSearchKeyDialog = true }
                )
                Text(
                    text = stringResource(R.string.ai_web_search_key_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            // --- Trash ---
            SettingsGroupCard(
                title = stringResource(R.string.ai_trash_section),
                icon = Icons.Default.Delete
            ) {
                ListItem(
                    headlineContent = {
                        Text(
                            stringResource(R.string.ai_trash_count, state.trashCount)
                        )
                    },
                    supportingContent = { Text(state.trashPath) },
                    trailingContent = {
                        TextButton(
                            onClick = {
                                viewModel.onEvent(AiSettingsUiEvent.EmptyTrash)
                            },
                            enabled = state.trashCount > 0
                        ) {
                            Text(stringResource(R.string.ai_trash_empty))
                        }
                    }
                )
                state.trashEntries.take(5).forEach { entry ->
                    ListItem(
                        headlineContent = {
                            Text(entry.fileName, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        },
                        trailingContent = {
                            TextButton(onClick = {
                                viewModel.onEvent(AiSettingsUiEvent.RestoreTrashEntry(entry))
                            }) {
                                Text(stringResource(R.string.ai_trash_restore))
                            }
                        }
                    )
                }
            }

            // --- Activity log ---
            SettingsGroupCard(
                title = stringResource(R.string.ai_activity_section),
                icon = Icons.Default.History
            ) {
                if (state.actionLog.isEmpty()) {
                    Text(
                        text = stringResource(R.string.ai_chat_activity_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                state.actionLog.take(10).forEach { entry ->
                    ListItem(
                        headlineContent = { Text("${entry.toolName} • ${entry.outcome.name.lowercase()}") },
                        supportingContent = { Text(entry.summary, maxLines = 2) }
                    )
                }
            }

            // --- Privacy note ---
            Text(
                text = stringResource(R.string.ai_privacy_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    // --- Dialogs ---
    if (showAddDialog || editingProvider != null) {
        ProviderEditorDialog(
            provider = editingProvider,
            busy = busyProviderId != null,
            onSave = { label, protocol, baseUrl, apiKey ->
                viewModel.onEvent(
                    AiSettingsUiEvent.SaveProvider(
                        editingProvider?.id, label, protocol, baseUrl, apiKey
                    )
                )
                showAddDialog = false
                editingProvider = null
            },
            onFetchModels = { editingProvider?.let { viewModel.onEvent(
                AiSettingsUiEvent.FetchModels(it.id)
            ) } },
            onTestConnection = { editingProvider?.let { viewModel.onEvent(
                AiSettingsUiEvent.TestConnection(it.id)
            ) } },
            onDismiss = {
                showAddDialog = false
                editingProvider = null
                viewModel.refresh()
            }
        )
    }

    deletingProvider?.let { provider ->
        AlertDialog(
            onDismissRequest = { deletingProvider = null },
            title = { Text(stringResource(R.string.ai_provider_delete_title)) },
            text = { Text(stringResource(R.string.ai_provider_delete_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onEvent(AiSettingsUiEvent.DeleteProvider(provider.id))
                    deletingProvider = null
                }) {
                    Text(stringResource(R.string.ai_provider_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingProvider = null }) {
                    Text(stringResource(R.string.ai_provider_cancel))
                }
            }
        )
    }

    if (showModelPicker) {
        ModelPickerDialog(
            models = state.models,
            active = state.activeModel,
            onSelect = { providerId, modelId ->
                viewModel.onEvent(AiSettingsUiEvent.SetDefaultModel(providerId, modelId))
                showModelPicker = false
            },
            onDismiss = { showModelPicker = false }
        )
    }

    if (showWebSearchKeyDialog) {
        WebSearchKeyDialog(
            onConfirm = { key ->
                viewModel.onEvent(AiSettingsUiEvent.SetWebSearchKey(key))
                showWebSearchKeyDialog = false
            },
            onDismiss = { showWebSearchKeyDialog = false }
        )
    }
}

@Composable
private fun CapabilityRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingSwitch(
        title = title,
        description = description,
        checked = checked,
        onCheckedChange = onCheckedChange
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WebSearchProviderRow(
    selected: WebSearchProvider,
    onSelected: (WebSearchProvider) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        OutlinedTextField(
            value = selected.name.lowercase().replaceFirstChar { it.uppercase() },
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(R.string.ai_web_search_provider)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            WebSearchProvider.entries.forEach { provider ->
                DropdownMenuItem(
                    text = {
                        Text(provider.name.lowercase().replaceFirstChar { it.uppercase() })
                    },
                    onClick = {
                        onSelected(provider)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderEditorDialog(
    provider: ProviderUi?,
    busy: Boolean,
    onSave: (label: String, protocol: AiProtocol, baseUrl: String, apiKey: String) -> Unit,
    onFetchModels: () -> Unit,
    onTestConnection: () -> Unit,
    onDismiss: () -> Unit
) {
    var label by remember { mutableStateOf(provider?.label ?: "") }
    var protocol by remember { mutableStateOf(provider?.protocol ?: AiProtocol.OPENAI_COMPATIBLE) }
    var baseUrl by remember {
        mutableStateOf(provider?.baseUrl ?: AiProtocol.OPENAI_COMPATIBLE.defaultBaseUrl)
    }
    var apiKey by remember { mutableStateOf("") }
    var protocolExpanded by remember { mutableStateOf(false) }

    val valid = label.isNotBlank() && baseUrl.isNotBlank() &&
        (provider != null || apiKey.isNotBlank())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (provider == null) R.string.ai_provider_add
                    else R.string.ai_provider_edit
                )
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text(stringResource(R.string.ai_provider_label)) },
                    singleLine = true
                )
                ExposedDropdownMenuBox(
                    expanded = protocolExpanded,
                    onExpandedChange = { protocolExpanded = it }
                ) {
                    OutlinedTextField(
                        value = protocol.name.replace('_', ' ').lowercase()
                            .replaceFirstChar { it.uppercase() },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.ai_provider_protocol)) },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = protocolExpanded)
                        },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = protocolExpanded,
                        onDismissRequest = { protocolExpanded = false }
                    ) {
                        AiProtocol.entries.forEach { entry ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        entry.name.replace('_', ' ').lowercase()
                                            .replaceFirstChar { it.uppercase() }
                                    )
                                },
                                onClick = {
                                    protocol = entry
                                    if (provider == null) baseUrl = entry.defaultBaseUrl
                                    protocolExpanded = false
                                }
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = { baseUrl = it },
                    label = { Text(stringResource(R.string.ai_provider_base_url)) },
                    singleLine = true
                )
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = {
                        Text(
                            if (provider == null) {
                                stringResource(R.string.ai_provider_api_key)
                            } else {
                                provider.maskedKey
                                    ?: stringResource(R.string.ai_provider_api_key)
                            }
                        )
                    },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onFetchModels,
                        enabled = !busy
                    ) {
                        if (busy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                        }
                        Text(stringResource(R.string.ai_provider_fetch_models))
                    }
                    TextButton(onClick = onTestConnection, enabled = !busy) {
                        Text(stringResource(R.string.ai_provider_test_connection))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(label, protocol, baseUrl, apiKey) },
                enabled = valid
            ) {
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

@Composable
private fun ModelPickerDialog(
    models: List<ModelUi>,
    active: com.synth.synthmusic.domain.model.ActiveModelRef?,
    onSelect: (Long, String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_default_model_pick)) },
        text = {
            LazyColumn(
                modifier = Modifier.height(360.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                val grouped = models.groupBy { it.providerLabel }
                grouped.forEach { (providerLabel, providerModels) ->
                    item(key = "header_$providerLabel") {
                        Text(
                            text = providerLabel,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                    }
                    items(
                        items = providerModels,
                        key = { "${it.providerId}_${it.model.modelId}" }
                    ) { entry ->
                        val selected = active?.providerId == entry.providerId &&
                            active?.modelId == entry.model.modelId
                        ListItem(
                            headlineContent = { Text(entry.model.displayName) },
                            supportingContent = { Text(entry.model.modelId) },
                            trailingContent = {
                                if (selected) {
                                    Icon(
                                        Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            modifier = Modifier.clickable {
                                onSelect(entry.providerId, entry.model.modelId)
                            }
                        )
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
private fun WebSearchKeyDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var key by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ai_web_search_key)) },
        text = {
            OutlinedTextField(
                value = key,
                onValueChange = { key = it },
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(key) },
                enabled = key.isNotBlank()
            ) {
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
