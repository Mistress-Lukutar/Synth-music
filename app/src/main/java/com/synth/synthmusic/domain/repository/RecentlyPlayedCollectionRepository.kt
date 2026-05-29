package com.synth.synthmusic.domain.repository

import com.synth.synthmusic.domain.model.RecentlyPlayedCollection
import kotlinx.coroutines.flow.Flow

/**
 * Repository for tracking and retrieving recently played collections.
 */
interface RecentlyPlayedCollectionRepository {

    /**
     * Record a collection as recently played.
     */
    suspend fun recordPlayed(collection: RecentlyPlayedCollection)

    /**
     * Observe the most recently played collections (up to 20).
     */
    fun observeRecent(): Flow<List<RecentlyPlayedCollection>>
}
