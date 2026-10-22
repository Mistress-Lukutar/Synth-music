package com.synth.synthmusic.data.repository

import com.synth.synthmusic.domain.model.Artist
import com.synth.synthmusic.domain.repository.ArtistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Fake [ArtistRepository] that keeps an in-memory list of artists and records
 * bio updates so tests can assert saved values.
 */
class FakeArtistRepository : ArtistRepository {

    private val _artists = MutableStateFlow<List<Artist>>(emptyList())
    override fun observeAllArtists(): Flow<List<Artist>> = _artists.asStateFlow()

    /** Mutable copy for seeding / mutating artists from tests. */
    private val _artistList = mutableListOf<Artist>()

    /** Map tracking every bio value set via [updateArtistBio] keyed by artist id. */
    val savedBios = mutableMapOf<String, String?>()

    override suspend fun saveArtists(artists: List<Artist>) {
        _artistList.clear()
        _artistList.addAll(artists)
        _artists.value = _artistList.toList()
    }

    override suspend fun deleteAllArtists() {
        _artistList.clear()
        _artists.value = emptyList()
    }

    override suspend fun updateArtistArtwork(artistId: String, artworkUri: String?) {
        // no-op in the fake
    }

    override suspend fun updateArtistBio(artistId: String, bio: String?) {
        savedBios[artistId] = bio
    }

    override suspend fun replaceAllArtists(artists: List<Artist>) {
        _artistList.clear()
        _artistList.addAll(artists)
        _artists.value = _artistList.toList()
    }

    /** Seed the fake with a list of artists. */
    fun seedArtists(artists: List<Artist>) {
        _artistList.clear()
        _artistList.addAll(artists)
        _artists.value = _artistList.toList()
    }

    /** Clear all saved bios and artist state. */
    fun reset() {
        _artistList.clear()
        savedBios.clear()
        _artists.value = emptyList()
    }
}
