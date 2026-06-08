package com.synth.synthmusic.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.net.toUri
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.synth.synthmusic.MainActivity
import com.synth.synthmusic.MusicApplication
import com.synth.synthmusic.R
import com.synth.synthmusic.data.local.database.AppDatabase
import com.synth.synthmusic.data.local.database.PlaybackQueueItemEntity
import com.synth.synthmusic.data.local.database.PlaybackStateEntity
import com.synth.synthmusic.data.local.database.SongDao
import com.synth.synthmusic.data.local.database.toDomain
import com.synth.synthmusic.data.media.AudioFadeManager
import com.synth.synthmusic.domain.repository.PlaylistRepository
import com.synth.synthmusic.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.pow

/**
 * Foreground service that owns the sole [ExoPlayer] instance.
 *
 * This service is the **only** component allowed to create, control, and release
 * the player. UI layers communicate via [androidx.media3.session.MediaController]
 * and observe state through a [MediaSession].
 *
 * The service handles:
 * - Audio focus and wake lock.
 * - Volume fade in/out via [AudioFadeManager].
 * - Process-death recovery (restore queue and position from Room).
 * - Persisting playback state on graceful shutdown.
 */
class PlaybackService : MediaSessionService() {

    private val appDatabase: AppDatabase by inject()
    private val songDao: SongDao by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val playlistRepository: PlaylistRepository by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null
    private var fadeManager: AudioFadeManager? = null

    private val isRestoring = AtomicBoolean(false)
    private var currentTargetVolume: Float = 1f
    private var fadeDurationMs: Int = 300

    private var positionUpdateJob: Job? = null
    private var settingsJob: Job? = null
    private var endOfTrackJob: Job? = null

