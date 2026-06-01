package com.synth.synthmusic.ui.components

import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.ui.library.components.SongListItem

/**
 * Reusable vertical list of songs with current-track highlighting.
 *
 * @param songs List of songs to display.
 * @param currentSongId ID of the currently playing song, or null.
 * @param onSongClick Callback invoked with the item index when a song is clicked.
 * @param onNavigateToSongInfo Callback to open song info.
 * @param onNavigateToEditMetadata Callback to open metadata editor.
 * @param onAddToPlaylist Callback to add song to a playlist.
 * @param onPlayNext Callback to play this song next in the queue.
 * @param onAddToQueue Callback to append this song to the queue.
 * @param onShare Callback to share the song.
 * @param onRemoveFromPlaylist Callback to remove the song from the current playlist.
 * @param trailingContent Optional trailing composable rendered after each item.
 * @param modifier Modifier for styling.
 */
@Composable
fun SongList(
    songs: List<Song>,
    currentSongId: String?,
    onSongClick: (Song) -> Unit,
    onNavigateToSongInfo: ((String) -> Unit)? = null,
    onNavigateToEditMetadata: ((String) -> Unit)? = null,
    onAddToPlaylist: ((String) -> Unit)? = null,
    onPlayNext: ((String) -> Unit)? = null,
    onAddToQueue: ((String) -> Unit)? = null,
    onShare: ((String) -> Unit)? = null,
    onRemoveFromPlaylist: ((String) -> Unit)? = null,
    trailingContent: @Composable ((Song) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier = modifier) {
        itemsIndexed(
            items = songs,
            key = { _, song -> song.id }
        ) { index, song ->
            SongListItem(
                song = song,
                onClick = { onSongClick(song) },
                onNavigateToSongInfo = onNavigateToSongInfo,
                onNavigateToEditMetadata = onNavigateToEditMetadata,
                onAddToPlaylist = onAddToPlaylist,
                onPlayNext = onPlayNext,
                onAddToQueue = onAddToQueue,
                onShare = onShare,
                onRemoveFromPlaylist = onRemoveFromPlaylist,
                isCurrent = song.id == currentSongId,
                trailingContent = trailingContent
            )
        }
    }
}
