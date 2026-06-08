package com.synth.synthmusic.data.repository

import com.synth.synthmusic.domain.model.Artist
import com.synth.synthmusic.domain.repository.ArtistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

class FakeArtistRepository : ArtistRepository {
    override fun observeAllArtists(): Flow<List<Artist>> = flowOf(emptyList())
    override suspend fun saveArtists(artists: List<Artist>) {}
    override suspend fun deleteAllArtists() {}
    override suspend fun updateArtistArtwork(artistId: String, artworkUri: String?) {}
    override suspend fun replaceAllArtists(artists: List<Artist>) {}
}
