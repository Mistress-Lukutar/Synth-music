package com.synth.synthmusic.ui.settings.ai.customtools

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.synth.synthmusic.R
import com.synth.synthmusic.data.ai.customtools.CustomToolRecord
import com.synth.synthmusic.ui.settings.components.SettingsGroupCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.androidx.compose.koinViewModel

/**
 * Management screen for user-imported custom AI tools: import via SAF,
 * enable/disable, secrets, test runs and deletion.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun CustomToolsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CustomToolsViewModel = koinViewModel()
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var detailsTool by remember { mutableStateOf<CustomToolRecord?>(null) }
    var deletingTool by remember { mutableStateOf<CustomToolRecord?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val bytes = withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    }.getOrNull()
                }
                val name = uri.lastPathSegment?.substringAfterLast('/') ?: "tool"
                if (bytes != null) viewModel.import(bytes, name)
            }
        }
    }

    LaunchedEffect(events) {
        events?.let { event ->
            when (event) {
                is CustomToolsEvent.Message -> snackbarHostState.showSnackbar(event.text)
            }
            viewModel.consumeEvent()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.ai_custom_tools_title)) },
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
            Text(
                text = stringResource(R.string.ai_custom_tools_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )

            SettingsGroupCard(
                title = stringResource(R.string.ai_custom_tools_section),
                icon = Icons.Default.Extension
            ) {
                if (state.records.isEmpty() && !state.isLoading) {
                    Text(
                        text = stringResource(R.string.ai_custom_tools_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
                state.records.forEach { record ->
                    ListItem(
                        headlineContent = { Text(record.displayName) },
                        supportingContent = {
                            Column {
                                Text(record.name, fontFamily = FontFamily.Monospace)
                                Text(
                                    listOf(
                                        record.grants.joinToString { it.name.lowercase() },
                                        if (record.isScript) "script" else "http",
                                        record.confirmationLevel.name.lowercase()
                                    ).joinToString(" • "),
                                    style = MaterialTheme.typography.bodySmall
                                )
                                record.loadError?.let { error ->
                                    Text(
                                        text = "${stringResource(R.string.ai_custom_tools_load_error)}: $error",
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        },
                        trailingContent = {
                            Switch(
                                checked = record.enabled,
                                onCheckedChange = { enabled ->
                                    viewModel.setEnabled(record.name, enabled)
                                }
                            )
                        },
                        modifier = Modifier.clickable { detailsTool = record }
                    )
                }
                ListItem(
                    headlineContent = {
                        Text(stringResource(R.string.ai_custom_tools_import))
                    },
                    leadingContent = { Icon(Icons.Default.Add, contentDescription = null) },
                    modifier = Modifier.clickable {
                        importLauncher.launch(
                            arrayOf(
                                "application/json",
                                "application/zip",
                                "application/x-zip-compressed",
                                "application/octet-stream"
                            )
                        )
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
        }
    }

    // Close the details dialog when its tool disappears (deleted).
    LaunchedEffect(detailsTool, state.records) {
        val record = detailsTool
        if (record != null && state.records.none { it.name == record.name }) {
            detailsTool = null
        }
    }

    detailsTool?.let { record ->
        state.records.firstOrNull { it.name == record.name }?.let { current ->
            ToolDetailsDialog(
                record = current,
                state = state,
                onSetSecret = { secretName, value ->
                    viewModel.setSecret(record.name, secretName, value)
                },
                onRunTest = { args -> viewModel.runTest(record.name, args) },
                onDelete = {
                    deletingTool = record
                },
                onDismiss = {
                    viewModel.clearTestOutput()
                    detailsTool = null
                }
            )
        }
    }

    deletingTool?.let { record ->
        AlertDialog(
            onDismissRequest = { deletingTool = null },
            title = { Text(stringResource(R.string.ai_custom_tools_delete)) },
            text = {
                Text(
                    stringResource(
                        R.string.ai_custom_tools_delete_confirm, record.displayName
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(record.name)
                    deletingTool = null
                    viewModel.clearTestOutput()
                    detailsTool = null
                }) {
                    Text(stringResource(R.string.ai_custom_tools_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingTool = null }) {
                    Text(stringResource(R.string.ai_custom_tools_close))
                }
            }
        )
    }
}

/**
 * Details dialog for one tool: description, schema, secret inputs, an
 * explicit test run and the delete action.
 */
