package com.synth.synthmusic.ui.metadata

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.Tab
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.ui.metadata.components.ArtworkPicker
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
    val song by viewModel.song.collectAsStateWithLifecycle()

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
        ) {
            val tabs = listOf("Details", "Lyrics", "Artwork")
            var selectedTab by remember { mutableIntStateOf(0) }

            PrimaryTabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            when (selectedTab) {
                0 -> DetailsTab(song)
                1 -> LyricsTab(song)
                2 -> ArtworkTab(song)
            }
        }
    }
}

@Composable
private fun DetailsTab(song: Song?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
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

@Composable
private fun LyricsTab(song: Song?) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        val lyrics = song?.lyrics
        if (lyrics.isNullOrBlank()) {
            Text(
                text = "No lyrics available.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = lyrics,
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun ArtworkTab(song: Song?) {
    ArtworkPicker(
        artworkUri = song?.artworkUri,
        editable = false,
        onPick = {},
        onReset = {},
        onRemove = {},
        modifier = Modifier.fillMaxSize()
    )
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
