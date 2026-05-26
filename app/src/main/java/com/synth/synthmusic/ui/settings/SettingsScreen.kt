package com.synth.synthmusic.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.synth.synthmusic.domain.model.AccentColor
import com.synth.synthmusic.domain.model.ThemeMode
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
            SettingRadioGroup(
                title = "Theme",
                options = ThemeMode.entries,
                selected = settings.theme,
                onSelected = { viewModel.updateTheme(it) },
                label = { it.name }
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingRadioGroup(
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
                steps = 9, // 0, 500, 1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500, 5000
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

@Composable
private fun <T> SettingRadioGroup(
    title: String,
    options: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(
                        selected = option == selected,
                        onClick = { onSelected(option) },
                        role = Role.RadioButton
                    )
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = option == selected,
                    onClick = null
                )
                Text(
                    text = label(option),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
