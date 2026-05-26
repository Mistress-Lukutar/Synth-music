package com.synth.synthmusic.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synth.synthmusic.domain.repository.AlbumRepository
import com.synth.synthmusic.domain.repository.ArtistRepository
import com.synth.synthmusic.domain.repository.SongRepository
import com.synth.synthmusic.domain.usecase.ScanMusicUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for the library screen managing tabs and media scanning.
 */
class LibraryViewModel(
    private val songRepository: SongRepository,
    private val albumRepository: AlbumRepository,
    private val artistRepository: ArtistRepository,
    private val scanMusicUseCase: ScanMusicUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        combine(
            songRepository.observeAllSongs(),
            albumRepository.observeAllAlbums(),
            artistRepository.observeAllArtists()
        ) { songs, albums, artists ->
            Triple(songs, albums, artists)
        }.onEach { (songs, albums, artists) ->
            _uiState.update {
                it.copy(
                    songs = songs,
                    albums = albums,
                    artists = artists,
                    songCount = songs.size
                )
            }
        }.launchIn(viewModelScope)
    }

    fun onEvent(event: LibraryEvent) {
        when (event) {
            is LibraryEvent.SelectTab -> {
                _uiState.update { it.copy(selectedTab = event.tab) }
            }
            is LibraryEvent.ScanLibrary -> scanLibrary()
            is LibraryEvent.PlaySong -> {
                // playback logic will be handled in Phase 3
            }
        }
    }

    private fun scanLibrary() {
        viewModelScope.launch {
            _uiState.update { it.copy(isScanning = true, scanError = null) }
            scanMusicUseCase()
                .onSuccess { count ->
                    _uiState.update { it.copy(isScanning = false, songCount = count) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isScanning = false, scanError = error.message) }
                }
        }
    }
}
