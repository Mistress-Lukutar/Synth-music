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
    suspend fun getSongsByIds(songIds: List<String>): List<Song>
    suspend fun getAllSongs(): List<Song>
    suspend fun saveSongs(songs: List<Song>)
    suspend fun deleteSong(songId: String)
    suspend fun updateSongRating(songId: String, rating: Float)
    suspend fun updateSongFavorite(songId: String, isFavorite: Boolean)
    suspend fun incrementPlayCount(songId: String)
    suspend fun updateSongLyrics(songId: String, lyrics: String?)
    suspend fun deleteAllSongs()
    fun observeFavoriteSongs(): Flow<List<Song>>
    fun observeTopSongs(): Flow<List<Song>>
    fun observeRecentSongs(): Flow<List<Song>>
    fun observeHistory(): Flow<List<Song>>
    fun observeFolders(): Flow<List<String>>
    fun searchSongs(query: String): Flow<List<Song>>
}
