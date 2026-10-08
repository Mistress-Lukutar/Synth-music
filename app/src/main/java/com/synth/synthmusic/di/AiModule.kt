package com.synth.synthmusic.di

import com.synth.synthmusic.data.ai.client.AiClientFactory
import com.synth.synthmusic.data.ai.client.AnthropicClient
import com.synth.synthmusic.data.ai.client.GeminiClient
import com.synth.synthmusic.data.ai.client.OpenAiClient
import com.synth.synthmusic.data.ai.ImageAttachmentLoader
import com.synth.synthmusic.data.ai.crypto.ApiKeyCipher
import com.synth.synthmusic.data.local.database.CoverBlacklistDao
import com.synth.synthmusic.domain.usecase.WriteArtworkToMp3UseCase
import com.synth.synthmusic.data.ai.tools.BrowseCollectionsTool
import com.synth.synthmusic.data.ai.tools.ManageCoverBlacklistTool
import com.synth.synthmusic.data.ai.tools.PurgeCoversTool
import com.synth.synthmusic.data.ai.tools.readEmbeddedCover
import com.synth.synthmusic.data.ai.tools.sha256Hex
import com.synth.synthmusic.data.ai.tools.GetPlaybackStateTool
import com.synth.synthmusic.data.ai.tools.GetPlaylistTool
import com.synth.synthmusic.data.ai.tools.GetSongsDetailsTool
import com.synth.synthmusic.data.ai.tools.AuditTracksTool
import com.synth.synthmusic.data.ai.tools.LibraryStatsTool
import com.synth.synthmusic.data.ai.tools.DownloadImageTool
import com.synth.synthmusic.data.ai.tools.DownloadStore
import com.synth.synthmusic.data.ai.tools.FetchUrlTool
import com.synth.synthmusic.data.ai.tools.GetLyricsOnlineTool
import com.synth.synthmusic.data.ai.tools.GetSongArtworkTool
import com.synth.synthmusic.data.ai.tools.DeleteFileTool
import com.synth.synthmusic.data.ai.tools.EmbedArtworkTool
import com.synth.synthmusic.data.ai.tools.ListMusicFoldersTool
import com.synth.synthmusic.data.ai.tools.ManagePlaylistTool
import com.synth.synthmusic.data.ai.tools.MoveFileTool
import com.synth.synthmusic.data.ai.tools.RemoveArtworkTool
import com.synth.synthmusic.data.ai.tools.RenameFileTool
import com.synth.synthmusic.data.ai.tools.SearchMusicMetadataTool
import com.synth.synthmusic.data.ai.tools.SearchReleaseTool
import com.synth.synthmusic.data.ai.tools.SearchSongsTool
import com.synth.synthmusic.data.ai.tools.WebSearchTool
import com.synth.synthmusic.data.ai.tools.SetLyricsTool
import com.synth.synthmusic.data.ai.tools.SetRatingFavoriteTool
import com.synth.synthmusic.data.ai.tools.TrashManager
import com.synth.synthmusic.data.ai.tools.UpdateSongsMetadataTool
import com.synth.synthmusic.data.local.datastore.AiSettingsDataStore
import com.synth.synthmusic.data.repository.AiActionLogRepositoryImpl
import com.synth.synthmusic.data.repository.AiAssistantRepositoryImpl
import com.synth.synthmusic.data.repository.AiChatRepositoryImpl
import com.synth.synthmusic.data.repository.AiModelRepositoryImpl
import com.synth.synthmusic.data.repository.AiProviderRepositoryImpl
import com.synth.synthmusic.data.repository.AiSettingsRepositoryImpl
import com.synth.synthmusic.domain.repository.AiAssistantRepository
import com.synth.synthmusic.domain.repository.AiChatRepository
import com.synth.synthmusic.domain.repository.AiModelRepository
import com.synth.synthmusic.domain.repository.AiProviderRepository
import com.synth.synthmusic.domain.repository.AiSettingsRepository
import com.synth.synthmusic.domain.repository.AiActionLogRepository
import com.synth.synthmusic.domain.repository.PlaylistRepository
import com.synth.synthmusic.domain.repository.SongRepository
import com.synth.synthmusic.data.media.PlaybackRepository
import com.synth.synthmusic.domain.usecase.ai.EmptyToolDispatcher
import com.synth.synthmusic.domain.usecase.ai.AiTool
import com.synth.synthmusic.domain.usecase.ai.FetchModelsUseCase
import com.synth.synthmusic.domain.usecase.ai.LibraryContextProvider
import com.synth.synthmusic.domain.usecase.ai.RunChatUseCase
import com.synth.synthmusic.domain.usecase.ai.SeedBuiltInAssistantsUseCase
import com.synth.synthmusic.domain.usecase.ai.TestConnectionUseCase
import com.synth.synthmusic.domain.usecase.ai.ToolDispatcherImpl
import kotlinx.coroutines.flow.first
import com.synth.synthmusic.domain.usecase.ai.ToolDispatcher
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.bind
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

