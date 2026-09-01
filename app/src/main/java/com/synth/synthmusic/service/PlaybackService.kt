package com.synth.synthmusic.service

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ServiceCompat
import android.content.pm.ServiceInfo
import androidx.core.net.toUri
import androidx.core.app.NotificationCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
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
import com.synth.synthmusic.data.local.database.PlaybackOriginalQueueItemEntity
import com.synth.synthmusic.data.local.database.PlaybackQueueItemEntity
import com.synth.synthmusic.data.local.database.PlaybackStateEntity
import com.synth.synthmusic.data.local.database.SongDao
import com.synth.synthmusic.data.local.database.toDomain
import com.synth.synthmusic.domain.model.QueueItem
import com.synth.synthmusic.domain.repository.PlaylistRepository
import com.synth.synthmusic.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
 * - Per-track volume adjustment based on ReplayGain tags.
 * - Process-death recovery (restore queue and position from Room).
 * - Persisting playback state on graceful shutdown.
 */
class PlaybackService : MediaSessionService() {

    private val appDatabase: AppDatabase by inject()
    private val songDao: SongDao by inject()
    private val settingsRepository: SettingsRepository by inject()
    private val playlistRepository: PlaylistRepository by inject()
    private val playbackRepository: com.synth.synthmusic.data.media.PlaybackRepository by inject()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var exoPlayer: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    private val isRestoring = AtomicBoolean(false)
    private var currentTargetVolume: Float = 1f

    private var settingsJob: Job? = null