@Composable
private fun ToolDetailsDialog(
    record: CustomToolRecord,
    state: CustomToolsUiState,
    onSetSecret: (secretName: String, value: String) -> Unit,
    onRunTest: (argsJson: String) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    var argsText by remember(record.name) {
        mutableStateOf(exampleArgs(record))
    }
    val storedSecrets = state.storedSecrets[record.name].orEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(record.displayName) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    record.name,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall
                )
                record.author?.let { author ->
                    Text(
                        "by $author",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (record.description.isNotBlank()) {
                    Text(record.description, style = MaterialTheme.typography.bodyMedium)
                }
                Text(
                    listOf(
                        record.grants.joinToString { it.name.lowercase() },
                        record.confirmationLevel.name.lowercase()
                    ).joinToString(" • "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                record.loadError?.let { error ->
                    Text(
                        text = "${stringResource(R.string.ai_custom_tools_load_error)}: $error",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                HorizontalDivider()

                Text(
                    text = stringResource(R.string.ai_custom_tools_schema),
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    text = record.paramsSchemaJson,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (record.secretRefs.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        text = stringResource(R.string.ai_custom_tools_secret_hint),
                        style = MaterialTheme.typography.titleSmall
                    )
                    record.secretRefs.forEach { secretName ->
                        var value by remember(record.name, secretName) { mutableStateOf("") }
                        Row(
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = value,
                                onValueChange = { value = it },
                                label = {
                                    Text(
                                        if (secretName in storedSecrets) {
                                            "$secretName (set)"
                                        } else {
                                            secretName
                                        }
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    onSetSecret(secretName, value)
                                    value = ""
                                },
                                enabled = value.isNotBlank()
                            ) {
                                Text(stringResource(R.string.ai_custom_tools_secret_save))
                            }
                        }
                    }
                }

                HorizontalDivider()

                Text(
                    text = stringResource(R.string.ai_custom_tools_test),
                    style = MaterialTheme.typography.titleSmall
                )
                OutlinedTextField(
                    value = argsText,
                    onValueChange = { argsText = it },
                    label = { Text(stringResource(R.string.ai_custom_tools_args_label)) },
                    minLines = 2,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )
                TextButton(
                    onClick = { onRunTest(argsText) },
                    enabled = !state.isTestRunning && record.loadError == null
                ) {
                    Text(stringResource(R.string.ai_custom_tools_run))
                }
                state.testOutput?.let { output ->
                    Text(
                        text = output,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = if (output.startsWith("ERROR")) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
                Text(
                    text = stringResource(R.string.ai_custom_tools_delete),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ai_custom_tools_close))
            }
        }
    )
}

/** Builds a small example argument object from the schema's properties. */
private fun exampleArgs(record: CustomToolRecord): String = try {
    val schema = kotlinx.serialization.json.Json.parseToJsonElement(record.paramsSchemaJson)
        as? kotlinx.serialization.json.JsonObject
    val properties = schema?.get("properties") as? kotlinx.serialization.json.JsonObject
    val example = kotlinx.serialization.json.buildJsonObject {
        properties?.forEach { (name, spec) ->
            val type = (spec as? kotlinx.serialization.json.JsonObject)
                ?.get("type")?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
            put(
                name,
                when (type) {
                    "integer", "number" -> kotlinx.serialization.json.JsonPrimitive(1)
                    "boolean" -> kotlinx.serialization.json.JsonPrimitive(true)
                    else -> kotlinx.serialization.json.JsonPrimitive("")
                }
            )
        }
    }
    example.toString()
} catch (e: Exception) {
    "{}"
}
