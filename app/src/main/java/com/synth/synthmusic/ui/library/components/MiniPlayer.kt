package com.synth.synthmusic.ui.library.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.synth.synthmusic.R
import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.ui.theme.SynthMusicTheme

/**
 * Persistent bottom mini-player shown across library screens.
 *
 * @param song Currently playing song or null.
 * @param isPlaying Whether playback is active.
 * @param positionMs Current playback position in milliseconds.
 * @param durationMs Total track duration in milliseconds.
 * @param onTogglePlayPause Callback to play or pause.
 * @param onPrevious Callback to skip to the previous track.
 * @param onNext Callback to skip to the next track.
 * @param onExpand Callback to open the full Now Playing screen.
 * @param modifier Modifier for styling.
 */
@Composable
fun MiniPlayer(
    song: Song?,
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    onTogglePlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (song == null) return
    val progress = if (durationMs > 0) positionMs.toFloat() / durationMs else 0f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onExpand() }
    ) {
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f),
            drawStopIndicator = {}
            )
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val context = LocalContext.current
            val imageRequest = remember(song.artworkUri) {
                ImageRequest.Builder(context)
                    .data(song.artworkUri)
                    .placeholder(R.drawable.ic_placeholder_artwork)
                    .error(R.drawable.ic_placeholder_artwork)
                    .build()
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = "Album art",
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(4.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 8.dp)
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onPrevious, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    modifier = Modifier.size(22.dp)
                )
            }
            IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(40.dp)) {
                Icon(
                    imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Pause" else "Play",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun MiniPlayerPreview() {
    SynthMusicTheme {
        MiniPlayer(
            song = Song(
                id = "preview",
                title = "Untitled",
                artist = "Unknown",
                album = "Preview Album",
                albumArtist = "Preview Artist",
                durationMs = 180000L,
                trackNumber = 1,
                year = 2024,
                genre = "Pop",
                comment = "",
                path = "/music/preview.mp3",
                uri = "content://media/external/audio/media/1",
                bitrate = 320,
                sampleRate = 44100,
                fileSize = 0L,
                artworkUri = null,
                rating = 0f,
                playCount = 0,
                lastPlayed = null,
                dateAdded = 0L,
                dateModified = 0L,
                lyrics = null,
                isFavorite = false
            ),
            isPlaying = true,
            positionMs = 0L,
            durationMs = 180000L,
            onTogglePlayPause = {},
            onPrevious = {},
            onNext = {},
            onExpand = {}
        )
    }
}
