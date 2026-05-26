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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Metadata editor screen for a single song.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditMetadataScreen(
    songId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditMetadataViewModel = koinViewModel { parametersOf(songId) }
) {
    val song by viewModel.song.collectAsState()
    val s = song

    var title by remember(s?.id) { mutableStateOf(s?.title ?: "") }
    var artist by remember(s?.id) { mutableStateOf(s?.artist ?: "") }
    var album by remember(s?.id) { mutableStateOf(s?.album ?: "") }
    var genre by remember(s?.id) { mutableStateOf(s?.genre ?: "") }
    var year by remember(s?.id) { mutableStateOf(s?.year?.toString() ?: "") }
    var comment by remember(s?.id) { mutableStateOf(s?.comment ?: "") }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Edit Metadata") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    if (s != null) {
                        TextButton(
                            onClick = {
                                viewModel.save(title, artist, album, genre, year, comment)
                                onNavigateBack()
                            }
                        ) {
                            Text("Save")
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
                .padding(16.dp)
        ) {
            if (s != null) {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
                TextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artist") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
                TextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text("Album") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
                TextField(
                    value = genre,
                    onValueChange = { genre = it },
                    label = { Text("Genre") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
                TextField(
                    value = year,
                    onValueChange = { year = it },
                    label = { Text("Year") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
                TextField(
                    value = comment,
                    onValueChange = { comment = it },
                    label = { Text("Comment") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                )
            } else {
                Text("Loading...", style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}
