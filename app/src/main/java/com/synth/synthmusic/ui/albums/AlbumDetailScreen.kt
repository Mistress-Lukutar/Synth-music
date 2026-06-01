package com.synth.synthmusic.ui.albums

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.synth.synthmusic.R
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.synth.synthmusic.ui.library.components.AddToPlaylistDialog
import com.synth.synthmusic.ui.library.components.SongListItem
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Detail screen showing all songs in a specific album.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(
    albumTitle: String,
    albumArtist: String,
    onNavigateBack: () -> Unit,
    onNavigateToNowPlaying: () -> Unit,
    onNavigateToSongInfo: (String) -> Unit,
    onNavigateToEditMetadata: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AlbumDetailViewModel = koinViewModel { parametersOf(albumTitle, albumArtist) }
) {
    val album by viewModel.album.collectAsState()
    val songs by viewModel.songs.collectAsState()
    val playback by viewModel.playbackState.collectAsState()
    var selectedSongForPlaylist by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = album?.title ?: albumTitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
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
                .padding(innerPadding)
        ) {
            item {
                AlbumHeader(
                    album = album,
                    songCount = songs.size,
                    onPlayAll = { viewModel.playAll() },
                    onShuffleAll = { viewModel.shuffleAll() }
                )
            }
            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                SongListItem(
                    song = song,
                    onClick = { viewModel.playSongAt(index) },
                    onNavigateToSongInfo = onNavigateToSongInfo,
                    onNavigateToEditMetadata = onNavigateToEditMetadata,
                    onAddToPlaylist = { selectedSongForPlaylist = it },
                    onPlayNext = { viewModel.playNext(it) },
                    onAddToQueue = { viewModel.addToQueue(it) },
                    isCurrent = song.id == playback.currentSongId
                )
            }
        }
    }

    selectedSongForPlaylist?.let { songId ->
        AddToPlaylistDialog(
            songId = songId,
            onDismiss = { selectedSongForPlaylist = null }
        )
    }
}

@Composable
private fun AlbumHeader(
    album: com.synth.synthmusic.domain.model.Album?,
    songCount: Int,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            val context = LocalContext.current
            val artworkUri = album?.artworkUri
            val imageRequest = remember(artworkUri) {
                ImageRequest.Builder(context)
                    .data(artworkUri)
                    .size(120)
                    .placeholder(R.drawable.ic_placeholder_artwork)
                    .error(R.drawable.ic_placeholder_artwork)
                    .build()
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = "Album art",
                modifier = Modifier
                    .size(120.dp)
                    .aspectRatio(1f),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = album?.artist ?: "",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "$songCount tracks",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.padding(vertical = 8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Button(
                onClick = onPlayAll,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Play All")
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onShuffleAll,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Default.Shuffle, contentDescription = null)
                Spacer(modifier = Modifier.width(4.dp))
                Text("Shuffle")
            }
        }
    }
}
