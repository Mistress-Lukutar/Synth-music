package com.synth.synthmusic.ui.playlists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synth.synthmusic.data.media.MediaPlaybackManager
import com.synth.synthmusic.domain.model.Playlist
import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.domain.repository.PlaylistRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

/**
 * ViewModel for playlist detail screen.
 */
class PlaylistDetailViewModel(
    private val playlistId: Long,
    private val playlistRepository: PlaylistRepository,
    private val playbackManager: MediaPlaybackManager
) : ViewModel() {

    private val _playlist = MutableStateFlow<Playlist?>(null)
    val playlist: StateFlow<Playlist?> = _playlist.asStateFlow()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    init {
        playlistRepository.observeAllPlaylists()
            .onEach { list ->
                _playlist.value = list.find { it.id == playlistId }
            }
            .launchIn(viewModelScope)

        playlistRepository.observePlaylistSongs(playlistId)
            .onEach { list -> _songs.value = list }
            .launchIn(viewModelScope)
    }

    fun playSongAt(index: Int) {
        val tracks = _songs.value
        if (index in tracks.indices) {
            playbackManager.playSongs(tracks, index)
        }
    }

    fun playAll() {
        val tracks = _songs.value
        if (tracks.isNotEmpty()) {
            playbackManager.playSongs(tracks, 0)
        }
    }

    fun shuffleAll() {
        val tracks = _songs.value.shuffled()
        if (tracks.isNotEmpty()) {
            playbackManager.playSongs(tracks, 0)
        }
    }

    fun removeSong(songId: String) {
        viewModelScope.launch {
            playlistRepository.removeSongFromPlaylist(playlistId, songId)
        }
    }
}
