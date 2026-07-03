package com.synth.synthmusic.ui.albums

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.synth.synthmusic.data.local.cover.CoverCache
import com.synth.synthmusic.data.media.PlaybackRepository
import com.synth.synthmusic.domain.model.Album
import com.synth.synthmusic.domain.model.CollectionType
import com.synth.synthmusic.domain.model.GeneratedArtworkConfig
import com.synth.synthmusic.domain.model.RecentlyPlayedCollection
import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.domain.repository.AlbumRepository
import com.synth.synthmusic.domain.repository.RecentlyPlayedCollectionRepository
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

/**
 * ViewModel for album detail screen.
 */
class AlbumDetailViewModel(
    private val albumTitle: String,
    private val albumArtist: String,
    private val albumRepository: AlbumRepository,
    private val songRepository: SongRepository,
    private val playbackRepository: PlaybackRepository,
    private val recentlyPlayedRepository: RecentlyPlayedCollectionRepository,
    private val coverCache: CoverCache,
    private val generateArtworkUseCase: GenerateArtworkUseCase,
    private val loadArtworkBytesUseCase: LoadArtworkBytesUseCase
) : ViewModel() {

    private val _album = MutableStateFlow<Album?>(null)
    val album: StateFlow<Album?> = _album.asStateFlow()

    val playbackState = playbackRepository.playbackState

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    fun playSongAt(index: Int) {
        val tracks = _songs.value
        if (index in tracks.indices) {
            playbackRepository.playSongs(tracks, index)
            recordAlbumPlayed()
        }
    }

    fun playAll() {
        viewModelScope.launch {
            val tracks = songRepository.observeSongsByAlbum(albumTitle).first()
            if (tracks.isNotEmpty()) {
                playbackRepository.playSongs(tracks, 0)
                recordAlbumPlayed()
            }
        }
    }

    fun shuffleAll() {
        viewModelScope.launch {
            val tracks = songRepository.observeSongsByAlbum(albumTitle).first().shuffled()
            if (tracks.isNotEmpty()) {
                playbackRepository.playSongs(tracks, 0)
                recordAlbumPlayed()
            }
        }
    }

    fun playNext(songId: String) {
        val song = _songs.value.find { it.id == songId } ?: return
        playbackRepository.playNext(song)
    }

    fun addToQueue(songId: String) {
        val song = _songs.value.find { it.id == songId } ?: return
        playbackRepository.addToQueue(song)
    }

    /**
     * Updates the album artwork with the given image bytes, or removes it when null.
     */
    fun updateArtwork(bytes: ByteArray?) {
        viewModelScope.launch {
            val album = _album.value ?: return@launch
            if (bytes != null) {
                val file = coverCache.saveCustomCover(CoverCache.Type.ALBUM, album.id, bytes)
                albumRepository.updateAlbumArtwork(album.id, Uri.fromFile(file).toString())
            } else {
                coverCache.deleteCover(CoverCache.Type.ALBUM, album.id)
                albumRepository.updateAlbumArtwork(album.id, null)
            }
        }
    }

    /**
     * Uses the first track's artwork as the album cover.
     */
    fun autoArtwork() {
        viewModelScope.launch {
            val firstArtworkUri = _songs.value.firstOrNull()?.artworkUri ?: return@launch
            val bytes = loadArtworkBytesUseCase(firstArtworkUri) ?: return@launch
            updateArtwork(bytes)
        }
    }

    /**
     * Generates an abstract cover from [config] and applies it to the album.
     */
    fun generateArtwork(config: GeneratedArtworkConfig) {
        viewModelScope.launch {
            val bytes = generateArtworkUseCase(config)
            updateArtwork(bytes)
        }
    }

    private fun recordAlbumPlayed() {
        val a = _album.value ?: return
        viewModelScope.launch {
            recentlyPlayedRepository.recordPlayed(
                RecentlyPlayedCollection(
                    type = CollectionType.ALBUM,
                    identifier = a.id,
                    name = a.title,
                    extra = a.artist,
                    artworkUri = a.artworkUri,
                    playedAt = System.currentTimeMillis()
                )
            )
        }
    }

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
