package com.synth.synthmusic.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synth.synthmusic.domain.repository.PlaylistRepository
import com.synth.synthmusic.domain.repository.SongRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.io.File

/**
 * ViewModel for playlist management screens.
 */
class PlaylistViewModel(
    private val playlistRepository: PlaylistRepository,
    private val songRepository: SongRepository
) : ViewModel() {

    private val _playlists = MutableStateFlow<List<com.synth.synthmusic.domain.model.Playlist>>(emptyList())
    val playlists: StateFlow<List<com.synth.synthmusic.domain.model.Playlist>> = _playlists.asStateFlow()

    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog.asStateFlow()

    private val _folders = MutableStateFlow<List<String>>(emptyList())
    val folders: StateFlow<List<String>> = _folders.asStateFlow()

    init {
        playlistRepository.observeAllPlaylists()
            .onEach { list -> _playlists.value = list }
            .launchIn(viewModelScope)

        songRepository.observeFolders()
            .onEach { list -> _folders.value = list }
            .launchIn(viewModelScope)
    }

    fun openCreateDialog() {
        _showCreateDialog.value = true
    }

    fun dismissCreateDialog() {
        _showCreateDialog.value = false
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            playlistRepository.createPlaylist(name)
            _showCreateDialog.value = false
        }
    }

    fun createPlaylistFromFolder(folderPath: String) {
        viewModelScope.launch {
            val name = File(folderPath).name.ifBlank { folderPath }
            val playlistId = playlistRepository.createPlaylist(name)
            val songs = songRepository.observeSongsByFolder(folderPath).first()
            songs.forEach { song ->
                playlistRepository.addSongToPlaylist(playlistId, song.id)
            }
            _showCreateDialog.value = false
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlistId)
        }
    }

    fun renamePlaylist(playlistId: Long, name: String) {
        viewModelScope.launch {
            playlistRepository.renamePlaylist(playlistId, name)
        }
    }
}
