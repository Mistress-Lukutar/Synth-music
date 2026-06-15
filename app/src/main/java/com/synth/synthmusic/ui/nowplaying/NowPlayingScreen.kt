package com.synth.synthmusic.ui.nowplaying

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.content.res.Configuration
import androidx.compose.runtime.mutableFloatStateOf
import com.synth.synthmusic.ui.theme.SynthMusicTheme
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.ui.nowplaying.components.ActionBar
import com.synth.synthmusic.ui.nowplaying.components.AudioQualityLabel
import com.synth.synthmusic.ui.nowplaying.components.AudioVisualizerRing
import com.synth.synthmusic.ui.nowplaying.components.BlurredArtworkBackground
import com.synth.synthmusic.ui.nowplaying.components.LyricsBottomSheet
import com.synth.synthmusic.ui.nowplaying.components.PlaybackControls
import com.synth.synthmusic.ui.nowplaying.components.PlaybackSpeedBottomSheet
import com.synth.synthmusic.ui.nowplaying.components.QueueBottomSheet
import com.synth.synthmusic.ui.nowplaying.components.RotatingVinyl
import com.synth.synthmusic.ui.nowplaying.components.WaveformSlider
import com.synth.synthmusic.ui.playback.PlaybackViewModel
import com.synth.synthmusic.ui.share.ShareSongSheet
import com.synth.synthmusic.ui.sleeptimer.SleepTimerDialog
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

/**
 * Stateless content composable for the Now Playing screen.
 *
 * @param uiState Current UI state including song metadata and playback info.
 * @param artworkCenterY Vertical center of the disc in window coordinates, used to align the background artwork.
 * @param onArtworkCenterMeasured Callback invoked when the disc center is measured.
 * @param onNavigateBack Callback to collapse the player.
 * @param onNavigateToVisualizer Callback to open the visualizer screen.
 * @param onToggleFavorite Callback to toggle the favorite status of the current song.
 * @param onToggleShuffle Callback to toggle shuffle mode.
 * @param onCycleRepeat Callback to cycle through repeat modes.
 * @param onPlayPause Callback to toggle playback.
 * @param onPrevious Callback to skip to the previous track.
 * @param onNext Callback to skip to the next track.
 * @param onSeek Callback with the desired position in milliseconds.
 * @param onRecordAudioPermissionGranted Callback invoked when RECORD_AUDIO permission result arrives.
 * @param onShowSleepTimer Callback to show the sleep timer dialog.
 * @param onShowShareSheet Callback to show the share sheet.
 * @param onShowLyrics Callback to show the lyrics bottom sheet.
 * @param onShowSpeedSheet Callback to show the playback speed bottom sheet.
 * @param onShowQueue Callback to show the queue bottom sheet.
 * @param modifier Modifier for styling.
 */
