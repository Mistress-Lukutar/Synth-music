package com.synth.synthmusic.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for playback state.
 */
@Dao
interface PlaybackStateDao {
    @Query("SELECT * FROM playback_state WHERE id = 1")
    fun observe(): Flow<PlaybackStateEntity?>

    @Query("SELECT * FROM playback_state WHERE id = 1")
    suspend fun get(): PlaybackStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(state: PlaybackStateEntity)

    @Query("DELETE FROM playback_state")
    suspend fun deleteAll()
}
