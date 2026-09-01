package com.synth.synthmusic.data.media

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.synth.synthmusic.domain.model.QueueItem
import com.synth.synthmusic.domain.model.PlaybackState
import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.domain.repository.SongRepository
import com.synth.synthmusic.service.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicLong

/**
 * Adapts Media3 [MediaController] into the application's existing [StateFlow]-based
 * playback model.
 *
 * This repository is the **single entry point** for all UI components that need to
 * observe playback state or issue playback commands. It maintains a [MediaController]
 * connection to [PlaybackService] and mirrors the player's state into hot [StateFlow]s
 * so that Jetpack Compose screens can collect them with [collectAsStateWithLifecycle].
 *
 * The queue is represented by two internal lists:
 * - `_originalQueue` — the user-defined order (album/playlist order + append/next operations).
 * - `_activeQueue` — the order actually handed to ExoPlayer. When shuffle is enabled
 *   this list is a shuffled view of `_originalQueue`.
 *
 * ExoPlayer's built-in shuffle is disabled; shuffle is implemented manually by
 * rebuilding `_activeQueue`, which guarantees that the on-screen queue matches the
 * real playback order and that `play next` always inserts right after the current item.
 *
 * @param context application context used to build the [SessionToken].
 * @param songRepository data source for resolving [Song] entities from media IDs.
 */
