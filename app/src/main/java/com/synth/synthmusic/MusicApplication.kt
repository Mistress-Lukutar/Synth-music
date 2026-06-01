package com.synth.synthmusic

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import android.util.Log
import coil.Coil
import coil.ImageLoader
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.synth.synthmusic.data.media.waveform.WaveformPreloader
import com.synth.synthmusic.di.appModule
import com.synth.synthmusic.di.dataModule
import com.synth.synthmusic.domain.repository.SongRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin

/**
 * Application entry point initializing Koin dependency injection.
 */
class MusicApplication : Application() {

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        initImageLoader()
        startKoin {
            androidContext(this@MusicApplication)
            modules(appModule, dataModule)
        }
        createNotificationChannel()
        resumeWaveformGeneration()
    }

    private fun resumeWaveformGeneration() {
        applicationScope.launch {
            try {
                val koin = GlobalContext.get()
                val waveformPreloader = koin.get<WaveformPreloader>()
                val songRepository = koin.get<SongRepository>()
                val songs = songRepository.getAllSongs()
                waveformPreloader.resumeIncomplete(songs)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to resume waveform generation", e)
            }
        }
    }

    private fun initImageLoader() {
        val imageLoader = ImageLoader.Builder(this)
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(250L * 1024 * 1024)
                    .build()
            }
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .crossfade(false)
            .build()
        Coil.setImageLoader(imageLoader)
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

    override fun onTerminate() {
        super.onTerminate()
        applicationScope.cancel()
    }

    companion object {
        const val PLAYBACK_NOTIFICATION_CHANNEL_ID = "playback_channel"
        private const val TAG = "MusicApplication"
    }
}
