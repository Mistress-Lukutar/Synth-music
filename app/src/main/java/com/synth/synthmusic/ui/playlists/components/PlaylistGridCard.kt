package com.synth.synthmusic.ui.playlists.components

import android.content.res.Configuration
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import com.synth.synthmusic.domain.model.GeneratedArtworkConfig
import com.synth.synthmusic.domain.model.Playlist
import com.synth.synthmusic.ui.components.CollectionCard
import com.synth.synthmusic.ui.components.CollectionCardStyle
import com.synth.synthmusic.ui.library.components.ChangeArtworkDialog
import com.synth.synthmusic.ui.library.components.GenerateArtworkDialog
import com.synth.synthmusic.ui.theme.SynthMusicTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Actions that can be performed on a playlist's artwork.
 */
sealed interface ArtworkAction {
    data class Picked(val bytes: ByteArray) : ArtworkAction
    data object Auto : ArtworkAction
    data class Generated(val config: GeneratedArtworkConfig) : ArtworkAction
    data object Removed : ArtworkAction
}

/**
 * Grid card composable for a playlist.
 *
 * Displays the playlist as a square card. Long-pressing the card opens a menu
 * with rename, delete, and change artwork actions.
 *
 * @param playlist Playlist to display.
 * @param onClick Callback invoked when the card is clicked.
 * @param onRename Callback invoked with the new name when renamed.
 * @param onDelete Callback invoked when the delete action is confirmed.
 * @param onArtworkAction Callback invoked for artwork changes.
 * @param modifier Modifier for styling.
 */
@Composable
fun PlaylistGridCard(
    playlist: Playlist,
    onClick: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onArtworkAction: (ArtworkAction) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showArtworkDialog by remember { mutableStateOf(false) }
    var showGenerateDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val pickMedia = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) {
                    onArtworkAction(ArtworkAction.Picked(bytes))
                }
            }
        }
    }

    Box(modifier = modifier) {
        CollectionCard(
            imageModel = playlist.artworkUri,
            title = playlist.name,
            subtitle = null,
            meta = "${playlist.songCount} tracks",
            style = CollectionCardStyle.Grid,
            onClick = onClick,
            onLongClick = { expanded = true }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            if (!playlist.isFixed) {
                DropdownMenuItem(
                    text = { Text("Rename") },
                    onClick = { expanded = false; showRename = true },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text("Delete") },
                    onClick = { expanded = false; showDeleteConfirm = true },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) }
                )
            }
            DropdownMenuItem(
                text = { Text("Change artwork") },
                onClick = { expanded = false; showArtworkDialog = true }
            )
        }
    }

    if (showRename) {
        var text by remember { mutableStateOf(playlist.name) }
        AlertDialog(
            onDismissRequest = { showRename = false },
            title = { Text("Rename Playlist") },
            text = {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Name") }
                )
            },
            confirmButton = {
                TextButton(onClick = { showRename = false; onRename(text) }) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRename = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Playlist") },
            text = { Text("Are you sure you want to delete \"${playlist.name}\"?") },
            confirmButton = {
                TextButton(
                    onClick = { showDeleteConfirm = false; onDelete() }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showArtworkDialog) {
        ChangeArtworkDialog(
            artworkUri = playlist.artworkUri,
            collectionTitle = playlist.name,
            onDismiss = { showArtworkDialog = false },
            onPick = { pickMedia.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            onAuto = {
                showArtworkDialog = false
                onArtworkAction(ArtworkAction.Auto)
            },
            onGenerate = {
                showArtworkDialog = false
                showGenerateDialog = true
            },
            onRemove = {
                showArtworkDialog = false
                onArtworkAction(ArtworkAction.Removed)
            }
        )
    }

    if (showGenerateDialog) {
        GenerateArtworkDialog(
            onGenerate = { config ->
                showGenerateDialog = false
                onArtworkAction(ArtworkAction.Generated(config))
            },
            onDismiss = { showGenerateDialog = false }
        )
    }
}

@Preview(showBackground = true, name = "Light")
@Preview(showBackground = true, name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PlaylistGridCardPreview() {
    SynthMusicTheme {
        PlaylistGridCard(
            playlist = Playlist(
                id = 1,
                name = "My Playlist",
                createdAt = 0,
                songCount = 8,
                artworkUri = null,
                isFixed = false
            ),
            onClick = {},
            onRename = {},
            onDelete = {},
            onArtworkAction = {}
        )
    }
}
