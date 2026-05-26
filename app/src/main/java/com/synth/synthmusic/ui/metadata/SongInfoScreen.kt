package com.synth.synthmusic.ui.metadata

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Read-only song metadata detail screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongInfoScreen(
    songId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SongInfoViewModel = koinViewModel { parametersOf(songId) }
) {
    val song by viewModel.song.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Song Info") },
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
            song?.let { s ->
                InfoRow(label = "Title", value = s.title)
                InfoRow(label = "Artist", value = s.artist)
                InfoRow(label = "Album", value = s.album)
                InfoRow(label = "Album Artist", value = s.albumArtist)
                InfoRow(label = "Genre", value = s.genre)
                InfoRow(label = "Year", value = s.year.toString())
                InfoRow(label = "Track Number", value = s.trackNumber.toString())
                InfoRow(label = "Duration", value = "${s.durationMs / 1000}s")
                InfoRow(label = "Bitrate", value = "${s.bitrate} kbps")
                InfoRow(label = "Sample Rate", value = "${s.sampleRate} Hz")
                InfoRow(label = "Path", value = s.path)
                InfoRow(label = "Comment", value = s.comment)
            } ?: Text("Loading...", style = MaterialTheme.typography.bodyLarge)
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
