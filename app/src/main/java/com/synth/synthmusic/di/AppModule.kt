package com.synth.synthmusic.di

import androidx.room.Room
import com.synth.synthmusic.data.local.database.AppDatabase
import com.synth.synthmusic.data.local.datastore.SettingsDataStore
import com.synth.synthmusic.data.media.PlaybackRepository
import com.synth.synthmusic.data.media.waveform.WaveformGenerator
import com.synth.synthmusic.data.media.waveform.WaveformPreloader
import com.synth.synthmusic.data.repository.AlbumRepositoryImpl
import com.synth.synthmusic.data.repository.ArtistRepositoryImpl
import com.synth.synthmusic.data.repository.PlaylistRepositoryImpl
import com.synth.synthmusic.data.repository.SettingsRepositoryImpl
import com.synth.synthmusic.data.repository.SongRepositoryImpl
import com.synth.synthmusic.domain.repository.AlbumRepository
import com.synth.synthmusic.domain.repository.ArtistRepository
import com.synth.synthmusic.domain.repository.PlaylistRepository
import com.synth.synthmusic.domain.repository.SettingsRepository
import com.synth.synthmusic.domain.repository.SongRepository
import com.synth.synthmusic.domain.usecase.CheckRecordAudioPermissionUseCase
import com.synth.synthmusic.domain.usecase.CheckWritePermissionUseCase
import com.synth.synthmusic.domain.usecase.ExportPlaylistUseCase
import com.synth.synthmusic.domain.usecase.GenerateArtworkUseCase
import com.synth.synthmusic.domain.usecase.ImportPlaylistUseCase
import com.synth.synthmusic.domain.usecase.LoadArtworkBytesUseCase
import com.synth.synthmusic.domain.usecase.PlaySongUseCase
import com.synth.synthmusic.domain.usecase.ScanMusicUseCase
import com.synth.synthmusic.domain.usecase.UpdateMetadataUseCase
import com.synth.synthmusic.domain.usecase.WriteArtworkToMp3UseCase
import com.synth.synthmusic.ui.library.LibraryViewModel
import com.synth.synthmusic.ui.metadata.SongInfoViewModel
import com.synth.synthmusic.ui.nowplaying.NowPlayingViewModel
import com.synth.synthmusic.ui.playback.PlaybackViewModel
import com.synth.synthmusic.ui.playlists.PlaylistDetailViewModel
import com.synth.synthmusic.ui.playlists.PlaylistViewModel
import com.synth.synthmusic.ui.genres.GenreDetailViewModel
import com.synth.synthmusic.ui.search.SearchViewModel
import com.synth.synthmusic.ui.settings.SettingsViewModel
import com.synth.synthmusic.ui.sleeptimer.SleepTimerViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
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
        )
            .addMigrations(
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11,
                AppDatabase.MIGRATION_11_12,
                AppDatabase.MIGRATION_12_13
            )
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()
    }

    single { get<AppDatabase>().songDao() }
    single { get<AppDatabase>().albumDao() }
    single { get<AppDatabase>().artistDao() }
    single { get<AppDatabase>().playlistDao() }
    single { get<AppDatabase>().playbackStateDao() }
    single { get<AppDatabase>().playbackQueueItemDao() }
    single { get<AppDatabase>().playbackOriginalQueueItemDao() }
    single { get<AppDatabase>().waveformDataDao() }
    single { get<AppDatabase>().recentlyPlayedCollectionDao() }

    single { SettingsDataStore(androidContext()) }
    single<SongRepository> { SongRepositoryImpl(get()) }
    single<AlbumRepository> { AlbumRepositoryImpl(get()) }
    single<ArtistRepository> { ArtistRepositoryImpl(get()) }
    single<SettingsRepository> { SettingsRepositoryImpl(get()) }
    single<PlaylistRepository> { PlaylistRepositoryImpl(get(), get()) }
    single<com.synth.synthmusic.domain.repository.RecentlyPlayedCollectionRepository> {
        com.synth.synthmusic.data.repository.RecentlyPlayedCollectionRepositoryImpl(get())
    }
    single { PlaybackRepository(androidContext(), get()) }
    single { WaveformGenerator(androidContext()) }
    single { WaveformPreloader(get(), get()) }
    single { com.synth.synthmusic.data.local.cover.CoverCache(androidContext()) }

    single {
        ScanMusicUseCase(
            context = androidContext(),
            songRepository = get(),
            albumRepository = get(),
            artistRepository = get(),
            playlistRepository = get(),
            waveformPreloader = get(),
            waveformDataDao = get(),
            coverCache = get()
        )
    }

    single { PlaySongUseCase(get()) }
    single { CheckWritePermissionUseCase(androidContext()) }
    single { CheckRecordAudioPermissionUseCase(androidContext()) }
    single { WriteArtworkToMp3UseCase(get(), get()) }
    single { UpdateMetadataUseCase(get(), get()) }
    single { GenerateArtworkUseCase() }
    single { LoadArtworkBytesUseCase(androidContext()) }
    single { ExportPlaylistUseCase(androidContext(), get()) }
    single { ImportPlaylistUseCase(androidContext(), get(), get()) }

    viewModel {
        LibraryViewModel(
            songRepository = get(),
            artistRepository = get(),
            playbackRepository = get(),
            recentlyPlayedRepository = get()
        )
    }

    viewModel {
        NowPlayingViewModel(
            playbackRepository = get(),
            songRepository = get(),
            playlistRepository = get(),
            settingsRepository = get(),
            waveformGenerator = get(),
            waveformDataDao = get(),
            writeArtworkUseCase = get(),
            checkWritePermission = get(),
            checkRecordAudioPermission = get()
        )
    }

    viewModel {
        PlaybackViewModel(playbackRepository = get())
    }



    viewModel {
        PlaylistViewModel(
            playlistRepository = get(),
            songRepository = get(),
            coverCache = get(),
            generateArtworkUseCase = get(),
            loadArtworkBytesUseCase = get()
        )
    }

    viewModel { (playlistId: Long) ->
        PlaylistDetailViewModel(
            playlistId = playlistId,
            playlistRepository = get(),
            playbackRepository = get(),
            recentlyPlayedRepository = get(),
            coverCache = get(),
            generateArtworkUseCase = get(),
            loadArtworkBytesUseCase = get()
        )
    }

    viewModel { (albumTitle: String, albumArtist: String) ->
        com.synth.synthmusic.ui.albums.AlbumDetailViewModel(
            albumTitle = albumTitle,
            albumArtist = albumArtist,
            albumRepository = get(),
            songRepository = get(),
            playbackRepository = get(),
            recentlyPlayedRepository = get(),
            coverCache = get(),
            generateArtworkUseCase = get(),
            loadArtworkBytesUseCase = get()
        )
    }

    viewModel { (artistName: String) ->
        com.synth.synthmusic.ui.artists.ArtistDetailViewModel(
            artistName = artistName,
            artistRepository = get(),
            albumRepository = get(),
            songRepository = get(),
            playbackRepository = get(),
            recentlyPlayedRepository = get(),
            coverCache = get(),
            generateArtworkUseCase = get(),
            loadArtworkBytesUseCase = get()
        )
    }

    viewModel { (songId: String) ->
        com.synth.synthmusic.ui.metadata.EditMetadataViewModel(
            songId = songId,
            songRepository = get(),
            checkWritePermission = get(),
            writeArtworkUseCase = get(),
            updateMetadataUseCase = get()
        )
    }


    viewModel {
        SettingsViewModel(
            settingsRepository = get(),
            scanMusicUseCase = get()
        )
    }

    viewModel {
        SearchViewModel(
            songRepository = get(),
            playbackRepository = get()
        )
    }


    viewModel { (genre: String) ->
        GenreDetailViewModel(
            genre = genre,
            songRepository = get(),
            playbackRepository = get()
        )
    }

    viewModel {
        SleepTimerViewModel(playbackRepository = get())
    }

    viewModel { (songId: String) ->
        SongInfoViewModel(
            songId = songId,
            songRepository = get()
        )
    }
}


