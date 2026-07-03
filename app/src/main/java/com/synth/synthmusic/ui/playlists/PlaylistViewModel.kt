package com.synth.synthmusic.ui.playlists

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synth.synthmusic.data.local.cover.CoverCache
import com.synth.synthmusic.domain.model.GeneratedArtworkConfig
import com.synth.synthmusic.domain.model.Playlist
import com.synth.synthmusic.domain.repository.PlaylistRepository
import com.synth.synthmusic.domain.repository.SongRepository
import com.synth.synthmusic.domain.usecase.GenerateArtworkUseCase
import com.synth.synthmusic.domain.usecase.LoadArtworkBytesUseCase
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
    private val songRepository: SongRepository,
    private val coverCache: CoverCache,
    private val generateArtworkUseCase: GenerateArtworkUseCase,
    private val loadArtworkBytesUseCase: LoadArtworkBytesUseCase
) : ViewModel() {

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

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
        val playlist = _playlists.value.find { it.id == playlistId } ?: return
        if (playlist.isFixed) return
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlistId)
        }
    }

    fun renamePlaylist(playlistId: Long, name: String) {
        val playlist = _playlists.value.find { it.id == playlistId } ?: return
        if (playlist.isFixed) return
        viewModelScope.launch {
            playlistRepository.renamePlaylist(playlistId, name)
        }
    }

    /**
     * Updates a playlist artwork with the given image bytes, or removes it when null.
     */
    fun updateArtwork(playlistId: Long, bytes: ByteArray?) {
        val playlist = _playlists.value.find { it.id == playlistId } ?: return
        viewModelScope.launch {
            if (bytes != null) {
                val file = coverCache.saveCustomCover(CoverCache.Type.PLAYLIST, playlist.id.toString(), bytes)
                playlistRepository.updatePlaylistArtwork(playlist.id, Uri.fromFile(file).toString())
            } else {
                coverCache.deleteCover(CoverCache.Type.PLAYLIST, playlist.id.toString())
                playlistRepository.updatePlaylistArtwork(playlist.id, null)
            }
        }
    }

    /**
     * Uses the first track's artwork as the playlist cover.
     */
    fun autoArtwork(playlistId: Long) {
        viewModelScope.launch {
            val songs = playlistRepository.observePlaylistSongs(playlistId).first()
            val firstArtworkUri = songs.firstOrNull()?.artworkUri ?: return@launch
            val bytes = loadArtworkBytesUseCase(firstArtworkUri) ?: return@launch
            updateArtwork(playlistId, bytes)
        }
    }

    /**
     * Generates an abstract cover from [config] and applies it to the playlist.
     */
    fun generateArtwork(playlistId: Long, config: GeneratedArtworkConfig) {
        viewModelScope.launch {
            val bytes = generateArtworkUseCase(config)
            updateArtwork(playlistId, bytes)
        }
    }
}
