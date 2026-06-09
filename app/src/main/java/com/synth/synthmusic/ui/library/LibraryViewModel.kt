package com.synth.synthmusic.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synth.synthmusic.data.media.PlaybackRepository
import com.synth.synthmusic.domain.repository.ArtistRepository
import com.synth.synthmusic.domain.repository.RecentlyPlayedCollectionRepository
import com.synth.synthmusic.domain.repository.SongRepository
import com.synth.synthmusic.domain.usecase.ScanMusicUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.ExperimentalCoroutinesApi

/**
 * ViewModel for the library screen managing tabs and media scanning.
 */
@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val songRepository: SongRepository,
    private val artistRepository: ArtistRepository,
    private val scanMusicUseCase: ScanMusicUseCase,
    private val playbackRepository: PlaybackRepository,
    private val recentlyPlayedRepository: RecentlyPlayedCollectionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    val currentPlayback = playbackRepository.playbackState

    init {
        artistRepository.observeAllArtists()
            .onEach { artists ->
                _uiState.update { it.copy(artists = artists) }
            }
            .launchIn(viewModelScope)

        recentlyPlayedRepository.observeRecent()
            .onEach { list -> _uiState.update { it.copy(recentCollections = list) } }
            .launchIn(viewModelScope)

        songRepository.observeGenres()
            .onEach { genres -> _uiState.update { it.copy(genres = genres) } }
            .launchIn(viewModelScope)

        // Debounced search flow
        _uiState
            .debounce(300)
            .flatMapLatest { state ->
                val query = state.searchQuery
                if (query.isBlank()) {
                    flowOf(emptyList())
                } else {
                    songRepository.searchSongs(query)
                }
            }
            .onEach { results -> _uiState.update { it.copy(searchResults = results) } }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: LibraryEvent) {
        when (event) {
            is LibraryEvent.SelectTab -> {
                _uiState.update { it.copy(selectedTab = event.tab) }
            }
            is LibraryEvent.ScanLibrary -> scanLibrary()
            is LibraryEvent.PlaySong -> playSong(event.songId)
            is LibraryEvent.SearchQueryChanged -> {
                _uiState.update { it.copy(searchQuery = event.query) }
            }
        }
    }

    fun playNext(songId: String) {
        viewModelScope.launch {
            val song = songRepository.getSongById(songId) ?: return@launch
            playbackRepository.playNext(song)
        }
    }

    fun addToQueue(songId: String) {
        viewModelScope.launch {
            val song = songRepository.getSongById(songId) ?: return@launch
            playbackRepository.addToQueue(song)
        }
    }

    private fun playSong(songId: String) {
        viewModelScope.launch {
            val songs = songRepository.observeAllSongs().first()
            val index = songs.indexOfFirst { it.id == songId }
            if (index != -1) {
                playbackRepository.playSongs(songs, index)
            }
        }
    }

    private fun scanLibrary() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, scanError = null) }
            scanMusicUseCase()
                .onSuccess {
                    _uiState.update { it.copy(isScanning = false) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isScanning = false, scanError = error.message) }
                }
        }
    }
}
