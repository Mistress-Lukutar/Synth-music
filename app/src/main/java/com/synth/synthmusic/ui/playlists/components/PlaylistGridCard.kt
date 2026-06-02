package com.synth.synthmusic.ui.playlists.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.synth.synthmusic.domain.model.Playlist
import com.synth.synthmusic.ui.library.components.GridCardItem

/**
 * Grid card composable for a playlist.
 *
 * Displays the playlist as a square card with an overflow menu for rename and delete.
 *
 * @param playlist Playlist to display.
 * @param onClick Callback invoked when the card is clicked.
 * @param onRename Callback invoked with the new name when renamed.
 * @param onDelete Callback invoked when the delete action is confirmed.
 * @param modifier Modifier for styling.
 */
@Composable
fun PlaylistGridCard(
    playlist: Playlist,
    onClick: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    GridCardItem(
        imageUri = playlist.artworkUri,
        title = playlist.name,
        subtitle = null,
        meta = "${playlist.songCount} tracks",
        onClick = onClick,
        overflowActions = {
            IconButton(onClick = { expanded = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = "More",
                    tint = Color.White
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
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
            }
        },
        modifier = modifier
    )

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
}
