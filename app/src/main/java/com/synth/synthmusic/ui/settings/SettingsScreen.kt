package com.synth.synthmusic.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.synth.synthmusic.domain.model.AccentColor
import com.synth.synthmusic.domain.model.ThemeMode
import com.synth.synthmusic.ui.settings.components.SettingDropdown
import com.synth.synthmusic.ui.settings.components.SettingSwitch
import org.koin.androidx.compose.koinViewModel

/**
 * Application settings screen with appearance, playback, and library options.
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

    val audioPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.rescanLibrary()
        }
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
                .padding(16.dp)
        ) {
            SettingSectionHeader("Appearance")
            SettingDropdown(
                title = "Theme",
                options = ThemeMode.entries,
                selected = settings.theme,
                onSelected = { viewModel.updateTheme(it) },
                label = { it.name }
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingDropdown(
                title = "Accent Color",
                options = AccentColor.entries,
                selected = settings.accentColor,
                onSelected = { viewModel.updateAccentColor(it) },
                label = { it.name }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            SettingSectionHeader("Playback")
            Text(
                text = "Fade: ${settings.fadeDurationMs}ms",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp)
            )
            Slider(
                value = settings.fadeDurationMs.toFloat(),
                onValueChange = { viewModel.updateFadeDuration(it.toInt()) },
                valueRange = 0f..2000f,
                steps = 19,
                modifier = Modifier.fillMaxWidth()
            )
            SettingSwitch(
                title = "Auto Rescan Library",
                checked = settings.autoRescan,
                onCheckedChange = { viewModel.updateAutoRescan(it) }
            )
            SettingSwitch(
                title = "Skip Silence",
                checked = settings.skipSilence,
                onCheckedChange = { viewModel.updateSkipSilence(it) }
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            SettingSectionHeader("Library")
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { permissionLauncher.launch(audioPermission) },
                enabled = !isScanning,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Rescan Library")
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

            SettingSectionHeader("About")
            Text(
                text = "Synth Music v1.0",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(vertical = 8.dp)
            )
            Text(
                text = "Offline-first music player built with Jetpack Compose.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingSectionHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(vertical = 8.dp)
    )
}
