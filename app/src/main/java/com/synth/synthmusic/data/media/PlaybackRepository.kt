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

/**
 * Adapts Media3 [MediaController] into the application's existing [StateFlow]-based
 * playback model.
 *
 * This repository is the **single entry point** for all UI components that need to
 * observe playback state or issue playback commands. It maintains a [MediaController]
 * connection to [PlaybackService] and mirrors the player's state into hot [StateFlow]s
 * so that Jetpack Compose screens can collect them with [collectAsStateWithLifecycle].
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

    private val _currentQueue = MutableStateFlow<List<Song>>(emptyList())
    val currentQueue: StateFlow<List<Song>> = _currentQueue.asStateFlow()

    private val _currentPositionMs = MutableStateFlow(0L)
    val currentPositionMs: StateFlow<Long> = _currentPositionMs.asStateFlow()

    private val _currentDurationMs = MutableStateFlow(0L)
    val currentDurationMs: StateFlow<Long> = _currentDurationMs.asStateFlow()

    private val _audioSessionId = MutableStateFlow(0)
    val audioSessionId: StateFlow<Int> = _audioSessionId.asStateFlow()

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

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
            if (timelineSize != _currentQueue.value.size) {
                scope.launch {
                    syncQueueFromTimeline(controller, timeline)
                }
            }
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            _audioSessionId.value = audioSessionId
        }
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
        _playbackState.value = PlaybackState(
            currentSongId = controller.currentMediaItem?.mediaId,
            isPlaying = controller.isPlaying,
            repeatMode = controller.repeatMode,
            shuffleEnabled = controller.shuffleModeEnabled
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
            _currentQueue.value = emptyList()
            return
        }
        val songs = withContext(Dispatchers.IO) {
            songRepository.getSongsByIds(mediaIds)
        }
        // Preserve timeline order
        val songMap = songs.associateBy { it.id }
        _currentQueue.value = mediaIds.mapNotNull { songMap[it] }
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
        _currentQueue.value = songs
        val mediaItems = songs.map { it.toMediaItem() }
        controller.setMediaItems(mediaItems, startIndex, 0L)
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
        _currentQueue.update { it + song }
        controller.addMediaItem(song.toMediaItem())
    }

    fun playNext(song: Song) {
        val controller = mediaController ?: return
        val currentIndex = controller.currentMediaItemIndex
        val insertIndex = (currentIndex + 1).coerceAtMost(_currentQueue.value.size)
        _currentQueue.update { queue ->
            val mutable = queue.toMutableList()
            mutable.add(insertIndex, song)
            mutable
        }
        controller.addMediaItem(insertIndex, song.toMediaItem())
    }

    fun clearQueue() {
        val controller = mediaController ?: return
        _currentQueue.value = emptyList()
        controller.clearMediaItems()
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val controller = mediaController ?: return
        _currentQueue.update { queue ->
            if (fromIndex !in queue.indices) return@update queue
            val mutable = queue.toMutableList()
            val item = mutable.removeAt(fromIndex)
            val newIndex = toIndex.coerceIn(0, mutable.size)
            mutable.add(newIndex, item)
            mutable
        }
        controller.moveMediaItem(fromIndex, toIndex)
    }

    fun removeFromQueue(index: Int) {
        val controller = mediaController ?: return
        _currentQueue.update { queue ->
            if (index !in queue.indices) return@update queue
            val mutable = queue.toMutableList()
            mutable.removeAt(index)
            mutable
        }
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
        mediaController?.seekTo(positionMs)
    }

    fun setShuffleEnabled(enabled: Boolean) {
        val controller = mediaController ?: return
        controller.shuffleModeEnabled = enabled
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

    // endregion

    private fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id)
        .setUri(uri)
        .setMediaMetadata(
            androidx.media3.common.MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setArtworkUri(artworkUri?.toUri())
                .build()
        )
        .build()

    companion object {
        private const val TAG = "PlaybackRepository"
        private const val POSITION_POLL_INTERVAL_MS = 50L
    }
}
