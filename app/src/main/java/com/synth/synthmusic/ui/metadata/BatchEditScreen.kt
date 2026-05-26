package com.synth.synthmusic.ui.metadata

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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Batch metadata editor screen.
 *
 * Allows the user to select which fields to overwrite and apply
 * the same values across multiple songs at once.
 *
 * @param songIds the list of song identifiers to edit.
 * @param onNavigateBack callback invoked when the user navigates back.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BatchEditScreen(
    songIds: List<String>,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BatchEditViewModel = koinViewModel { parametersOf(songIds) }
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Batch Edit (${uiState.songs.size} songs)") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            viewModel.onEvent(BatchEditEvent.Save)
                            onNavigateBack()
                        },
                        enabled = !uiState.isSaving
                    ) {
                        Text("Save")
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
            if (uiState.isSaving) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(modifier = Modifier.height(16.dp))
            }

            Text(
                text = "Check the fields you want to overwrite, then enter the new value.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            BatchFieldRow(
                label = "Title",
                checked = uiState.title.enabled,
                value = uiState.title.value,
                onCheckedChange = { viewModel.onEvent(BatchEditEvent.ToggleField(MetadataField.TITLE, it)) },
                onValueChange = { viewModel.onEvent(BatchEditEvent.UpdateValue(MetadataField.TITLE, it)) }
            )
            BatchFieldRow(
                label = "Artist",
                checked = uiState.artist.enabled,
                value = uiState.artist.value,
                onCheckedChange = { viewModel.onEvent(BatchEditEvent.ToggleField(MetadataField.ARTIST, it)) },
                onValueChange = { viewModel.onEvent(BatchEditEvent.UpdateValue(MetadataField.ARTIST, it)) }
            )
            BatchFieldRow(
                label = "Album",
                checked = uiState.album.enabled,
                value = uiState.album.value,
                onCheckedChange = { viewModel.onEvent(BatchEditEvent.ToggleField(MetadataField.ALBUM, it)) },
                onValueChange = { viewModel.onEvent(BatchEditEvent.UpdateValue(MetadataField.ALBUM, it)) }
            )
            BatchFieldRow(
                label = "Album Artist",
                checked = uiState.albumArtist.enabled,
                value = uiState.albumArtist.value,
                onCheckedChange = { viewModel.onEvent(BatchEditEvent.ToggleField(MetadataField.ALBUM_ARTIST, it)) },
                onValueChange = { viewModel.onEvent(BatchEditEvent.UpdateValue(MetadataField.ALBUM_ARTIST, it)) }
            )
            BatchFieldRow(
                label = "Genre",
                checked = uiState.genre.enabled,
                value = uiState.genre.value,
                onCheckedChange = { viewModel.onEvent(BatchEditEvent.ToggleField(MetadataField.GENRE, it)) },
                onValueChange = { viewModel.onEvent(BatchEditEvent.UpdateValue(MetadataField.GENRE, it)) }
            )
            BatchFieldRow(
                label = "Year",
                checked = uiState.year.enabled,
                value = uiState.year.value,
                onCheckedChange = { viewModel.onEvent(BatchEditEvent.ToggleField(MetadataField.YEAR, it)) },
                onValueChange = { viewModel.onEvent(BatchEditEvent.UpdateValue(MetadataField.YEAR, it)) }
            )
            BatchFieldRow(
                label = "Track Number",
                checked = uiState.trackNumber.enabled,
                value = uiState.trackNumber.value,
                onCheckedChange = { viewModel.onEvent(BatchEditEvent.ToggleField(MetadataField.TRACK_NUMBER, it)) },
                onValueChange = { viewModel.onEvent(BatchEditEvent.UpdateValue(MetadataField.TRACK_NUMBER, it)) }
            )
            BatchFieldRow(
                label = "Comment",
                checked = uiState.comment.enabled,
                value = uiState.comment.value,
                onCheckedChange = { viewModel.onEvent(BatchEditEvent.ToggleField(MetadataField.COMMENT, it)) },
                onValueChange = { viewModel.onEvent(BatchEditEvent.UpdateValue(MetadataField.COMMENT, it)) }
            )
        }
    }
}

@Composable
private fun BatchFieldRow(
    label: String,
    checked: Boolean,
    value: String,
    onCheckedChange: (Boolean) -> Unit,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
            enabled = checked,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
