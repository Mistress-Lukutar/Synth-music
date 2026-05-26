package com.synth.synthmusic.domain.repository

import com.synth.synthmusic.domain.model.Album
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for album data operations.
 */
interface AlbumRepository {
    fun observeAllAlbums(): Flow<List<Album>>
    suspend fun saveAlbums(albums: List<Album>)
    suspend fun deleteAllAlbums()
}
