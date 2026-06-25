package com.synth.synthmusic.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

/**
 * DAO for the active (display) playback queue persisted across process death.
 */
@Dao
interface PlaybackQueueItemDao {

    @Query("SELECT * FROM playback_queue_items ORDER BY order_index ASC")
    suspend fun getAllOrdered(): List<PlaybackQueueItemEntity>

    @Insert
    suspend fun insertAll(items: List<PlaybackQueueItemEntity>)

    @Query("DELETE FROM playback_queue_items")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(items: List<PlaybackQueueItemEntity>) {
        clearAll()
        if (items.isNotEmpty()) {
            insertAll(items)
        }
    }
}