/**
 * Koin module providing AI assistant dependencies: crypto, shared JSON,
 * HTTP stack, repositories, chat clients and ViewModels.
 */
val aiModule = module {

    single {
        Json {
            ignoreUnknownKeys = true
            encodeDefaults = false
        }
    }

    single {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .build()
    }

    single { ApiKeyCipher(androidContext()) }

    // --- Data layer ---
    single { AiSettingsDataStore(androidContext(), get()) }
    single<AiProviderRepository> { AiProviderRepositoryImpl(get(), get(), get()) }
    single<AiModelRepository> { AiModelRepositoryImpl(get()) }
    single<AiSettingsRepository> { AiSettingsRepositoryImpl(get()) }
    single<AiChatRepository> { AiChatRepositoryImpl(get(), get(), get(), get()) }
    single<AiAssistantRepository> { AiAssistantRepositoryImpl(get()) }
    single<AiActionLogRepository> { AiActionLogRepositoryImpl(get()) }
    single { ImageAttachmentLoader(androidContext()) }

    // --- Chat clients ---
    single { OpenAiClient(get(), get()) }
    single { AnthropicClient(get(), get()) }
    single { GeminiClient(get(), get()) }
    single { AiClientFactory(get(), get(), get()) }

    // --- Use cases ---
    single { FetchModelsUseCase() }
    single { TestConnectionUseCase() }
    single { SeedBuiltInAssistantsUseCase(get()) }
    single<ToolDispatcher> {
        ToolDispatcherImpl(
            tools = getAll(),
            actionLogRepository = get(),
            settingsRepository = get()
        )
    }
    single { LibraryContextProvider(get(), get()) }
    single {
        RunChatUseCase(
            chatRepository = get(),
            providerRepository = get(),
            settingsRepository = get(),
            assistantRepository = get(),
            toolDispatcher = get(),
            clientFactory = get(),
            libraryContextProvider = get()
        )
    }

    // --- AI tools ---
    // Each tool is registered under its concrete type with `bind AiTool`
    // so all of them are discoverable via getAll<AiTool>(). Registering 20+
    // definitions with the same primary type AiTool makes the later ones
    // override the earlier ones, and getAll() would return a single tool.
    single { TrashManager(androidContext(), get()) }
    single { ListMusicFoldersTool(songRepository = get()) } bind AiTool::class
    single { RenameFileTool(appContext = androidContext(), songRepository = get()) } bind AiTool::class
    single { MoveFileTool(appContext = androidContext(), songRepository = get()) } bind AiTool::class
    single {
        DeleteFileTool(
            appContext = androidContext(),
            songRepository = get(),
            playlistRepository = get(),
            trashManager = get()
        )
    } bind AiTool::class
    single {
        UpdateSongsMetadataTool(
            appContext = androidContext(),
            songRepository = get(),
            updateMetadata = get()
        )
    } bind AiTool::class
    single {
        SetLyricsTool(
            appContext = androidContext(),
            songRepository = get(),
            updateMetadata = get()
        )
    } bind AiTool::class
    single { SetRatingFavoriteTool(songRepository = get()) } bind AiTool::class
    single {
        EmbedArtworkTool(
            appContext = androidContext(),
            songRepository = get(),
            writeArtwork = get(),
            coverCache = get(),
            downloadStore = get()
        )
    } bind AiTool::class
    single {
        RemoveArtworkTool(
            appContext = androidContext(),
            songRepository = get(),
            writeArtwork = get()
        )
    } bind AiTool::class
    single { ManagePlaylistTool(playlistRepository = get()) } bind AiTool::class
    single { DownloadStore(androidContext()) }
    single { SearchMusicMetadataTool() } bind AiTool::class
    single { SearchReleaseTool() } bind AiTool::class
    single { GetLyricsOnlineTool() } bind AiTool::class
    single { WebSearchTool(settingsRepository = get()) } bind AiTool::class
    single { FetchUrlTool() } bind AiTool::class
    single { DownloadImageTool(downloadStore = get()) } bind AiTool::class
    single { GetSongArtworkTool(appContext = androidContext(), songRepository = get()) } bind AiTool::class
    single {
        SearchSongsTool(allSongs = { get<SongRepository>().getAllSongs() })
    } bind AiTool::class
    single {
        GetSongsDetailsTool(getSongsByIds = { ids ->
            get<SongRepository>().getSongsByIds(ids)
        })
    } bind AiTool::class
    single {
        val songRepository = get<SongRepository>()
        val playlistRepository = get<PlaylistRepository>()
        BrowseCollectionsTool(
            allSongs = { songRepository.getAllSongs() },
            genres = { songRepository.observeGenres().first() },
            playlists = playlistSummaries(playlistRepository)
        )
    } bind AiTool::class
    single {
        val playlistRepository = get<PlaylistRepository>()
        GetPlaylistTool(
            playlistSongs = { playlistId ->
                playlistRepository.observePlaylistSongs(playlistId).first()
            },
            allPlaylists = playlistSummaries(playlistRepository)
        )
    } bind AiTool::class
    single {
        LibraryStatsTool(allSongs = { get<SongRepository>().getAllSongs() })
    } bind AiTool::class
    single {
        val songRepository = get<SongRepository>()
        val playlistRepository = get<PlaylistRepository>()
        AuditTracksTool(
            allSongs = { songRepository.getAllSongs() },
            playlistSongs = { playlistId ->
                playlistRepository.observePlaylistSongs(playlistId).first()
            },
            allPlaylists = playlistSummaries(playlistRepository),
            embeddedCoverHash = { song ->
                readEmbeddedCover(song)?.let { sha256Hex(it.bytes) }
            },
            isCoverBlacklisted = { hash ->
                get<CoverBlacklistDao>().exists(hash)
            }
        )
    } bind AiTool::class
    single {
        ManageCoverBlacklistTool(
            blacklistDao = get(),
            readCover = { song -> readEmbeddedCover(song) },
            getSongById = { id ->
                get<SongRepository>().getSongById(id)
            }
        )
    } bind AiTool::class
    single {
        val songRepository = get<SongRepository>()
        val playlistRepository = get<PlaylistRepository>()
        PurgeCoversTool(
            blacklistDao = get(),
            allSongs = { songRepository.getAllSongs() },
            playlistSongs = { playlistId ->
                playlistRepository.observePlaylistSongs(playlistId).first()
            },
            allPlaylists = playlistSummaries(playlistRepository),
            readCover = { song -> readEmbeddedCover(song) },
            removeCover = { song ->
                get<WriteArtworkToMp3UseCase>().removeArtwork(song)
            }
        )
    } bind AiTool::class
    single {
        val playbackRepository = get<PlaybackRepository>()
        GetPlaybackStateTool(
            playbackSnapshot = {
                val state = playbackRepository.playbackState.value
                Triple(state.currentSongId, state.isPlaying, state.shuffleEnabled)
            },
            getSongById = { id ->
                get<SongRepository>().getSongById(id)
            }
        )
    } bind AiTool::class

    // --- ViewModels ---
    viewModel { com.synth.synthmusic.ui.settings.ai.AiSettingsViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel {
        com.synth.synthmusic.ui.ai.AiViewModel(
            chatRepository = get(),
            assistantRepository = get(),
            providerRepository = get(),
            modelRepository = get(),
            runChatUseCase = get(),
            imageAttachmentLoader = get(),
            actionLogRepository = get(),
            aiSettingsDataStore = get()
        )
    }
    viewModel { (assistantId: Long?) ->
        com.synth.synthmusic.ui.ai.assistant.AssistantEditorViewModel(
            assistantId = assistantId,
            assistantRepository = get()
        )
    }
}

/**
 * Live playlist summaries (id, name, song count) shared by the library tools.
 * Counts are recomputed from the join table on every call because the
 * entity's song_count column is only maintained on some mutation paths.
 */
private fun playlistSummaries(
    playlistRepository: PlaylistRepository
): suspend () -> List<Triple<Long, String, Int>> = {
    playlistRepository.observeAllPlaylists().first().map { playlist ->
        Triple(
            playlist.id,
            playlist.name,
            playlistRepository.observePlaylistSongs(playlist.id).first().size
        )
    }
}
