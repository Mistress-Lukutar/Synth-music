package com.synth.synthmusic.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synth.synthmusic.data.media.MediaPlaybackManager
import com.synth.synthmusic.domain.repository.ArtistRepository
import com.synth.synthmusic.domain.repository.PlaylistRepository
import com.synth.synthmusic.domain.repository.RecentlyPlayedCollectionRepository
import com.synth.synthmusic.domain.repository.SongRepository
import com.synth.synthmusic.domain.usecase.ScanMusicUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the library screen managing tabs and media scanning.
 */
class LibraryViewModel(
    private val songRepository: SongRepository,
    private val artistRepository: ArtistRepository,
    private val playlistRepository: PlaylistRepository,
    private val scanMusicUseCase: ScanMusicUseCase,
    private val playbackManager: MediaPlaybackManager,
    private val recentlyPlayedRepository: RecentlyPlayedCollectionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    val currentPlayback = playbackManager.playbackState


    init {
        artistRepository.observeAllArtists()
            .onEach { artists ->
                _uiState.update { it.copy(artists = artists) }
            }
            .launchIn(viewModelScope)

        songRepository.observeTopSongs()
            .onEach { list -> _uiState.update { it.copy(topSongs = list) } }
            .launchIn(viewModelScope)

        recentlyPlayedRepository.observeRecent()
            .onEach { list -> _uiState.update { it.copy(recentCollections = list) } }
            .launchIn(viewModelScope)

        songRepository.observeHistory()
            .onEach { list -> _uiState.update { it.copy(historySongs = list) } }
            .launchIn(viewModelScope)

        playbackManager.currentQueue
            .onEach { list -> _uiState.update { it.copy(queueSongs = list) } }
            .launchIn(viewModelScope)
    }

    fun onEvent(event: LibraryEvent) {
        when (event) {
            is LibraryEvent.SelectTab -> {
                _uiState.update { it.copy(selectedTab = event.tab) }
            }
            is LibraryEvent.ScanLibrary -> scanLibrary()
            is LibraryEvent.PlaySong -> playSong(event.songId)
            is LibraryEvent.ClearQueue -> playbackManager.clearQueue()
        }
    }

    fun playNext(songId: String) {
        viewModelScope.launch {
            val song = songRepository.getSongById(songId) ?: return@launch
            playbackManager.playNext(song)
        }
    }

    fun addToQueue(songId: String) {
        viewModelScope.launch {
            val song = songRepository.getSongById(songId) ?: return@launch
            playbackManager.addToQueue(song)
        }
    }

    fun playQueueItem(index: Int) {
        playbackManager.playQueueItem(index)
    }

    private fun playSong(songId: String) {
        viewModelScope.launch {
            val songs = songRepository.observeAllSongs().first()
            val index = songs.indexOfFirst { it.id == songId }
            if (index != -1) {
                playbackManager.playSongs(songs, index)
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
