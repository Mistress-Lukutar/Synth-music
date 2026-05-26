package com.synth.synthmusic.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.synth.synthmusic.MainActivity
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
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onDestroy() {
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
}
