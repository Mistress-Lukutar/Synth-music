package com.synth.synthmusic.service

import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.synth.synthmusic.MainActivity
import com.synth.synthmusic.MusicApplication
import com.synth.synthmusic.R
import com.synth.synthmusic.data.media.MediaPlaybackManager
import org.koin.android.ext.android.inject

/**
 * Foreground service providing background playback via Media3 MediaSession.
 */
@UnstableApi
class PlaybackService : MediaSessionService() {

    private val playbackManager: MediaPlaybackManager by inject()
    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        // Provider must be set before super.onCreate() so MediaNotificationManager
        // is initialized with the correct channel and icon.
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

        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        mediaSession = MediaSession.Builder(this, playbackManager.player)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()

        // Register the session with the service so MediaNotificationManager
        // starts tracking it and can post the playback notification.
        mediaSession?.let {
            addSession(it)
            Log.d(TAG, "MediaSession added to service")
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
        mediaSession?.let { removeSession(it) }
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player ?: run {
            stopSelf()
            return
        }
        if (!player.playWhenReady || player.playbackState == androidx.media3.common.Player.STATE_ENDED) {
            stopSelf()
        }
    }

    companion object {
        private const val TAG = "PlaybackService"
        private const val NOTIFICATION_ID = 1001
    }
}
