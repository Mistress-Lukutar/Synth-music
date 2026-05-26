package com.synth.synthmusic.domain.repository

import com.synth.synthmusic.domain.model.Artist
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for artist data operations.
 */
interface ArtistRepository {
    fun observeAllArtists(): Flow<List<Artist>>
    suspend fun saveArtists(artists: List<Artist>)
    suspend fun deleteAllArtists()
}
