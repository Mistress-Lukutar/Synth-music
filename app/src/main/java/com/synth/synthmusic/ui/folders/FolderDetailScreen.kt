package com.synth.synthmusic.ui.folders

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.synth.synthmusic.ui.library.components.SongListItem
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Detail screen showing songs inside a specific folder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FolderDetailScreen(
    folderPath: String,
    onNavigateBack: () -> Unit,
    onNavigateToNowPlaying: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: FolderDetailViewModel = koinViewModel { parametersOf(folderPath) }
) {
    val songs by viewModel.songs.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(folderPath.substringAfterLast("/").ifEmpty { folderPath }) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("${songs.size} tracks")
                    Spacer(modifier = Modifier.padding(vertical = 8.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { viewModel.playAll() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Play All")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = { viewModel.shuffleAll() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Shuffle, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Shuffle")
                        }
                    }
                }
            }
            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                SongListItem(
                    song = song,
                    onClick = { viewModel.playSongAt(index) }
                )
            }
        }
    }
}
