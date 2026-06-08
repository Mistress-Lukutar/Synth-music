package com.synth.synthmusic.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import com.synth.synthmusic.data.media.MediaPlaybackManager
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/**
 * Receiver that pauses playback when audio output changes from headphones
 * to the device's built-in speakers (e.g. headphone unplug).
 *
 * Registered in [AndroidManifest.xml] with the [AudioManager.ACTION_AUDIO_BECOMING_NOISY]
 * intent filter so it works even when the app is in the background.
 */
class AudioBecomingNoisyReceiver : BroadcastReceiver(), KoinComponent {

    private val playbackManager: MediaPlaybackManager by inject()

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
            playbackManager.pause()
        }
    }
}
