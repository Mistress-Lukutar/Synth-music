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
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.synth.synthmusic.ui.nowplaying.components.ActionBar
import com.synth.synthmusic.ui.nowplaying.components.AudioQualityLabel
import com.synth.synthmusic.ui.nowplaying.components.AudioVisualizerRing
import com.synth.synthmusic.ui.nowplaying.components.BlurredArtworkBackground
import com.synth.synthmusic.ui.nowplaying.components.LyricsBottomSheet
import com.synth.synthmusic.ui.nowplaying.components.PlaybackControls
import com.synth.synthmusic.ui.nowplaying.components.PlaybackSpeedBottomSheet
import com.synth.synthmusic.ui.nowplaying.components.RotatingVinyl
import com.synth.synthmusic.ui.nowplaying.components.WaveformSlider
import com.synth.synthmusic.ui.playback.PlaybackViewModel
import com.synth.synthmusic.ui.share.ShareSongSheet
import com.synth.synthmusic.ui.sleeptimer.SleepTimerDialog
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

/**
 * Full-screen player with blurred background, spinning vinyl, visualizer ring,
 * waveform seekbar, and playback controls.
 */
@Composable
fun NowPlayingScreen(
    onNavigateBack: () -> Unit,
    onNavigateToVisualizer: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NowPlayingViewModel = koinViewModel(),
    playbackViewModel: PlaybackViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showSleepTimer by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showLyrics by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        // Background layer
        BlurredArtworkBackground(artworkUri = uiState.song?.artworkUri)

        // Content layer
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Top bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Collapse",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "Now Playing",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                // Spacer to balance the icon button on the left
                Box(modifier = Modifier.size(48.dp))
            }

            // Vinyl + Visualizer ring
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                AudioVisualizerRing(
                    audioSessionId = uiState.audioSessionId,
                    isPlaying = uiState.isPlaying,
                    dotColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .fillMaxWidth(0.92f)
                        .aspectRatio(1f)
                )
                RotatingVinyl(
                    artworkUri = uiState.song?.artworkUri,
                    isPlaying = uiState.isPlaying,
                    size = 280.dp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Metadata row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = uiState.song?.title ?: "Unknown Title",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
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
                }

                IconButton(onClick = { viewModel.onEvent(NowPlayingEvent.ToggleFavorite) }) {
                    Icon(
                        imageVector = if (uiState.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (uiState.isFavorite) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = { /* Queue not yet implemented */ }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                        contentDescription = "Queue",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Waveform seekbar
            WaveformSlider(
                amplitudes = uiState.waveformAmplitudes.takeIf { it.isNotEmpty() } ?: EmptyWaveform,
                progress = if (uiState.durationMs > 0) uiState.positionMs.toFloat() / uiState.durationMs else 0f,
                onSeek = { fraction ->
                    val pos = (fraction * uiState.durationMs).toLong()
                    playbackViewModel.seekTo(pos)
                },
                modifier = Modifier.fillMaxWidth()
            )

            // Time row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDuration(uiState.positionMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = formatDuration(uiState.durationMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            AudioQualityLabel(
                label = uiState.audioQualityLabel,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Playback controls
            PlaybackControls(
                isPlaying = uiState.isPlaying,
                shuffleEnabled = uiState.shuffleEnabled,
                repeatMode = uiState.repeatMode,
                onShuffleClick = { viewModel.onEvent(NowPlayingEvent.ToggleShuffle) },
                onPreviousClick = { playbackViewModel.previous() },
                onPlayPauseClick = { playbackViewModel.playPause() },
                onNextClick = { playbackViewModel.next() },
                onRepeatClick = { viewModel.onEvent(NowPlayingEvent.CycleRepeat) }
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom action bar
            ActionBar(
                onTimerClick = { showSleepTimer = true },
                onShareClick = { showShareSheet = true },
                onLyricsClick = { showLyrics = true },
                onVisualizerClick = onNavigateToVisualizer
            )

            Spacer(modifier = Modifier.height(24.dp))
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
    if (showLyrics) {
        LyricsBottomSheet(
            song = uiState.song,
            onSave = { viewModel.onEvent(NowPlayingEvent.SaveLyrics(it)) },
            onDismiss = { showLyrics = false }
        )
    }
    if (showSpeedSheet) {
        PlaybackSpeedBottomSheet(
            currentSpeed = uiState.playbackSpeed,
            currentPitch = uiState.playbackPitch,
            onSpeedChanged = { viewModel.onEvent(NowPlayingEvent.SetPlaybackSpeed(it)) },
            onPitchChanged = { viewModel.onEvent(NowPlayingEvent.SetPlaybackPitch(it)) },
            onDismiss = { showSpeedSheet = false }
        )
    }
}

private val EmptyWaveform = List(200) { 0.05f }

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
