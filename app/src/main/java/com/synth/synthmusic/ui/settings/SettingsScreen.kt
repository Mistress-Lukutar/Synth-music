package com.synth.synthmusic.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.synth.synthmusic.domain.model.AccentColor
import com.synth.synthmusic.domain.model.ThemeMode
import com.synth.synthmusic.ui.settings.components.SettingSwitch
import com.synth.synthmusic.ui.settings.components.ThemePickerDialog
import org.koin.androidx.compose.koinViewModel

/**
 * Application settings screen with grouped cards for appearance, playback, library and about.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    showBackButton: Boolean = true,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isScanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val scanError by viewModel.scanError.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showThemeDialog by remember { mutableStateOf(false) }

    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) viewModel.rescanLibrary()
    }

    LaunchedEffect(scanError) {
        scanError?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeScanError()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
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
            // Appearance
            SettingsGroupCard(
                title = "Appearance",
                icon = Icons.Default.Palette
            ) {
                ListItem(
                    headlineContent = { Text("Theme & Colors") },
                    supportingContent = {
                        Text(
                            buildString {
                                append(
                                    settings.theme.name.lowercase().replaceFirstChar { it.uppercase() }
                                )
                                append(" • ")
                                append(
                                    settings.accentColor.name.lowercase()
                                        .replaceFirstChar { it.uppercase() }
                                )
                            }
                        )
                    },
                    leadingContent = {
                        Icon(Icons.Default.Brush, contentDescription = null)
                    },
                    trailingContent = {
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null
                        )
                    },
                    modifier = Modifier.clickable { showThemeDialog = true }
                )
            }

            // Playback
            SettingsGroupCard(
                title = "Playback",
                icon = Icons.Default.GraphicEq
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Crossfade",
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "${settings.fadeDurationMs} ms",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = settings.fadeDurationMs.toFloat(),
                        onValueChange = { viewModel.updateFadeDuration(it.toInt()) },
                        valueRange = 0f..2000f,
                        steps = 19,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                HorizontalDivider()

                SettingSwitch(
                    title = "Skip Silence",
                    description = "Automatically skip silent parts during playback",
                    checked = settings.skipSilence,
                    onCheckedChange = { viewModel.updateSkipSilence(it) },
                    icon = Icons.Default.SkipNext
                )
            }

            // Library
            SettingsGroupCard(
                title = "Library",
                icon = Icons.Default.LibraryMusic
            ) {
                SettingSwitch(
                    title = "Auto Rescan Library",
                    description = "Scan for new music on app startup",
                    checked = settings.autoRescan,
                    onCheckedChange = { viewModel.updateAutoRescan(it) },
                    icon = Icons.Default.Sync
                )

                HorizontalDivider()

                ListItem(
                    headlineContent = { Text("Rescan Library") },
                    supportingContent = { Text("Refresh all tracks and metadata") },
                    leadingContent = {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                    },
                    trailingContent = {
                        if (isScanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.clickable {
                        permissionLauncher.launch(audioPermission)
                    }
                )
            }

            // About
            SettingsGroupCard(
                title = "About",
                icon = Icons.Default.Info
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Synth Music",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "v0.1.0-alpha",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Offline-first music player built with Jetpack Compose, " +
                            "Media3 and Room. Designed for local MP3 collections with rich " +
                            "metadata support, waveform visualizations and custom audio effects.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    val uriHandler = LocalUriHandler.current
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Developed by ",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        TextButton(
                            onClick = { uriHandler.openUri("https://github.com/Mistress-Lukutar/Synth-music/") },
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("Mistress-Lukutar")
                        }
                    }
                }
            }
        }
    }

    if (showThemeDialog) {
        ThemePickerDialog(
            currentTheme = settings.theme,
            currentAccent = settings.accentColor,
            onThemeSelected = { viewModel.updateTheme(it) },
            onAccentSelected = { viewModel.updateAccentColor(it) },
            onDismiss = { showThemeDialog = false }
        )
    }
}

@Composable
private fun SettingsGroupCard(
    title: String,
    icon: ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.padding(
                    start = 16.dp,
                    top = 16.dp,
                    end = 16.dp,
                    bottom = 4.dp
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            content()
        }
    }
}