    /**
     * Pauses playback when a headset or Bluetooth device is disconnected.
     * This is more reliable than manifest-registered [AudioBecomingNoisyReceiver]
     * on modern OEM devices.
     */
    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) {
            val wasPrivateOutput = removedDevices.any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_HEADSET
            }
            Log.d(TAG, "AudioDeviceCallback.onAudioDevicesRemoved: wasPrivateOutput=$wasPrivateOutput")
            if (wasPrivateOutput && exoPlayer?.isPlaying == true) {
                Log.d(TAG, "Pausing because private audio output was removed")
                exoPlayer?.pause()
            }
        }
    }

    private val playerListener = object : Player.Listener {

        override fun onPlaybackStateChanged(state: Int) {
            updateDuration()
            persistStateImmediate()
            if (state == Player.STATE_IDLE && exoPlayer?.playWhenReady == false) {
                Log.d(TAG, "Player is idle and not playing, stopping service")
                stopSelf()
            }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) {
                exoPlayer?.volume = currentTargetVolume
            }
            persistStateImmediate()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateDuration()
            mediaItem?.mediaId?.let { songId ->
                updateTargetVolume(songId)
                serviceScope.launch { playlistRepository.recordPlayAndSyncPlaylists(songId) }
            }
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

        override fun onAudioSessionIdChanged(audioSessionId: Int) {
            Log.d(TAG, "ExoPlayer audioSessionId changed: $audioSessionId")
            playbackRepository.setAudioSessionId(audioSessionId)
        }
    }

    override fun onCreate() {
        Log.d(TAG, "onCreate")
        // Promote to foreground immediately so the system does not kill us
        // before Media3 posts a playback notification. ServiceCompat is used
        // so Android 12+ respects the declared mediaPlayback foreground type.
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            createIdleNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            } else {
                0
            }
        )

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
            player.addListener(playerListener)

            val sessionActivityPendingIntent = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE
            )

            mediaSession = MediaSession.Builder(this, player)
                .setSessionActivity(sessionActivityPendingIntent)
                .build()

            mediaSession?.let {
                addSession(it)
                Log.d(TAG, "MediaSession created and added")
            }

            restoreState()
            collectPlaybackSettings()
            collectShuffleChanges()

            val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            audioManager.registerAudioDeviceCallback(audioDeviceCallback, Handler(Looper.getMainLooper()))
            Log.d(TAG, "AudioDeviceCallback registered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize playback service", e)
            stopSelf()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: action=${intent?.action}, flags=$flags, startId=$startId")
        if (intent?.action == AudioBecomingNoisyReceiver.ACTION_PAUSE) {
            Log.d(TAG, "Received ACTION_PAUSE, calling exoPlayer.pause()")
            exoPlayer?.pause()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        settingsJob?.cancel()

        persistStateAsync()

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        Log.d(TAG, "AudioDeviceCallback unregistered")

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
        Log.d(TAG, "onTaskRemoved")
        val player = exoPlayer ?: run {
            stopSelf()
            return
        }
        if (!player.playWhenReady || player.playbackState == Player.STATE_ENDED) {
            persistStateAsync()
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
        serviceScope.launch {
            val song = songDao.getById(songId)
            val gainDb = song?.replayGainTrackDb
            currentTargetVolume = if (gainDb != null) {
                10.0.pow(gainDb / 20.0).toFloat().coerceIn(0f, 1f)
            } else {
                1f
            }
            withContext(Dispatchers.Main) {
                exoPlayer?.volume = currentTargetVolume
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
            }
        }
    }

    // endregion

    /**
     * Persists playback state whenever the manual shuffle flag changes.
     *
     * Toggling shuffle only reorders the queue via [PlaybackRepository] and fires no
     * ExoPlayer listener callback, so without this the flag would only reach the
     * database on the next unrelated player event.
     */
    private fun collectShuffleChanges() {
        serviceScope.launch {
            playbackRepository.playbackState
                .map { it.shuffleEnabled }
                .distinctUntilChanged()
                .drop(1)
                .collect {
                    // Skip the emission produced by state restore; the database
                    // already holds the restored value at that point.
                    if (!isRestoring.get()) persistStateImmediate()
                }
        }
    }

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

    /**
     * Fires a best-effort state persist without blocking the caller thread.
     * Used in lifecycle callbacks (onTaskRemoved/onDestroy) where blocking
     * the main thread causes ANRs.
     */
    private fun persistStateAsync() {
        if (!serviceScope.isActive) return
        serviceScope.launch {
            writePlaybackState()
        }
    }

    private suspend fun writePlaybackState() {
        val player = exoPlayer ?: return

        // All player reads must happen on the main thread.
        val (currentSongId, positionMs, activeMediaIds, repeatMode, shuffleMode) = withContext(Dispatchers.Main) {
            val songId = player.currentMediaItem?.mediaId
            val position = player.currentPosition
            val queueSize = player.currentTimeline.windowCount
            val mediaIds = mutableListOf<String>()
            val window = androidx.media3.common.Timeline.Window()
            for (i in 0 until queueSize) {
                player.currentTimeline.getWindow(i, window)
                mediaIds.add(window.mediaItem.mediaId)
            }
            Quintuple(songId, position, mediaIds, player.repeatMode, playbackRepository.playbackState.value.shuffleEnabled)
        }

        withContext(Dispatchers.IO) {
            val originalQueue = playbackRepository.originalQueue.value

            appDatabase.savePlaybackState(
                PlaybackStateEntity(
                    currentSongId = currentSongId,
                    positionMs = positionMs,
                    isPlaying = false,
                    repeatMode = repeatMode,
                    shuffleMode = shuffleMode
                ),
                activeQueue = activeMediaIds.mapIndexed { index, songId ->
                    PlaybackQueueItemEntity(songId = songId, orderIndex = index)
                },
                originalQueue = originalQueue.mapIndexed { index, item ->
                    PlaybackOriginalQueueItemEntity(songId = item.song.id, orderIndex = index)
                }
            )
        }
    }

    // endregion

    // region State restore

    private fun restoreState() {
        serviceScope.launch {
            isRestoring.set(true)
            try {
                val saved = appDatabase.playbackStateDao().get() ?: return@launch
                val activeItems = appDatabase.playbackQueueItemDao().getAllOrdered()
                val originalItems = appDatabase.playbackOriginalQueueItemDao().getAllOrdered()

                if (activeItems.isEmpty()) {
                    // Fallback: at least restore the last played song if no queue was saved.
                    saved.currentSongId?.let { songId ->
                        val song = songDao.getById(songId)?.toDomain() ?: return@launch
                        val item = QueueItem(id = 1L, song = song)
                        playbackRepository.restoreQueues(listOf(item), listOf(item), saved.shuffleMode)
                        withContext(Dispatchers.Main) {
                            exoPlayer?.setMediaItems(listOf(item.toMediaItem()), 0, saved.positionMs.coerceAtLeast(0))
                            exoPlayer?.prepare()
                            exoPlayer?.repeatMode = saved.repeatMode
                            exoPlayer?.shuffleModeEnabled = false
                        }
                    }
                    return@launch
                }
                if (!isRestoring.get()) return@launch

                val allSongIds = (activeItems.map { it.songId } + originalItems.map { it.songId }).distinct()
                val songMap = allSongIds.chunked(999)
                    .flatMap { chunk -> songDao.getByIds(chunk) }
                    .associateBy { it.id }

                fun buildQueueItem(songId: String, id: Long): QueueItem? {
                    val song = songMap[songId]?.toDomain() ?: return null
                    return QueueItem(id = id, song = song)
                }

                val activeQueue = activeItems.mapIndexedNotNull { index, entity ->
                    buildQueueItem(entity.songId, index + 1L)
                }
                val originalQueue = originalItems.mapIndexedNotNull { index, entity ->
                    buildQueueItem(entity.songId, index + 1L)
                }
                if (activeQueue.isEmpty()) return@launch

                val safeOriginalQueue = originalQueue.ifEmpty { activeQueue }
                val startIndex = activeQueue.indexOfFirst { it.song.id == saved.currentSongId }
                    .coerceAtLeast(0)

                playbackRepository.restoreQueues(activeQueue, safeOriginalQueue, saved.shuffleMode)

                withContext(Dispatchers.Main) {
                    exoPlayer?.setMediaItems(
                        activeQueue.map { it.toMediaItem() },
                        startIndex,
                        saved.positionMs.coerceAtLeast(0)
                    )
                    exoPlayer?.prepare()
                    exoPlayer?.repeatMode = saved.repeatMode
                    // Shuffle is managed by the active queue order; keep ExoPlayer's flag off.
                    exoPlayer?.shuffleModeEnabled = false
                }
            } finally {
                isRestoring.set(false)
            }
        }
    }

    // endregion

    private fun QueueItem.toMediaItem(): androidx.media3.common.MediaItem =
        androidx.media3.common.MediaItem.Builder()
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

    private data class Quintuple<A, B, C, D, E>(
        val first: A,
        val second: B,
        val third: C,
        val fourth: D,
        val fifth: E
    )

    companion object {
        private const val TAG = "PlaybackService"
        private const val NOTIFICATION_ID = 1001
    }
}
