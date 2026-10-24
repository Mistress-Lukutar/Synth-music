package com.synth.synthmusic.ui.search.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.synth.synthmusic.R
import com.synth.synthmusic.domain.model.Album
import com.synth.synthmusic.domain.model.Artist
import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.ui.library.components.ArtistListItem
import com.synth.synthmusic.ui.library.components.SongListItem

/**
 * Displays search result sections for songs, albums, and artists in a single scrollable list.
 *
 * Renders a results summary followed by categorized sections with headers. Each section
 * conditionally renders only when it contains items. Songs use [SongListItem], albums use
 * a compact row layout, and artists use [ArtistListItem].
 *
 * @param songs Filtered list of matching songs.
 * @param albums Filtered list of matching albums.
 * @param artists Filtered list of matching artists.
 * @param currentSongId ID of the currently playing song (used to highlight it).
 * @param onSongClick Callback invoked when a song is tapped (triggers playback).
 * @param onNavigateToSongInfo Callback for navigating to song info details.
 * @param onAddToPlaylist Callback for adding a song to a playlist.
 * @param onPlayNext Callback for playing a song next in the queue.
 * @param onAddToQueue Callback for adding a song to the end of the queue.
 * @param onShare Callback for sharing a song.
 * @param onNavigateToAlbumDetail Callback for navigating to album detail (title, artist).
 * @param onNavigateToArtistDetail Callback for navigating to artist detail by name.
 * @param modifier Modifier for styling.
 */
@Composable
fun SearchResultSections(
    songs: List<Song>,
    albums: List<Album>,
    artists: List<Artist>,
    currentSongId: String?,
    onSongClick: (Song) -> Unit,
    onNavigateToSongInfo: (String) -> Unit,
    onAddToPlaylist: (String) -> Unit,
    onPlayNext: (String) -> Unit,
    onAddToQueue: (String) -> Unit,
    onShare: (String) -> Unit,
    onNavigateToAlbumDetail: (String, String) -> Unit,
    onNavigateToArtistDetail: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(modifier) {
        // Results summary
        item {
            val songCount = songs.size
            val albumCount = albums.size
            val artistCount = artists.size

            val parts = buildList {
                if (songCount > 0) add("$songCount songs")
                if (albumCount > 0) add("$albumCount albums")
                if (artistCount > 0) add("$artistCount artists")
            }

            val summaryText = if (parts.isEmpty()) "No results" else parts.joinToString(" \u00B7 ")

            Text(
                text = summaryText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        // Songs section
        if (songs.isNotEmpty()) {
            item {
                Text(
                    text = "Songs",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(songs, key = { "song_" + it.id }) { song ->
                SongListItem(
                    song = song,
                    onClick = { onSongClick(song) },
                    onNavigateToSongInfo = onNavigateToSongInfo,
                    onAddToPlaylist = onAddToPlaylist,
                    onPlayNext = onPlayNext,
                    onAddToQueue = onAddToQueue,
                    onShare = onShare,
                    isCurrent = song.id == currentSongId
                )
            }
        }

        // Albums section
        if (albums.isNotEmpty()) {
            item {
                Text(
                    text = "Albums",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(albums, key = { "album_" + it.id }) { album ->
                AlbumSearchRow(
                    album = album,
                    onNavigateToAlbumDetail = onNavigateToAlbumDetail
                )
            }
        }

        // Artists section
        if (artists.isNotEmpty()) {
            item {
                Text(
                    text = "Artists",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(artists, key = { "artist_" + it.id }) { artist ->
                ArtistListItem(
                    artist = artist,
                    onClick = { onNavigateToArtistDetail(artist.name) }
                )
            }
        }
    }
}

/**
 * Compact row for displaying an album in search results.
 *
 * Renders a rounded-corner album artwork thumbnail alongside the title and artist name.
 * The entire row is clickable to navigate to album detail.
 *
 * @param album Album to display.
 * @param onNavigateToAlbumDetail Callback for navigating to album detail (title, artist).
 */
@Composable
private fun AlbumSearchRow(
    album: Album,
    onNavigateToAlbumDetail: (String, String) -> Unit
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onNavigateToAlbumDetail(album.title, album.artist) }
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            val imageRequest = remember(album.artworkUri) {
                ImageRequest.Builder(context)
                    .data(album.artworkUri)
                    .placeholder(R.drawable.ic_placeholder_artwork)
                    .error(R.drawable.ic_placeholder_artwork)
                    .build()
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = null,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp)),
                contentScale = ContentScale.Crop
            )
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(12.dp))

        Column {
            Text(
                text = album.title,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = album.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
