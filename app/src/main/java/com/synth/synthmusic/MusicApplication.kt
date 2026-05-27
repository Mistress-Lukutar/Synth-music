package com.synth.synthmusic

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.synth.synthmusic.di.appModule
import com.synth.synthmusic.di.dataModule
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

/**
 * Application entry point initializing Koin dependency injection.
 */
class MusicApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@MusicApplication)
            modules(appModule, dataModule)
        }
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                PLAYBACK_NOTIFICATION_CHANNEL_ID,
                "Media Playback",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Playback controls and progress"
                setShowBadge(false)
            }
            getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }

    companion object {
        const val PLAYBACK_NOTIFICATION_CHANNEL_ID = "playback_channel"
    }
}
