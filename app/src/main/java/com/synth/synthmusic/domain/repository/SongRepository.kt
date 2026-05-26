package com.synth.synthmusic.domain.repository

import com.synth.synthmusic.domain.model.Song
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for song data operations.
 */
interface SongRepository {
    fun observeAllSongs(): Flow<List<Song>>
    fun observeSongsByAlbum(album: String): Flow<List<Song>>
    fun observeSongsByArtist(artist: String): Flow<List<Song>>
    fun observeSongsByFolder(folderPath: String): Flow<List<Song>>
    fun observeSongById(songId: String): Flow<Song?>
    suspend fun getSongById(songId: String): Song?
    suspend fun saveSongs(songs: List<Song>)
    suspend fun deleteSong(songId: String)
    suspend fun updateSongRating(songId: String, rating: Float)
    suspend fun updateSongFavorite(songId: String, isFavorite: Boolean)
    suspend fun deleteAllSongs()
    fun searchSongs(query: String): Flow<List<Song>>
}
