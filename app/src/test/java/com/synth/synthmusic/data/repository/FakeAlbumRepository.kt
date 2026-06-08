package com.synth.synthmusic.data.repository

import com.synth.synthmusic.domain.model.Album
import com.synth.synthmusic.domain.repository.AlbumRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeAlbumRepository : AlbumRepository {
    override fun observeAllAlbums(): Flow<List<Album>> = flowOf(emptyList())
    override fun observeAlbumsByArtist(artist: String): Flow<List<Album>> = flowOf(emptyList())
    override suspend fun saveAlbums(albums: List<Album>) {}
    override suspend fun updateAlbumArtwork(albumId: String, artworkUri: String?) {}
    override suspend fun deleteAllAlbums() {}
    override suspend fun replaceAllAlbums(albums: List<Album>) {}
}
