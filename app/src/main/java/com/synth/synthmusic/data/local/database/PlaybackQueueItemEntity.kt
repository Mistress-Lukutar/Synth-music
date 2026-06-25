package com.synth.synthmusic.data.local.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a single item in the active (display) playback queue.
 *
 * The active queue reflects the order currently handed to ExoPlayer, which may
 * be shuffled. It is stored without a stable position id because process-death
 * recovery only needs the playback order, not runtime identity.
 */
@Entity(tableName = "playback_queue_items")
data class PlaybackQueueItemEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Int = 0,

    @ColumnInfo(name = "song_id")
    val songId: String,

    @ColumnInfo(name = "order_index")
    val orderIndex: Int
)
