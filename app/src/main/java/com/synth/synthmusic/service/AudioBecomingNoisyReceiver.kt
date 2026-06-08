package com.synth.synthmusic.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build

/**
 * Receiver that pauses playback when audio output changes from headphones
 * to the device's built-in speakers (e.g. headphone unplug).
 *
 * Registered in [AndroidManifest.xml] with the [AudioManager.ACTION_AUDIO_BECOMING_NOISY]
 * intent filter so it works even when the app is in the background.
 */
class AudioBecomingNoisyReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
            val pauseIntent = Intent(context, PlaybackService::class.java).apply {
                action = ACTION_PAUSE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(pauseIntent)
            } else {
                context.startService(pauseIntent)
            }
        }
    }

    companion object {
        const val ACTION_PAUSE = "com.synth.synthmusic.ACTION_PAUSE"
    }
}