    private val playerListener = object : Player.Listener {

        override fun onPlaybackStateChanged(state: Int) {
            updateDuration()
            persistStateImmediate()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                exoPlayer?.let { player ->
                    if (fadeDurationMs > 0) {
                        fadeManager?.fadeIn(fadeDurationMs.toLong(), currentTargetVolume)
                    } else {
                        player.volume = currentTargetVolume
                    }
                }
                startEndOfTrackMonitor()
            } else {
                stopEndOfTrackMonitor()
            }
            persistStateImmediate()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateDuration()
            mediaItem?.mediaId?.let { songId ->
                updateTargetVolume(songId)
                serviceScope.launch { playlistRepository.recordPlayAndSyncPlaylists(songId) }
            }
            exoPlayer?.let { player ->
                if (player.isPlaying && fadeDurationMs > 0) {
                    player.volume = 0f
                    fadeManager?.fadeIn(fadeDurationMs.toLong(), currentTargetVolume)
                } else {
                    player.volume = currentTargetVolume
                }
            }
            stopEndOfTrackMonitor()
            startEndOfTrackMonitor()
            persistStateImmediate()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            updateDuration()
            persistPositionDebounced()
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            persistStateImmediate()
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            persistStateImmediate()
        }
    }

    override fun onCreate() {
        // Promote to foreground immediately so the system does not kill us
        // before Media3 posts a playback notification.
        startForeground(NOTIFICATION_ID, createIdleNotification())

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId(MusicApplication.PLAYBACK_NOTIFICATION_CHANNEL_ID)
                .setNotificationId(NOTIFICATION_ID)
                .build()
                .apply {
                    setSmallIcon(R.drawable.ic_notification)
                }
        )
        super.onCreate()

        try {
            val player = createPlayer()
            exoPlayer = player
            fadeManager = AudioFadeManager(player, serviceScope)
            player.addListener(playerListener)

            val sessionActivityPendingIntent = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )

            val sessionPlayer = FadeAwarePlayer(player)
            mediaSession = MediaSession.Builder(this, sessionPlayer)
                .setSessionActivity(sessionActivityPendingIntent)
                .build()

            mediaSession?.let {
                addSession(it)
                Log.d(TAG, "MediaSession created and added")
            }

            restoreState()
            collectPlaybackSettings()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize playback service", e)
            stopSelf()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        stopEndOfTrackMonitor()
        settingsJob?.cancel()
        fadeManager?.cancel()

        persistStateBlocking()

        mediaSession?.let { removeSession(it) }
        mediaSession?.release()
        mediaSession = null

        exoPlayer?.removeListener(playerListener)
        exoPlayer?.release()
        exoPlayer = null

        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = exoPlayer ?: run {
            stopSelf()
            return
        }
        if (!player.playWhenReady || player.playbackState == Player.STATE_ENDED) {
            persistStateBlocking()
            stopSelf()
        }
    }

    // region Player setup & helpers

    private fun createPlayer(): ExoPlayer {
        return ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true // handleAudioFocus
            )
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
    }

    private fun createIdleNotification(): Notification {
        val channelId = MusicApplication.PLAYBACK_NOTIFICATION_CHANNEL_ID
        return NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Ready to play")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .build()
    }

    private fun updateDuration() {
        exoPlayer?.let { player ->
            // Duration is exposed via MediaSession/Timeline; no local UI to update here.
        }
    }

    private fun updateTargetVolume(songId: String) {
        val queueItem = exoPlayer?.currentTimeline?.let { timeline ->
            val window = androidx.media3.common.Timeline.Window()
            val currentIndex = exoPlayer?.currentMediaItemIndex ?: -1
            if (currentIndex in 0 until timeline.windowCount) {
                timeline.getWindow(currentIndex, window)
                window.mediaItem.mediaId == songId
            } else false
        } ?: false

        // Fallback: we cannot easily map MediaItem -> Song here without DB access.
        // We look up the song in the DB for ReplayGain.
        serviceScope.launch {
            val song = songDao.getById(songId)
            val gainDb = song?.replayGainTrackDb
            currentTargetVolume = if (gainDb != null) {
                10.0.pow(gainDb / 20.0).toFloat().coerceIn(0f, 1f)
            } else {
                1f
            }
        }
    }

    // endregion

    // region Settings collection

    private fun collectPlaybackSettings() {
        settingsJob = serviceScope.launch {
            settingsRepository.settings.collect { settings ->
                withContext(Dispatchers.Main) {
                    exoPlayer?.let { player ->
                        player.playbackParameters = androidx.media3.common.PlaybackParameters(
                            settings.playbackSpeed.coerceIn(0.25f, 4.0f),
                            settings.playbackPitch.coerceIn(0.25f, 4.0f)
                        )
                        player.skipSilenceEnabled = settings.skipSilence
                    }
                }
                fadeDurationMs = settings.fadeDurationMs.coerceIn(0, 2000)
            }
        }
    }

    // endregion

    // region State persistence

    private var persistJob: Job? = null
    private var positionPersistJob: Job? = null

    private fun persistStateImmediate() {
        if (!serviceScope.isActive) return
        persistJob?.cancel()
        persistJob = serviceScope.launch {
            writePlaybackState()
        }
    }

    private fun persistPositionDebounced() {
        if (!serviceScope.isActive) return
        positionPersistJob?.cancel()
        positionPersistJob = serviceScope.launch {
            delay(1000)
            writePlaybackState()
        }
    }

    private fun persistStateBlocking() {
        runBlocking(serviceScope.coroutineContext) {
            writePlaybackState()
        }
    }

    private suspend fun writePlaybackState() {
        val player = exoPlayer ?: return
        val currentSongId = player.currentMediaItem?.mediaId
        val positionMs = withContext(Dispatchers.Main) { player.currentPosition }
        val queueSize = player.currentTimeline.windowCount
        val queueMediaIds = mutableListOf<String>()
        val window = androidx.media3.common.Timeline.Window()
        for (i in 0 until queueSize) {
            player.currentTimeline.getWindow(i, window)
            queueMediaIds.add(window.mediaItem.mediaId)
        }

        appDatabase.savePlaybackState(
            PlaybackStateEntity(
                currentSongId = currentSongId,
                positionMs = positionMs,
                isPlaying = false,
                repeatMode = player.repeatMode,
                shuffleMode = player.shuffleModeEnabled
            ),
            queueMediaIds.mapIndexed { index, songId ->
                PlaybackQueueItemEntity(songId = songId, orderIndex = index)
            }
        )
    }

    // endregion

    // region State restore

    private fun restoreState() {
        serviceScope.launch {
            isRestoring.set(true)
            try {
                val saved = appDatabase.playbackStateDao().get() ?: return@launch
                val queueItems = appDatabase.playbackQueueItemDao().getAllOrdered()
                val songs = if (queueItems.isNotEmpty()) {
                    val songMap = queueItems.chunked(999)
                        .flatMap { chunk ->
                            songDao.getByIds(chunk.map { it.songId })
                        }
                        .associateBy { it.id }
                    queueItems.mapNotNull { songMap[it.songId] }
                } else {
                    saved.currentSongId?.let { id ->
                        songDao.getById(id)?.let { listOf(it) } ?: emptyList()
                    } ?: emptyList()
                }
                if (songs.isEmpty()) return@launch
                if (!isRestoring.get()) return@launch

                val domainSongs = songs.map { it.toDomain() }
                val startIndex = domainSongs.indexOfFirst { it.id == saved.currentSongId }
                    .coerceAtLeast(0)
                val mediaItems = domainSongs.map { song ->
                    MediaItem.Builder()
                        .setMediaId(song.id)
                        .setUri(song.uri)
                        .setMediaMetadata(
                            androidx.media3.common.MediaMetadata.Builder()
                                .setTitle(song.title)
                                .setArtist(song.artist)
                                .setAlbumTitle(song.album)
                                .setArtworkUri(song.artworkUri?.let { it.toUri() })
                                .build()
                        )
                        .build()
                }

                withContext(Dispatchers.Main) {
                    exoPlayer?.setMediaItems(mediaItems, startIndex, saved.positionMs.coerceAtLeast(0))
                    exoPlayer?.prepare()
                    exoPlayer?.repeatMode = saved.repeatMode
                    exoPlayer?.shuffleModeEnabled = saved.shuffleMode
                }
            } finally {
                isRestoring.set(false)
            }
        }
    }

    // endregion

    // region End-of-track monitor

    private fun startEndOfTrackMonitor() {
        stopEndOfTrackMonitor()
        if (fadeDurationMs <= 0) return
        endOfTrackJob = serviceScope.launch(Dispatchers.Main) {
            while (isActive) {
                exoPlayer?.let { player ->
                    if (player.isPlaying && player.duration > 0) {
                        val remaining = player.duration - player.currentPosition
                        val fm = fadeManager
                        if (remaining <= fadeDurationMs && fm != null && !fm.isFading && player.volume > 0f) {
                            fm.fadeOut(fadeDurationMs.toLong())
                        }
                    }
                }
                delay(100)
            }
        }
    }

    private fun stopEndOfTrackMonitor() {
        endOfTrackJob?.cancel()
        endOfTrackJob = null
    }

    // endregion

    // region FadeAwarePlayer

    /**
     * A [ForwardingPlayer] that intercepts **all** playback and queue-mutating
     * commands so that:
     *
     * 1. Volume fade is applied consistently for play / pause / skip / seek.
     * 2. External controllers cannot bypass the service's queue logic.
     */
    private inner class FadeAwarePlayer(
        player: ExoPlayer
    ) : ForwardingPlayer(player) {

        override fun play() {
            wrappedPlayer.play()
            if (fadeDurationMs > 0) {
                fadeManager?.fadeIn(fadeDurationMs.toLong(), currentTargetVolume)
            } else {
                wrappedPlayer.volume = currentTargetVolume
            }
        }

        override fun pause() {
            if (fadeDurationMs > 0 && wrappedPlayer.isPlaying) {
                fadeManager?.fadeOut(fadeDurationMs.toLong()) {
                    wrappedPlayer.pause()
                }
            } else {
                wrappedPlayer.pause()
            }
        }

        override fun stop() {
            if (fadeDurationMs > 0 && wrappedPlayer.isPlaying) {
                fadeManager?.fadeOut(fadeDurationMs.toLong()) {
                    wrappedPlayer.stop()
                }
            } else {
                wrappedPlayer.stop()
            }
        }

        override fun seekTo(positionMs: Long) {
            if (fadeDurationMs > 0) {
                fadeManager?.fadeOut(fadeDurationMs.toLong()) {
                    wrappedPlayer.seekTo(positionMs)
                    fadeManager?.fadeIn(fadeDurationMs.toLong(), currentTargetVolume)
                }
            } else {
                wrappedPlayer.seekTo(positionMs)
            }
        }

        override fun seekToNext() {
            if (fadeDurationMs > 0) {
                fadeManager?.fadeOut(fadeDurationMs.toLong()) {
                    wrappedPlayer.seekToNext()
                }
            } else {
                wrappedPlayer.seekToNext()
            }
        }

        override fun seekToPrevious() {
            if (fadeDurationMs > 0) {
                fadeManager?.fadeOut(fadeDurationMs.toLong()) {
                    wrappedPlayer.seekToPrevious()
                }
            } else {
                wrappedPlayer.seekToPrevious()
            }
        }

        override fun seekToNextMediaItem() {
            seekToNext()
        }

        override fun seekToPreviousMediaItem() {
            seekToPrevious()
        }

        override fun setPlayWhenReady(playWhenReady: Boolean) {
            if (playWhenReady) {
                play()
            } else {
                pause()
            }
        }

        override fun setShuffleModeEnabled(shuffleModeEnabled: Boolean) {
            wrappedPlayer.shuffleModeEnabled = shuffleModeEnabled
        }

        override fun setRepeatMode(repeatMode: Int) {
            wrappedPlayer.repeatMode = repeatMode
        }

        // region Queue overrides — ensure external controllers cannot bypass service logic

        override fun setMediaItem(mediaItem: MediaItem) {
            wrappedPlayer.setMediaItem(mediaItem)
        }

        override fun setMediaItem(mediaItem: MediaItem, startPositionMs: Long) {
            wrappedPlayer.setMediaItem(mediaItem, startPositionMs)
        }

        override fun setMediaItems(mediaItems: MutableList<MediaItem>) {
            wrappedPlayer.setMediaItems(mediaItems)
        }

        override fun setMediaItems(mediaItems: MutableList<MediaItem>, resetPosition: Boolean) {
            wrappedPlayer.setMediaItems(mediaItems, resetPosition)
        }

        override fun setMediaItems(
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ) {
            wrappedPlayer.setMediaItems(mediaItems, startIndex, startPositionMs)
        }

        override fun addMediaItem(mediaItem: MediaItem) {
            wrappedPlayer.addMediaItem(mediaItem)
        }

        override fun addMediaItem(index: Int, mediaItem: MediaItem) {
            wrappedPlayer.addMediaItem(index, mediaItem)
        }

        override fun addMediaItems(mediaItems: MutableList<MediaItem>) {
            wrappedPlayer.addMediaItems(mediaItems)
        }

        override fun addMediaItems(index: Int, mediaItems: MutableList<MediaItem>) {
            wrappedPlayer.addMediaItems(index, mediaItems)
        }

        override fun removeMediaItem(index: Int) {
            wrappedPlayer.removeMediaItem(index)
        }

        override fun moveMediaItem(currentIndex: Int, newIndex: Int) {
            wrappedPlayer.moveMediaItem(currentIndex, newIndex)
        }

        override fun clearMediaItems() {
            wrappedPlayer.clearMediaItems()
        }

        // endregion
    }

    // endregion

    companion object {
        private const val TAG = "PlaybackService"
        private const val NOTIFICATION_ID = 1001
    }
}
