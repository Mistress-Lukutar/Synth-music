package com.synth.synthmusic.ui.playlists

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.synth.synthmusic.R
import com.synth.synthmusic.ui.playlists.components.PlaylistGridCard
import org.koin.androidx.compose.koinViewModel

/**
 * Playlist list screen with grid cards, create, rename, and delete actions.
 *
 * Displays playlists in a 2-column grid with the app logo header.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistsScreen(
    onNavigateToPlaylistDetail: (Long) -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlaylistViewModel = koinViewModel()
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val showDialog by viewModel.showCreateDialog.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.ic_logo_yellow),
                            contentDescription = "Synth",
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Playlists")
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.openCreateDialog() }) {
                Icon(Icons.Default.Add, contentDescription = "Create playlist")
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(8.dp)
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    PlaylistGridCard(
                        playlist = playlist,
                        onClick = { onNavigateToPlaylistDetail(playlist.id) },
                        onRename = { viewModel.renamePlaylist(playlist.id, it) },
                        onDelete = { viewModel.deletePlaylist(playlist.id) },
                        onChangeArtwork = { bytes -> viewModel.updateArtwork(playlist.id, bytes) }
                    )
                }
            }
        }
    }

    if (showDialog) {
        CreatePlaylistDialog(
            folders = viewModel.folders.collectAsStateWithLifecycle().value,
            onDismiss = { viewModel.dismissCreateDialog() },
            onCreateEmpty = { viewModel.createPlaylist(it) },
            onCreateFromFolder = { viewModel.createPlaylistFromFolder(it) }
        )
    }
}

private enum class CreateDialogStep {
    ChooseMode,
    EmptyName,
    FolderList
}

@Composable
private fun CreatePlaylistDialog(
    folders: List<String>,
    onDismiss: () -> Unit,
    onCreateEmpty: (String) -> Unit,
    onCreateFromFolder: (String) -> Unit
) {
    var step by remember { mutableStateOf(CreateDialogStep.ChooseMode) }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Playlist") },
        text = {
            when (step) {
                CreateDialogStep.ChooseMode -> {
                    Column {
                        DialogOption(
                            icon = Icons.Default.Create,
                            label = "Empty playlist",
                            onClick = { step = CreateDialogStep.EmptyName }
                        )
                        DialogOption(
                            icon = Icons.Default.Folder,
                            label = "From folder",
                            onClick = { step = CreateDialogStep.FolderList }
                        )
                    }
                }
                CreateDialogStep.EmptyName -> {
                    TextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Playlist name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                CreateDialogStep.FolderList -> {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(folders, key = { it }) { path ->
                            val folderName = java.io.File(path).name.ifBlank { path }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onCreateFromFolder(path) }
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Folder,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = folderName,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (step) {
                CreateDialogStep.EmptyName -> {
                    TextButton(
                        onClick = { onCreateEmpty(name) },
                        enabled = name.isNotBlank()
                    ) {
                        Text("Create")
                    }
                }
                else -> { /* no confirm button for ChooseMode or FolderList */ }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun DialogOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}