class PlaybackRepository(
    private val context: Context,
    private val songRepository: SongRepository
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private var positionUpdateJob: Job? = null

    private val _playbackState = MutableStateFlow(PlaybackState())
    val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    /**
     * Internal active queue handed to ExoPlayer. When shuffle is on this is a shuffled
     * view of [_originalQueue]; when shuffle is off the two lists are identical.
     */
    private val _activeQueue = MutableStateFlow<List<QueueItem>>(emptyList())

    /**
     * Internal source-of-truth queue. Preserves the order chosen by the user so that
     * disabling shuffle can restore the unshuffled view.
     */
    private val _originalQueue = MutableStateFlow<List<QueueItem>>(emptyList())

    /**
     * UI-facing queue. Always reflects the active playback order.
     */
    private val _currentQueue = MutableStateFlow<List<Song>>(emptyList())
    val currentQueue: StateFlow<List<Song>> = _currentQueue.asStateFlow()

    /**
     * Exposes the original (unshuffled) queue. Used by [PlaybackService]
     * for persistence across process death.
     */
    val originalQueue: StateFlow<List<QueueItem>> = _originalQueue.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _currentDurationMs = MutableStateFlow(0L)
    val currentDurationMs: StateFlow<Long> = _currentDurationMs.asStateFlow()

    private val _audioSessionId = MutableStateFlow(0)
    val audioSessionId: StateFlow<Int> = _audioSessionId.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    /**
     * Generator of stable queue position ids. Starts at 1 and is initialized from
     * the max persisted id on restore so that new items never collide with restored ones.
     */
    private val nextQueueId = AtomicLong(1L)

    private fun generateQueueId(): Long = nextQueueId.getAndIncrement()

    /**
     * Listener attached to the connected [MediaController] to pump player events
     * into Kotlin [StateFlow]s.
     */
    private val playerListener = object : Player.Listener {

        override fun onPlaybackStateChanged(state: Int) {
            updatePlaybackState()
            updateDuration()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updatePlaybackState()
            if (isPlaying) {
                startPositionUpdates()
            } else {
                stopPositionUpdates()
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updatePlaybackState()
            _currentPositionMs.value = 0L
            updateDuration()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            _currentPositionMs.value = mediaController?.currentPosition ?: 0L
            updateDuration()
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            // Lightweight sync: if the timeline size diverges from our local queue,
            // reconcile so that external controllers (e.g. Android Auto) are reflected.
            val controller = mediaController ?: return
            val timelineSize = timeline.windowCount
            if (timelineSize != _activeQueue.value.size) {
                scope.launch {
                    syncQueueFromTimeline(controller, timeline)
                }
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            // Shuffle is managed manually by rebuilding the active queue. Ignore
            // ExoPlayer's own shuffle flag changes.
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            _audioSessionId.value = audioSessionId
        }
    }

    /**
     * Allows the service to report the ExoPlayer audio session id directly.
     *
     * This is a fallback because the [MediaController] wrapper may dispatch
     * [Player.Listener.onAudioSessionIdChanged] unreliably.
     */
    fun setAudioSessionId(audioSessionId: Int) {
        _audioSessionId.value = audioSessionId
    }

    /**
     * Builds a [MediaController] and binds it to [PlaybackService].
     *
     * Safe to call multiple times; subsequent calls are ignored while connected.
     */
    fun connect() {
        if (controllerFuture != null) return

        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )

        controllerFuture = MediaController.Builder(context, sessionToken)
            .buildAsync()
            .apply {
                addListener({
                    try {
                        val controller = get()
                        mediaController = controller
                        controller.addListener(playerListener)
                        _isConnected.value = true

                        // Initial sync
                        updatePlaybackState()
                        updateDuration()
                        scope.launch { syncQueueFromTimeline(controller, controller.currentTimeline) }
                        startPositionUpdates()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to connect MediaController", e)
                        _isConnected.value = false
                    }
                }, MoreExecutors.directExecutor())
            }
    }

    /**
     * Releases the [MediaController] and cleans up observers.
     *
     * Should be called when the Activity moves to the background and no longer
     * needs playback state updates (e.g. [ComponentActivity.onStop]).
     */
    fun disconnect() {
        stopPositionUpdates()
        mediaController?.removeListener(playerListener)
        mediaController = null
        controllerFuture?.let { future ->
            MediaController.releaseFuture(future)
        }
        controllerFuture = null
        _isConnected.value = false
    }

    // region State sync

    private fun updatePlaybackState() {
        val controller = mediaController ?: return
        // Shuffle is managed manually by rebuilding the active queue; do not mirror
        // ExoPlayer's shuffle flag here because it is kept disabled.
        _playbackState.value = _playbackState.value.copy(
            currentSongId = controller.currentMediaItem?.mediaId,
            isPlaying = controller.isPlaying,
            repeatMode = controller.repeatMode
        )
    }

    private fun updateDuration() {
        val controller = mediaController ?: return
        _currentDurationMs.value = controller.duration.coerceAtLeast(0L)
    }

    private suspend fun syncQueueFromTimeline(
        controller: MediaController,
        timeline: androidx.media3.common.Timeline
    ) {
        val window = androidx.media3.common.Timeline.Window()
        val mediaIds = mutableListOf<String>()
        for (i in 0 until timeline.windowCount) {
            timeline.getWindow(i, window)
            mediaIds.add(window.mediaItem.mediaId)
        }
        if (mediaIds.isEmpty()) {
            setQueues(emptyList(), emptyList())
            return
        }
        val songs = withContext(Dispatchers.IO) {
            songRepository.getSongsByIds(mediaIds)
        }
        val songMap = songs.associateBy { it.id }
        val activeItems = mediaIds.mapNotNull { songMap[it] }
            .map { QueueItem(generateQueueId(), it) }
        setQueues(active = activeItems, original = if (_playbackState.value.shuffleEnabled) _originalQueue.value else activeItems)
    }

    private fun setQueues(active: List<QueueItem>, original: List<QueueItem>) {
        _activeQueue.value = active
        _originalQueue.value = original
        _currentQueue.value = active.map { it.song }
    }

    private fun startPositionUpdates() {
        stopPositionUpdates()
        positionUpdateJob = scope.launch {
            while (isActive) {
                mediaController?.let { controller ->
                    if (controller.isPlaying) {
                        _currentPositionMs.value = controller.currentPosition
                    }
                }
                delay(POSITION_POLL_INTERVAL_MS)
            }
        }
    }

    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }

    // endregion

    // region Playback commands

    /**
     * Starts playback of the given [songs] beginning at [startIndex].
     *
     * If the [MediaController] is not yet connected the command is silently dropped.
     */
    fun playSongs(songs: List<Song>, startIndex: Int = 0) {
        val controller = mediaController ?: return
        if (songs.isEmpty()) return
        val safeStartIndex = startIndex.coerceIn(0, songs.lastIndex)

        val original = songs.map { QueueItem(generateQueueId(), it) }
        val active = if (_playbackState.value.shuffleEnabled) {
            buildShuffledQueue(original, original[safeStartIndex].id)
        } else {
            original
        }
        setQueues(active = active, original = original)

        val mediaItems = active.map { it.toMediaItem() }
        val exoStartIndex = if (_playbackState.value.shuffleEnabled) 0 else safeStartIndex
        controller.setMediaItems(mediaItems, exoStartIndex, 0L)
        controller.prepare()
        controller.play()
    }

    fun playQueueItem(index: Int) {
        val controller = mediaController ?: return
        controller.seekTo(index, 0L)
        controller.play()
    }

    fun addToQueue(song: Song) {
        val controller = mediaController ?: return
        val item = QueueItem(generateQueueId(), song)
        _activeQueue.update { it + item }
        _originalQueue.update { it + item }
        _currentQueue.value = _activeQueue.value.map { it.song }
        controller.addMediaItem(item.toMediaItem())
    }

    fun playNext(song: Song) {
        val controller = mediaController ?: return
        val currentIndex = controller.currentMediaItemIndex
        if (currentIndex !in _activeQueue.value.indices) {
            // Fallback to end of queue if current index is somehow out of sync.
            addToQueue(song)
            return
        }
        val item = QueueItem(generateQueueId(), song)
        val activeInsertIndex = (currentIndex + 1).coerceAtMost(_activeQueue.value.size)
        _activeQueue.update { queue ->
            val mutable = queue.toMutableList()
            mutable.add(activeInsertIndex, item)
            mutable
        }
        val currentActiveId = _activeQueue.value[currentIndex].id
        val originalCurrentIndex = _originalQueue.value.indexOfFirst { it.id == currentActiveId }
        val originalInsertIndex = (originalCurrentIndex + 1).coerceAtMost(_originalQueue.value.size)
        _originalQueue.update { queue ->
            val mutable = queue.toMutableList()
            mutable.add(originalInsertIndex, item)
            mutable
        }
        _currentQueue.value = _activeQueue.value.map { it.song }
        controller.addMediaItem(activeInsertIndex, item.toMediaItem())
    }

    fun clearQueue() {
        val controller = mediaController ?: return
        setQueues(emptyList(), emptyList())
        controller.clearMediaItems()
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val controller = mediaController ?: return
        _activeQueue.update { queue ->
            if (fromIndex !in queue.indices) return@update queue
            val mutable = queue.toMutableList()
            val item = mutable.removeAt(fromIndex)
            val newIndex = toIndex.coerceIn(0, mutable.size)
            mutable.add(newIndex, item)
            mutable
        }
        _currentQueue.value = _activeQueue.value.map { it.song }
        if (!_playbackState.value.shuffleEnabled) {
            _originalQueue.value = _activeQueue.value
        }
        controller.moveMediaItem(fromIndex, toIndex)
    }

    fun removeFromQueue(index: Int) {
        val controller = mediaController ?: return
        val removedId = _activeQueue.value.getOrNull(index)?.id ?: return
        _activeQueue.update { queue ->
            if (index !in queue.indices) return@update queue
            val mutable = queue.toMutableList()
            mutable.removeAt(index)
            mutable
        }
        _originalQueue.update { queue ->
            queue.filter { it.id != removedId }
        }
        _currentQueue.value = _activeQueue.value.map { it.song }
        controller.removeMediaItem(index)
    }

    fun play() {
        mediaController?.play()
    }

    fun pause() {
        mediaController?.pause()
    }

    fun stop() {
        mediaController?.stop()
    }

    fun playPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            controller.play()
        }
    }

    fun next() {
        mediaController?.seekToNext()
    }

    fun previous() {
        mediaController?.seekToPrevious()
    }

    fun seekTo(positionMs: Long) {
        val controller = mediaController ?: return
        controller.seekTo(positionMs)
        // Reflect the target position immediately instead of waiting for the next poll.
        _currentPositionMs.value = positionMs
    }

    /**
     * Toggles shuffle mode by reordering the active queue in place.
     *
     * When enabled, the current track stays at its current index and the remaining
     * tracks are shuffled around it. When disabled, the active queue is restored to
     * the original order. Reordering uses [MediaController.moveMediaItem] instead of
     * replacing all media items, so the current track keeps playing without a
     * codec re-initialization gap.
     */
    fun setShuffleEnabled(enabled: Boolean) {
        val controller = mediaController ?: return
        val currentIndex = controller.currentMediaItemIndex
        if (currentIndex !in _activeQueue.value.indices) return

        val targetOrder = if (enabled) {
            val others = _activeQueue.value.indices.filter { it != currentIndex }.shuffled()
            listOf(currentIndex) + others
        } else {
            _originalQueue.value.map { original ->
                _activeQueue.value.indexOfFirst { it.id == original.id }
            }
        }

        applyQueuePermutation(targetOrder)
        _playbackState.update { it.copy(shuffleEnabled = enabled) }
    }

    fun cycleRepeatMode() {
        val controller = mediaController ?: return
        val next = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        controller.repeatMode = next
        _playbackState.update { it.copy(repeatMode = next) }
    }

    /**
     * Restores both queues after process death and initializes the id generator
     * so that newly added items never collide with restored ones.
     *
     * The restored [activeQueue] is already in shuffled order when [shuffleEnabled]
     * was persisted as true, so only the flag needs to be re-applied here.
     */
    fun restoreQueues(
        activeQueue: List<QueueItem>,
        originalQueue: List<QueueItem>,
        shuffleEnabled: Boolean = false
    ) {
        val maxId = (activeQueue + originalQueue).maxOfOrNull { it.id } ?: 0L
        nextQueueId.set(maxId + 1)
        setQueues(active = activeQueue, original = originalQueue)
        _playbackState.update { it.copy(shuffleEnabled = shuffleEnabled) }
    }

    // endregion

    // region Queue helpers

    private fun buildShuffledQueue(queue: List<QueueItem>, currentItemId: Long): List<QueueItem> {
        val current = queue.find { it.id == currentItemId }
        if (current == null) return queue.shuffled()
        val rest = queue.filter { it.id != currentItemId }.shuffled()
        return listOf(current) + rest
    }

    /**
     * Reorders the ExoPlayer timeline and the local [_activeQueue] to match
     * [targetOrder] without recreating [MediaItem]s.
     *
     * [targetOrder] is a permutation of current indices: `targetOrder[i]` is the
     * index of the item that should end up at position `i`.
     */
    private fun applyQueuePermutation(targetOrder: List<Int>) {
        val controller = mediaController ?: return
        if (targetOrder.size != _activeQueue.value.size) return
        if (targetOrder.toSet() != _activeQueue.value.indices.toSet()) return

        val currentOrder = _activeQueue.value.indices.toMutableList()

        for (targetIndex in targetOrder.indices) {
            val currentIndex = currentOrder.indexOf(targetOrder[targetIndex])
            if (currentIndex == targetIndex) continue

            controller.moveMediaItem(currentIndex, targetIndex)
            val moved = currentOrder.removeAt(currentIndex)
            currentOrder.add(targetIndex, moved)
        }

        val newActive = targetOrder.map { _activeQueue.value[it] }
        _activeQueue.value = newActive
        _currentQueue.value = newActive.map { it.song }
    }

    private fun QueueItem.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(song.id)
        .setUri(song.uri)
        .setTag(id)
        .setMediaMetadata(
            androidx.media3.common.MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album)
                .setArtworkUri(song.artworkUri?.toUri())
                .build()
        )
        .build()

    // endregion

    companion object {
        private const val TAG = "PlaybackRepository"
        private const val POSITION_POLL_INTERVAL_MS = 50L
    }
}