@Composable
fun NowPlayingContent(
    uiState: NowPlayingUiState,
    artworkCenterY: Float,
    onArtworkCenterMeasured: (Float) -> Unit,
    onNavigateBack: () -> Unit,
    onNavigateToVisualizer: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onRecordAudioPermissionGranted: (Boolean) -> Unit,
    onShowSleepTimer: () -> Unit,
    onShowShareSheet: () -> Unit,
    onShowLyrics: () -> Unit,
    onShowSpeedSheet: () -> Unit,
    onShowQueue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Background layer
        BlurredArtworkBackground(
            artworkUri = uiState.song?.artworkUri,
            artworkCenterY = artworkCenterY
        )

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
                    .weight(1f)
                    .onGloballyPositioned { coordinates ->
                        val bounds = coordinates.boundsInWindow()
                        onArtworkCenterMeasured(bounds.top + bounds.height / 2f)
                    },
                contentAlignment = Alignment.Center
            ) {
                val context = LocalContext.current
                val recordAudioLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    onRecordAudioPermissionGranted(isGranted)
                }

                LaunchedEffect(Unit) {
                    if (ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        recordAudioLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }

                AudioVisualizerRing(
                    audioSessionId = uiState.audioSessionId,
                    isPlaying = uiState.isPlaying,
                    dotColor = MaterialTheme.colorScheme.primary,
                    hasRecordAudioPermission = uiState.hasRecordAudioPermission,
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

                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (uiState.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (uiState.isFavorite) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = onShowQueue) {
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
                    onSeek(pos)
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
                onShuffleClick = onToggleShuffle,
                onPreviousClick = onPrevious,
                onPlayPauseClick = onPlayPause,
                onNextClick = onNext,
                onRepeatClick = onCycleRepeat
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Bottom action bar
            ActionBar(
                onTimerClick = onShowSleepTimer,
                onShareClick = onShowShareSheet,
                onLyricsClick = onShowLyrics,
                onVisualizerClick = onNavigateToVisualizer
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

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
    var showQueue by remember { mutableStateOf(false) }
    var artworkCenterY by remember { mutableFloatStateOf(0f) }

    NowPlayingContent(
        uiState = uiState,
        artworkCenterY = artworkCenterY,
        onArtworkCenterMeasured = { artworkCenterY = it },
        onNavigateBack = onNavigateBack,
        onNavigateToVisualizer = onNavigateToVisualizer,
        onToggleFavorite = { viewModel.onEvent(NowPlayingEvent.ToggleFavorite) },
        onToggleShuffle = { viewModel.onEvent(NowPlayingEvent.ToggleShuffle) },
        onCycleRepeat = { viewModel.onEvent(NowPlayingEvent.CycleRepeat) },
        onPlayPause = { playbackViewModel.playPause() },
        onPrevious = { playbackViewModel.previous() },
        onNext = { playbackViewModel.next() },
        onSeek = { playbackViewModel.seekTo(it) },
        onRecordAudioPermissionGranted = { viewModel.refreshRecordAudioPermission() },
        onShowSleepTimer = { showSleepTimer = true },
        onShowShareSheet = { showShareSheet = true },
        onShowLyrics = { showLyrics = true },
        onShowSpeedSheet = { showSpeedSheet = true },
        onShowQueue = { showQueue = true },
        modifier = modifier
    )

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
    if (showQueue) {
        QueueBottomSheet(
            queue = uiState.queueSongs,
            currentSongId = uiState.song?.id,
            onPlayQueueItem = { viewModel.playQueueItem(it) },
            onRemoveFromQueue = { viewModel.removeFromQueue(it) },
            onClearQueue = { viewModel.clearQueue() },
            onDismiss = { showQueue = false }
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

@Preview(device = "id:pixel_5", name = "Light")
@Preview(device = "id:pixel_5", name = "Dark", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NowPlayingContentPreview() {
    SynthMusicTheme {
        NowPlayingContent(
            uiState = NowPlayingUiState(
                song = Song(
                    id = "1",
                    title = "Neon Dreams",
                    artist = "Synthwave Artist",
                    album = "Midnight Run",
                    albumArtist = "Synthwave Artist",
                    durationMs = 234000,
                    trackNumber = 3,
                    year = 2024,
                    genre = "Synthwave",
                    comment = "",
                    path = "/music/neon_dreams.mp3",
                    uri = "content://media/external/audio/media/1",
                    bitrate = 320,
                    sampleRate = 44100,
                    fileSize = 9_400_000,
                    artworkUri = null,
                    rating = 4.5f,
                    playCount = 128,
                    lastPlayed = null,
                    dateAdded = 0,
                    dateModified = 0,
                    lyrics = null,
                    isFavorite = true
                ),
                isPlaying = true,
                positionMs = 45000,
                durationMs = 234000,
                repeatMode = Player.REPEAT_MODE_ALL,
                shuffleEnabled = true,
                rating = 4.5f,
                isFavorite = true,
                waveformAmplitudes = List(200) { 0.05f + kotlin.random.Random.nextFloat() * 0.9f },
                playbackSpeed = 1.0f,
                playbackPitch = 1.0f,
                audioSessionId = 0,
                hasRecordAudioPermission = false,
                audioQualityLabel = "320 kbps"
            ),
            artworkCenterY = 0f,
            onArtworkCenterMeasured = {},
            onNavigateBack = {},
            onNavigateToVisualizer = {},
            onToggleFavorite = {},
            onToggleShuffle = {},
            onCycleRepeat = {},
            onPlayPause = {},
            onPrevious = {},
            onNext = {},
            onSeek = {},
            onRecordAudioPermissionGranted = {},
            onShowSleepTimer = {},
            onShowShareSheet = {},
            onShowLyrics = {},
            onShowSpeedSheet = {},
            onShowQueue = {}
        )
    }
}
