package com.synth.synthmusic.ui.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synth.synthmusic.domain.model.Album
import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.domain.repository.AlbumRepository
import com.synth.synthmusic.domain.repository.SongRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/**
 * ViewModel for album detail screen.
 */
class AlbumDetailViewModel(
    private val albumTitle: String,
    private val albumArtist: String,
    private val albumRepository: AlbumRepository,
    private val songRepository: SongRepository
) : ViewModel() {

    private val _album = MutableStateFlow<Album?>(null)
    val album: StateFlow<Album?> = _album.asStateFlow()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    init {
        albumRepository.observeAllAlbums()
            .onEach { albums ->
                _album.value = albums.find { it.title == albumTitle && it.artist == albumArtist }
            }
            .launchIn(viewModelScope)

        songRepository.observeSongsByAlbum(albumTitle)
            .onEach { _songs.value = it }
            .launchIn(viewModelScope)
    }
}
