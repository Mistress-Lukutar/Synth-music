package com.synth.synthmusic.ui.library.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.synth.synthmusic.domain.model.Album

/**
 * Grid item composable for an album.
 *
 * Delegates to [GridCardItem] for consistent styling across collection types.
 *
 * @param album Album to display.
 * @param onClick Callback invoked when the item is clicked.
 * @param modifier Modifier for styling.
 */
@Composable
fun AlbumGridItem(
    album: Album,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GridCardItem(
        imageUri = album.artworkUri,
        title = album.title,
        subtitle = album.artist,
        meta = "${album.songCount} tracks",
        onClick = onClick,
        modifier = modifier
    )
}
