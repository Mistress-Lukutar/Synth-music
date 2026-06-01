package com.synth.synthmusic.di

import android.content.Context
import androidx.room.Room
import com.synth.synthmusic.data.local.database.AppDatabase
import com.synth.synthmusic.data.local.datastore.SettingsDataStore
import com.synth.synthmusic.data.media.AudioEffectsManager
import com.synth.synthmusic.data.media.MediaPlaybackManager
import com.synth.synthmusic.data.repository.AlbumRepositoryImpl
import com.synth.synthmusic.data.repository.ArtistRepositoryImpl
import com.synth.synthmusic.data.repository.BookmarkRepositoryImpl
import com.synth.synthmusic.data.repository.PlaylistRepositoryImpl
import com.synth.synthmusic.data.repository.SettingsRepositoryImpl
import com.synth.synthmusic.data.repository.SongRepositoryImpl
import com.synth.synthmusic.domain.repository.AlbumRepository
import com.synth.synthmusic.domain.repository.ArtistRepository
import com.synth.synthmusic.domain.repository.BookmarkRepository
import com.synth.synthmusic.domain.repository.PlaylistRepository
import com.synth.synthmusic.domain.repository.SettingsRepository
import com.synth.synthmusic.domain.repository.SongRepository
import com.synth.synthmusic.data.media.waveform.WaveformGenerator
import com.synth.synthmusic.data.media.waveform.WaveformPreloader
import com.synth.synthmusic.domain.usecase.BatchUpdateMetadataUseCase
import com.synth.synthmusic.domain.usecase.ExportPlaylistUseCase
import com.synth.synthmusic.domain.usecase.ImportPlaylistUseCase
import com.synth.synthmusic.domain.usecase.PlaySongUseCase
import com.synth.synthmusic.domain.usecase.ScanMusicUseCase
import com.synth.synthmusic.domain.usecase.UpdateMetadataUseCase
import com.synth.synthmusic.ui.bookmarks.BookmarkViewModel
import com.synth.synthmusic.ui.equalizer.EqualizerViewModel
import com.synth.synthmusic.ui.library.LibraryViewModel
import com.synth.synthmusic.ui.metadata.SongInfoViewModel
import com.synth.synthmusic.ui.nowplaying.NowPlayingViewModel
import com.synth.synthmusic.ui.playback.PlaybackViewModel
import com.synth.synthmusic.ui.playlists.PlaylistDetailViewModel
import com.synth.synthmusic.ui.playlists.PlaylistViewModel
import com.synth.synthmusic.ui.queue.QueueViewModel
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
        ).fallbackToDestructiveMigration(dropAllTables = true).build()
    }

    single { get<AppDatabase>().songDao() }
    single { get<AppDatabase>().albumDao() }
    single { get<AppDatabase>().artistDao() }
    single { get<AppDatabase>().playlistDao() }
    single { get<AppDatabase>().bookmarkDao() }
    single { get<AppDatabase>().eqPresetDao() }
    single { get<AppDatabase>().playbackStateDao() }
    single { get<AppDatabase>().waveformDataDao() }
    single { get<AppDatabase>().recentlyPlayedCollectionDao() }

    single { SettingsDataStore(androidContext()) }
    single<SongRepository> { SongRepositoryImpl(get()) }
    single<AlbumRepository> { AlbumRepositoryImpl(get()) }
    single<ArtistRepository> { ArtistRepositoryImpl(get()) }
    single<SettingsRepository> { SettingsRepositoryImpl(get()) }
    single<PlaylistRepository> { PlaylistRepositoryImpl(get(), get()) }
    single<BookmarkRepository> { BookmarkRepositoryImpl(get()) }
    single<com.synth.synthmusic.domain.repository.RecentlyPlayedCollectionRepository> {
        com.synth.synthmusic.data.repository.RecentlyPlayedCollectionRepositoryImpl(get())
    }
    single { MediaPlaybackManager(androidContext(), get(), get(), get(), get()) }
    single { AudioEffectsManager(get()) }
    single { WaveformGenerator(androidContext()) }
    single { WaveformPreloader(get(), get()) }

    single {
        ScanMusicUseCase(
            context = androidContext(),
            songRepository = get(),
            albumRepository = get(),
            artistRepository = get(),
            waveformPreloader = get(),
            waveformDataDao = get()
        )
    }

    single { PlaySongUseCase(get()) }
    single { UpdateMetadataUseCase(get()) }
    single { BatchUpdateMetadataUseCase(get(), get()) }
    single { ExportPlaylistUseCase(androidContext(), get()) }
    single { ImportPlaylistUseCase(androidContext(), get(), get()) }

    viewModel {
        LibraryViewModel(
            songRepository = get(),
            artistRepository = get(),
            playlistRepository = get(),
            scanMusicUseCase = get(),
            playbackManager = get(),
            recentlyPlayedRepository = get()
        )
    }

    viewModel {
        NowPlayingViewModel(
            playbackManager = get(),
            songRepository = get(),
            settingsRepository = get(),
            waveformGenerator = get(),
            waveformDataDao = get()
        )
    }

    viewModel {
        PlaybackViewModel(playbackManager = get())
    }

    viewModel {
        QueueViewModel(playbackManager = get())
    }

    viewModel {
        EqualizerViewModel(
            audioEffectsManager = get(),
            playbackManager = get()
        )
    }

    viewModel {
        PlaylistViewModel(
            playlistRepository = get(),
            songRepository = get()
        )
    }

    viewModel { (playlistId: Long) ->
        PlaylistDetailViewModel(
            playlistId = playlistId,
            playlistRepository = get(),
            playbackManager = get(),
            recentlyPlayedRepository = get()
        )
    }

    viewModel { (albumTitle: String, albumArtist: String) ->
        com.synth.synthmusic.ui.albums.AlbumDetailViewModel(
            albumTitle = albumTitle,
            albumArtist = albumArtist,
            albumRepository = get(),
            songRepository = get(),
            playbackManager = get(),
            recentlyPlayedRepository = get()
        )
    }

    viewModel { (artistName: String) ->
        com.synth.synthmusic.ui.artists.ArtistDetailViewModel(
            artistName = artistName,
            artistRepository = get(),
            albumRepository = get(),
            songRepository = get(),
            playbackManager = get(),
            recentlyPlayedRepository = get()
        )
    }

    viewModel { (songId: String) ->
        com.synth.synthmusic.ui.metadata.EditMetadataViewModel(
            songId = songId,
            songRepository = get()
        )
    }

    viewModel { (songIds: List<String>) ->
        com.synth.synthmusic.ui.metadata.BatchEditViewModel(
            songIds = songIds,
            songRepository = get(),
            batchUpdateMetadataUseCase = get()
        )
    }

    viewModel {
        SettingsViewModel(settingsRepository = get())
    }

    viewModel {
        BookmarkViewModel(bookmarkRepository = get())
    }

    viewModel {
        SearchViewModel(
            songRepository = get(),
            playbackManager = get()
        )
    }

    viewModel {
        SleepTimerViewModel(playbackManager = get())
    }

    viewModel { (songId: String) ->
        SongInfoViewModel(
            songId = songId,
            songRepository = get()
        )
    }
}

/**
 * Koin module for data layer bindings.
 */
val dataModule = module {
    // additional data bindings if needed
}
