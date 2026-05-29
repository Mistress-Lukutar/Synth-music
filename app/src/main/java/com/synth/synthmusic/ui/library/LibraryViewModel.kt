package com.synth.synthmusic.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synth.synthmusic.data.media.MediaPlaybackManager
import com.synth.synthmusic.domain.model.Playlist
import com.synth.synthmusic.domain.repository.AlbumRepository
import com.synth.synthmusic.domain.repository.ArtistRepository
import com.synth.synthmusic.domain.repository.PlaylistRepository
import com.synth.synthmusic.domain.repository.RecentlyPlayedCollectionRepository
import com.synth.synthmusic.domain.repository.SongRepository
import com.synth.synthmusic.domain.usecase.ScanMusicUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    private val albumRepository: AlbumRepository,
    private val artistRepository: ArtistRepository,
    private val playlistRepository: PlaylistRepository,
    private val scanMusicUseCase: ScanMusicUseCase,
    private val playbackManager: MediaPlaybackManager,
    private val recentlyPlayedRepository: RecentlyPlayedCollectionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    val currentPlayback = playbackManager.playbackState

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

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

        songRepository.observeFavoriteSongs()
            .onEach { list -> _uiState.update { it.copy(favoriteSongs = list) } }
            .launchIn(viewModelScope)

        songRepository.observeTopSongs()
            .onEach { list -> _uiState.update { it.copy(topSongs = list) } }
            .launchIn(viewModelScope)

        songRepository.observeRecentSongs()
            .onEach { list -> _uiState.update { it.copy(recentSongs = list) } }
            .launchIn(viewModelScope)

        recentlyPlayedRepository.observeRecent()
            .onEach { list -> _uiState.update { it.copy(recentCollections = list) } }
            .launchIn(viewModelScope)

        songRepository.observeHistory()
            .onEach { list -> _uiState.update { it.copy(historySongs = list) } }
            .launchIn(viewModelScope)

        songRepository.observeFolders()
            .onEach { list -> _uiState.update { it.copy(folders = list) } }
            .launchIn(viewModelScope)

        playlistRepository.observeAllPlaylists()
            .onEach { list -> _playlists.value = list }
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
        }
    }

    fun togglePlayPause() {
        playbackManager.playPause()
    }

    fun skipNext() {
        playbackManager.next()
    }

    fun playNext(songId: String) {
        viewModelScope.launch {
            val song = songRepository.getSongById(songId) ?: return@launch
            playbackManager.playNext(song)
        }
    }

    fun shuffleAll() {
        viewModelScope.launch {
            val songs = songRepository.observeAllSongs().first().shuffled()
            if (songs.isNotEmpty()) {
                playbackManager.playSongs(songs, 0)
            }
        }
    }

    fun addToQueue(songId: String) {
        viewModelScope.launch {
            val song = songRepository.getSongById(songId) ?: return@launch
            playbackManager.addToQueue(song)
        }
    }

    fun addSongToPlaylist(playlistId: Long, songId: String) {
        viewModelScope.launch {
            playlistRepository.addSongToPlaylist(playlistId, songId)
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
                .onSuccess { count ->
                    _uiState.update { it.copy(isScanning = false, songCount = count) }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(isScanning = false, scanError = error.message) }
                }
        }
    }
}
