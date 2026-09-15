package com.synth.synthmusic.ui.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Tab displaying music folders.
 *
 * Shows a fake "All Songs" entry at the top (all tracks, newest first),
 * followed by the device folders where music files were found.
 *
 * @param folders List of folder paths containing music.
 * @param onAllClick Callback invoked when the "All Songs" entry is selected.
 * @param onFolderClick Callback invoked when a folder is selected.
 * @param modifier Modifier for styling.
 */
@Composable
fun FoldersTab(
    folders: List<String>,
    onAllClick: () -> Unit,
    onFolderClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 8.dp)
    ) {
        item(key = "all_songs") {
            FolderCard(
                title = "All Songs",
                subtitle = "All tracks, newest first",
                highlighted = true,
                onClick = onAllClick
            )
        }
        items(folders, key = { it }) { folder ->
            FolderCard(
                title = folder.substringAfterLast("/").ifEmpty { folder },
                subtitle = folder.toDisplayFolderPath(),
                highlighted = false,
                onClick = { onFolderClick(folder) }
            )
        }
    }
}

/**
 * Strips the leading system storage prefix from an absolute folder path so it
 * can be shown to the user, e.g. `/storage/emulated/0/Music/A` becomes `Music/A`
 * and `/storage/0123-4567/Music` becomes `Music`.
 */
private fun String.toDisplayFolderPath(): String {
    val withoutStorage = removePrefix("/storage/")
    if (withoutStorage == this) return this
    val withoutEmulated = withoutStorage.removePrefix("emulated/")
    val remainder = (if (withoutEmulated != withoutStorage) {
        withoutEmulated
    } else {
        withoutStorage
    }).substringAfter('/', "")
    return remainder.ifEmpty { this }
}

/**
 * A single folder entry card.
 *
 * @param title Display name of the folder.
 * @param subtitle Folder path or description shown below the title.
 * @param highlighted Whether the card uses the accent container color.
 * @param onClick Callback invoked when the card is clicked.
 */
@Composable
private fun FolderCard(
    title: String,
    subtitle: String,
    highlighted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
