package com.synth.synthmusic.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for recently played collections.
 */
@Dao
interface RecentlyPlayedCollectionDao {

    @Insert
    suspend fun insert(item: RecentlyPlayedCollectionEntity)

    @Update
    suspend fun update(item: RecentlyPlayedCollectionEntity)

    @Query("SELECT * FROM recently_played_collections WHERE type = :type AND identifier = :identifier LIMIT 1")
    suspend fun findByTypeAndIdentifier(type: String, identifier: String): RecentlyPlayedCollectionEntity?

    @Query("SELECT * FROM recently_played_collections ORDER BY played_at DESC LIMIT 20")
    fun observeRecent(): Flow<List<RecentlyPlayedCollectionEntity>>

    @Query("DELETE FROM recently_played_collections WHERE id NOT IN (SELECT id FROM recently_played_collections ORDER BY played_at DESC LIMIT 20)")
    suspend fun trimOld()
}
