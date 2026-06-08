package com.synth.synthmusic.data.repository

import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.domain.repository.SongRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

/**
 * In-memory fake implementation of [SongRepository] for unit testing.
 */
class FakeSongRepository : SongRepository {

    private val songs = MutableStateFlow<List<Song>>(emptyList())

    fun setSongs(value: List<Song>) {
        songs.value = value
    }

    override fun observeAllSongs(): Flow<List<Song>> = songs

    override fun observeSongsByAlbum(album: String): Flow<List<Song>> = flowOf(emptyList())

    override fun observeSongsByArtist(artist: String): Flow<List<Song>> = flowOf(emptyList())

    override fun observeSongsByFolder(folderPath: String): Flow<List<Song>> = flowOf(emptyList())

    override fun observeSongById(songId: String): Flow<Song?> = flowOf(null)

    override suspend fun getSongById(songId: String): Song? = songs.value.find { it.id == songId }

    override suspend fun getSongsByIds(songIds: List<String>): List<Song> =
        songs.value.filter { it.id in songIds }

    override suspend fun getAllSongs(): List<Song> = songs.value

    override suspend fun saveSongs(value: List<Song>) {
        songs.value = value
    }

    override suspend fun upsertSongs(songs: List<Song>) {
        this.songs.value = this.songs.value.filter { existing ->
            songs.none { it.id == existing.id }
        } + songs
    }

    override suspend fun deleteSong(songId: String) {}

    override suspend fun updateSongRating(songId: String, rating: Float) {}

    override suspend fun updateSongFavorite(songId: String, isFavorite: Boolean) {}

    override suspend fun incrementPlayCount(songId: String) {}

    override suspend fun updateSongLyrics(songId: String, lyrics: String?) {}

    override suspend fun updateSongArtwork(songId: String, artworkUri: String?) {}

    override suspend fun deleteAllSongs() {}

    override fun observeFavoriteSongs(): Flow<List<Song>> = flowOf(emptyList())

    override fun observeTopSongs(): Flow<List<Song>> = flowOf(emptyList())

    override fun observeRecentSongs(): Flow<List<Song>> = flowOf(emptyList())

    override fun observeHistory(): Flow<List<Song>> = flowOf(emptyList())

    override fun observeFolders(): Flow<List<String>> = flowOf(emptyList())

    override fun observeGenres(): Flow<List<String>> = flowOf(emptyList())

    override fun observeSongsByGenre(genre: String): Flow<List<Song>> = flowOf(emptyList())

    override fun searchSongs(query: String): Flow<List<Song>> =
        flowOf(songs.value.filter { it.title.contains(query, ignoreCase = true) })
}
