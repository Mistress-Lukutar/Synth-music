package com.synth.synthmusic.ui.nowplaying

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import com.synth.synthmusic.ui.nowplaying.components.WaveformSlider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.synth.synthmusic.ui.nowplaying.components.LyricsBottomSheet
import com.synth.synthmusic.ui.nowplaying.components.RatingStars
import com.synth.synthmusic.ui.share.ShareSongSheet
import com.synth.synthmusic.ui.share.VolumeOutputSheet
import com.synth.synthmusic.ui.sleeptimer.SleepTimerDialog
import org.koin.androidx.compose.koinViewModel

/**
 * Full-screen player with artwork, waveform seekbar, and playback controls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingScreen(
    onNavigateBack: () -> Unit,
    onNavigateToQueue: () -> Unit,
    onNavigateToEqualizer: () -> Unit,
    onNavigateToVisualizer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NowPlayingViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSleepTimer by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showVolumeSheet by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Now Playing") },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            // Artwork
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(uiState.song?.artworkUri)
                    .crossfade(true)
                    .build(),
                contentDescription = "Album art",
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .aspectRatio(1f),
                contentScale = ContentScale.Crop
            )

            // Metadata
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = uiState.song?.title ?: "Unknown Title",
                    style = MaterialTheme.typography.headlineSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = uiState.song?.artist ?: "Unknown Artist",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = uiState.song?.album ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                RatingStars(
                    rating = uiState.rating,
                    onRatingChanged = { viewModel.onEvent(NowPlayingEvent.UpdateRating(it)) }
                )
            }

            // Seekbar
            Column(modifier = Modifier.fillMaxWidth()) {
                if (uiState.waveformAmplitudes.isNotEmpty()) {
                    WaveformSlider(
                        amplitudes = uiState.waveformAmplitudes,
                        progress = if (uiState.durationMs > 0) uiState.positionMs.toFloat() / uiState.durationMs else 0f,
                        onSeek = { fraction ->
                            val pos = (fraction * uiState.durationMs).toLong()
                            viewModel.onEvent(NowPlayingEvent.Seek(pos))
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    androidx.compose.material3.Slider(
                        value = uiState.positionMs.toFloat(),
                        onValueChange = { viewModel.onEvent(NowPlayingEvent.Seek(it.toLong())) },
                        valueRange = 0f..uiState.durationMs.toFloat().coerceAtLeast(1f),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = formatDuration(uiState.positionMs),
                        style = MaterialTheme.typography.labelSmall
                    )
                    Text(
                        text = formatDuration(uiState.durationMs),
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }

            // Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { viewModel.onEvent(NowPlayingEvent.ToggleShuffle) }) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (uiState.shuffleEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { viewModel.onEvent(NowPlayingEvent.Previous) }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous")
                }
                IconButton(
                    onClick = { viewModel.onEvent(NowPlayingEvent.PlayPause) },
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(
                        imageVector = if (uiState.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (uiState.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { viewModel.onEvent(NowPlayingEvent.Next) }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next")
                }
                IconButton(onClick = { showSleepTimer = true }) {
                    Icon(Icons.Default.Timer, contentDescription = "Sleep Timer")
                }
                IconButton(onClick = { showShareSheet = true }) {
                    Icon(Icons.Default.Share, contentDescription = "Share")
                }
                IconButton(onClick = { showVolumeSheet = true }) {
                    Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = "Volume")
                }
                IconButton(onClick = { viewModel.onEvent(NowPlayingEvent.ToggleFavorite) }) {
                    Icon(
                        imageVector = if (uiState.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (uiState.isFavorite) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { showLyrics = true }) {
                    Icon(Icons.AutoMirrored.Filled.Article, contentDescription = "Lyrics")
                }
                IconButton(onClick = onNavigateToVisualizer) {
                    Icon(Icons.Default.GraphicEq, contentDescription = "Visualizer")
                }
                IconButton(onClick = { viewModel.onEvent(NowPlayingEvent.CycleRepeat) }) {
                    val icon = when (uiState.repeatMode) {
                        androidx.media3.common.Player.REPEAT_MODE_ONE -> Icons.Default.RepeatOne
                        else -> Icons.Default.Repeat
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = "Repeat",
                        tint = if (uiState.repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF)
                            MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showSleepTimer) {
        SleepTimerDialog(onDismiss = { showSleepTimer = false })
    }
    if (showShareSheet) {
        ShareSongSheet(
            song = uiState.song,
            onDismiss = { showShareSheet = false }
        )
    }
    if (showVolumeSheet) {
        VolumeOutputSheet(onDismiss = { showVolumeSheet = false })
    }
    if (showLyrics) {
        LyricsBottomSheet(
            song = uiState.song,
            onDismiss = { showLyrics = false }
        )
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}
