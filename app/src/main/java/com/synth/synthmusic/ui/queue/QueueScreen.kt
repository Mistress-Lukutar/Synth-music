package com.synth.synthmusic.ui.queue

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import org.koin.androidx.compose.koinViewModel
import kotlin.math.roundToInt

/**
 * Playback queue screen with drag-to-reorder support.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: QueueViewModel = koinViewModel()
) {
    val queue by viewModel.queue.collectAsState()
    val playback by viewModel.playbackState.collectAsState()

    var draggingItemIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val itemHeightPx = with(LocalDensity.current) { 64.dp.toPx() }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "${queue.size} tracks · ${formatTotalDuration(queue.sumOf { it.durationMs })}"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearQueue() }) {
                        Icon(Icons.Default.ClearAll, contentDescription = "Clear queue")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            itemsIndexed(queue, key = { index, song -> "${song.id}_$index" }) { index, song ->
                val isDragging = index == draggingItemIndex
                val targetIndex = if (isDragging) {
                    val offsetItems = (dragOffset / itemHeightPx).roundToInt()
                    (index + offsetItems).coerceIn(0, queue.size - 1)
                } else index

                QueueItem(
                    song = song,
                    isCurrent = song.id == playback.currentSongId,
                    onClick = { viewModel.playItem(index) },
                    onRemove = { viewModel.removeItem(index) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset {
                            if (isDragging) {
                                IntOffset(0, dragOffset.toInt())
                            } else {
                                IntOffset(0, 0)
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = {
                                    draggingItemIndex = index
                                    dragOffset = 0f
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    dragOffset += dragAmount.y
                                },
                                onDragEnd = {
                                    if (draggingItemIndex != -1) {
                                        val finalOffsetItems = (dragOffset / itemHeightPx).roundToInt()
                                        val newIndex = (draggingItemIndex + finalOffsetItems)
                                            .coerceIn(0, queue.size - 1)
                                        if (newIndex != draggingItemIndex) {
                                            viewModel.moveItem(draggingItemIndex, newIndex)
                                        }
                                    }
                                    draggingItemIndex = -1
                                    dragOffset = 0f
                                }
                            )
                        }
                )
            }
        }
    }
}

@Composable
private fun QueueItem(
    song: com.synth.synthmusic.domain.model.Song,
    isCurrent: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isCurrent)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(song.artworkUri)
                    .crossfade(true)
                    .build(),
                contentDescription = "Album art",
                modifier = Modifier.size(48.dp),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyLarge,
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
            Text(
                text = formatDuration(song.durationMs),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            IconButton(onClick = onRemove) {
                Icon(Icons.Default.Delete, contentDescription = "Remove")
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

private fun formatTotalDuration(ms: Long): String {
    val totalMinutes = ms / 1000 / 60
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) String.format("%d:%02d", hours, minutes) else String.format("%d min", minutes)
}
