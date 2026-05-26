package com.synth.synthmusic.ui.settings

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.synth.synthmusic.domain.model.AccentColor
import com.synth.synthmusic.domain.model.ThemeMode
import com.synth.synthmusic.ui.settings.components.SettingDropdown
import com.synth.synthmusic.ui.settings.components.SettingSwitch
import org.koin.androidx.compose.koinViewModel

/**
 * Application settings screen with appearance and playback options.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = koinViewModel()
) {
    val settings by viewModel.settings.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
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
            SettingSwitch(
                title = "Gapless Playback",
                checked = settings.gaplessPlayback,
                onCheckedChange = { viewModel.updateGapless(it) }
            )
            Text(
                text = "Crossfade: ${settings.crossfadeDurationMs}ms",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = 8.dp)
            )
            Slider(
                value = settings.crossfadeDurationMs.toFloat(),
                onValueChange = { viewModel.updateCrossfade(it.toInt()) },
                valueRange = 0f..5000f,
                steps = 9,
                modifier = Modifier.fillMaxWidth()
            )
            SettingSwitch(
                title = "Auto Rescan Library",
                checked = settings.autoRescan,
                onCheckedChange = { viewModel.updateAutoRescan(it) }
            )
            SettingSwitch(
                title = "Equalizer",
                checked = settings.eqEnabled,
                onCheckedChange = { viewModel.updateEqEnabled(it) }
            )
            SettingSwitch(
                title = "Loudness Enhancer",
                checked = settings.loudnessEnabled,
                onCheckedChange = { viewModel.updateLoudness(it) }
            )
            SettingSwitch(
                title = "Skip Silence",
                checked = settings.skipSilence,
                onCheckedChange = { viewModel.updateSkipSilence(it) }
            )

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
