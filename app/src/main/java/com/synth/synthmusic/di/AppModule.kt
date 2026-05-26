package com.synth.synthmusic.di

import android.content.Context
import androidx.room.Room
import com.synth.synthmusic.data.local.database.AppDatabase
import com.synth.synthmusic.data.local.datastore.SettingsDataStore
import com.synth.synthmusic.data.repository.SettingsRepositoryImpl
import com.synth.synthmusic.domain.repository.SettingsRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/**
 * Koin module providing core application dependencies.
 */
val appModule = module {
    single {
        Room.databaseBuilder(
            androidContext(),
            AppDatabase::class.java,
            "synth_music.db"
        ).build()
    }

    single { get<AppDatabase>().songDao() }
    single { get<AppDatabase>().albumDao() }
    single { get<AppDatabase>().artistDao() }
    single { get<AppDatabase>().playlistDao() }
    single { get<AppDatabase>().bookmarkDao() }
    single { get<AppDatabase>().eqPresetDao() }
    single { get<AppDatabase>().playbackStateDao() }

    single { SettingsDataStore(androidContext()) }
}

/**
 * Koin module binding repository interfaces to implementations.
 */
val dataModule = module {
    single<SettingsRepository> { SettingsRepositoryImpl(get()) }
}
