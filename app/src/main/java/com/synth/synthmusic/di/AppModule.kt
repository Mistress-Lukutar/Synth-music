package com.synth.synthmusic.di

import android.content.Context
import androidx.room.Room
import com.synth.synthmusic.data.local.database.AppDatabase
import com.synth.synthmusic.data.local.datastore.SettingsDataStore
import com.synth.synthmusic.data.repository.AlbumRepositoryImpl
import com.synth.synthmusic.data.repository.ArtistRepositoryImpl
import com.synth.synthmusic.data.repository.SettingsRepositoryImpl
import com.synth.synthmusic.data.repository.SongRepositoryImpl
import com.synth.synthmusic.domain.repository.AlbumRepository
import com.synth.synthmusic.domain.repository.ArtistRepository
import com.synth.synthmusic.domain.repository.SettingsRepository
import com.synth.synthmusic.domain.repository.SongRepository
import com.synth.synthmusic.domain.usecase.ScanMusicUseCase
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
    single<SongRepository> { SongRepositoryImpl(get()) }
    single<AlbumRepository> { AlbumRepositoryImpl(get()) }
    single<ArtistRepository> { ArtistRepositoryImpl(get()) }
    single<SettingsRepository> { SettingsRepositoryImpl(get()) }

    single {
        ScanMusicUseCase(
            context = androidContext(),
            songRepository = get(),
            albumRepository = get(),
            artistRepository = get()
        )
    }
}

/**
 * Koin module for data layer bindings.
 */
val dataModule = module {
    // additional data bindings if needed
}
