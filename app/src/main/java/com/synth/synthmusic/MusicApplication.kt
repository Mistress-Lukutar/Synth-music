package com.synth.synthmusic

import android.app.Application
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
    }
}
