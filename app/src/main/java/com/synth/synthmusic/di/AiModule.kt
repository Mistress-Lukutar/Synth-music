package com.synth.synthmusic.di

import com.synth.synthmusic.data.ai.client.AiClientFactory
import com.synth.synthmusic.data.ai.client.AnthropicClient
import com.synth.synthmusic.data.ai.client.GeminiClient
import com.synth.synthmusic.data.ai.client.OpenAiClient
import com.synth.synthmusic.data.ai.ImageAttachmentLoader
import com.synth.synthmusic.data.ai.crypto.ApiKeyCipher
import com.synth.synthmusic.data.ai.tools.BrowseCollectionsTool
import com.synth.synthmusic.data.ai.tools.GetPlaybackStateTool
import com.synth.synthmusic.data.ai.tools.GetPlaylistTool
import com.synth.synthmusic.data.ai.tools.GetSongsDetailsTool
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
    single<AiChatRepository> { AiChatRepositoryImpl(get(), get(), get(), get(), get()) }
    single<AiAssistantRepository> { AiAssistantRepositoryImpl(get()) }
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
    single {
        ToolDispatcherImpl(
            tools = getAll(),
            actionLogRepository = get()
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
    single { TrashManager(androidContext(), get()) }
    single<AiTool> {
        ListMusicFoldersTool(songRepository = get())
    }
    single<AiTool> {
        RenameFileTool(appContext = androidContext(), songRepository = get())
    }
    single<AiTool> {
        MoveFileTool(appContext = androidContext(), songRepository = get())
    }
    single<AiTool> {
        DeleteFileTool(
            appContext = androidContext(),
            songRepository = get(),
            playlistRepository = get(),
            trashManager = get()
        )
    }
    single<AiTool> {
        UpdateSongsMetadataTool(
            appContext = androidContext(),
            songRepository = get(),
            updateMetadata = get()
        )
    }
    single<AiTool> {
        SetLyricsTool(
            appContext = androidContext(),
            songRepository = get(),
            updateMetadata = get()
        )
    }
    single<AiTool> {
        SetRatingFavoriteTool(songRepository = get())
    }
    single<AiTool> {
        EmbedArtworkTool(
            appContext = androidContext(),
            songRepository = get(),
            writeArtwork = get(),
            coverCache = get(),
            downloadStore = get()
        )
    }
    single<AiTool> {
        RemoveArtworkTool(
            appContext = androidContext(),
            songRepository = get(),
            writeArtwork = get()
        )
    }
    single<AiTool> {
        ManagePlaylistTool(playlistRepository = get())
    }
    single { DownloadStore(androidContext()) }
    single<AiTool> { SearchMusicMetadataTool() }
    single<AiTool> { SearchReleaseTool() }
    single<AiTool> { GetLyricsOnlineTool() }
    single<AiTool> { WebSearchTool(settingsRepository = get()) }
    single<AiTool> { FetchUrlTool() }
    single<AiTool> { DownloadImageTool(downloadStore = get()) }
    single<AiTool> {
        GetSongArtworkTool(appContext = androidContext(), songRepository = get())
    }
    single<AiTool> {
        SearchSongsTool(searchSongs = { query ->
            get<SongRepository>().searchSongs(query).first()
        })
    }
    single<AiTool> {
        GetSongsDetailsTool(getSongsByIds = { ids ->
            get<SongRepository>().getSongsByIds(ids)
        })
    }
    single<AiTool> {
        val songRepository = get<SongRepository>()
        BrowseCollectionsTool(
            allSongs = { songRepository.getAllSongs() },
            genres = { songRepository.observeGenres().first() },
            playlists = {
                get<PlaylistRepository>().observeAllPlaylists().first().map { playlist ->
                    Triple(
                        playlist.id,
                        playlist.name,
                        get<PlaylistRepository>().observePlaylistSongs(playlist.id)
                            .first().size
                    )
                }
            }
        )
    }
    single<AiTool> {
        GetPlaylistTool(playlistSongs = { playlistId ->
            get<PlaylistRepository>().observePlaylistSongs(playlistId).first()
        })
    }
    single<AiTool> {
        LibraryStatsTool(allSongs = { get<SongRepository>().getAllSongs() })
    }
    single<AiTool> {
        val playbackRepository = get<PlaybackRepository>()
        GetPlaybackStateTool(playbackSnapshot = {
            val state = playbackRepository.playbackState.value
            Triple(state.currentSongId, state.isPlaying, state.shuffleEnabled)
        })
    }

    // --- ViewModels ---
    viewModel { com.synth.synthmusic.ui.settings.ai.AiSettingsViewModel(get(), get(), get(), get(), get(), get(), get(), get(), get(), get()) }
    viewModel { com.synth.synthmusic.ui.ai.AiHomeViewModel(get(), get(), get()) }
    viewModel { (chatId: Long) ->
        com.synth.synthmusic.ui.ai.chat.AiChatViewModel(
            chatId = chatId,
            chatRepository = get(),
            assistantRepository = get(),
            modelRepository = get(),
            runChatUseCase = get(),
            imageAttachmentLoader = get(),
            actionLogRepository = get()
        )
    }
    viewModel { (assistantId: Long?) ->
        com.synth.synthmusic.ui.ai.assistant.AssistantEditorViewModel(
            assistantId = assistantId,
            assistantRepository = get()
        )
    }
}
