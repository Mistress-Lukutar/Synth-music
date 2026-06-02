package com.synth.synthmusic.domain.repository

import com.synth.synthmusic.domain.model.Album
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for album data operations.
 */
interface AlbumRepository {
    fun observeAllAlbums(): Flow<List<Album>>
    fun observeAlbumsByArtist(artist: String): Flow<List<Album>>
    suspend fun saveAlbums(albums: List<Album>)
    suspend fun updateAlbumArtwork(albumId: String, artworkUri: String?)
    suspend fun deleteAllAlbums()
    suspend fun replaceAllAlbums(albums: List<Album>)
}
