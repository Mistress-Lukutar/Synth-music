package com.synth.synthmusic.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction

/**
 * DAO for the original (unshuffled) playback queue persisted across process death.
 */
@Dao
interface PlaybackOriginalQueueItemDao {

    @Query("SELECT * FROM playback_original_queue_items ORDER BY order_index ASC")
    suspend fun getAllOrdered(): List<PlaybackOriginalQueueItemEntity>

    @Insert
    suspend fun insertAll(items: List<PlaybackOriginalQueueItemEntity>)

    @Query("DELETE FROM playback_original_queue_items")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAll(items: List<PlaybackOriginalQueueItemEntity>) {
        clearAll()
        if (items.isNotEmpty()) {
            insertAll(items)
        }
    }
}
