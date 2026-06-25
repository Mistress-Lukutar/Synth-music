package com.synth.synthmusic.ui.nowplaying.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.ui.components.MenuDialog

/**
 * Dialog displaying and editing lyrics for the currently playing song.
 *
 * Uses the shared [MenuDialog] shell so it matches the other Now Playing menus.
 *
 * @param song Currently playing song.
 * @param onSave Callback invoked when user saves edited lyrics.
 * @param onDismiss Callback invoked when the dialog is dismissed.
 */
@Composable
fun LyricsDialog(
    song: Song?,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (song == null) return

    var isEditing by remember { mutableStateOf(false) }
    var editedLyrics by remember(song.id) { mutableStateOf(song.lyrics ?: "") }

    MenuDialog(
        title = "Lyrics",
        onDismiss = onDismiss,
        titleTrailing = {
            if (!isEditing) {
                TextButton(onClick = { isEditing = true }) {
                    Text("Edit")
                }
            }
        }
    ) {
        HorizontalDivider(
            modifier = Modifier.padding(horizontal = 16.dp),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )

        if (isEditing) {
            OutlinedTextField(
                value = editedLyrics,
                onValueChange = { editedLyrics = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                textStyle = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    isEditing = false
                    editedLyrics = song.lyrics ?: ""
                }) {
                    Text("Cancel")
                }
                Button(onClick = {
                    onSave(editedLyrics)
                    isEditing = false
                }) {
                    Text("Save")
                }
            }
        } else {
            val lyrics = song.lyrics
            if (lyrics.isNullOrBlank()) {
                Text(
                    text = "No lyrics available for this track.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                )
            } else {
                Text(
                    text = lyrics,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                )
            }
        }
    }
}
