package com.synth.synthmusic.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for artists.
 */
@Dao
interface ArtistDao {
    @Query("SELECT * FROM artists ORDER BY song_count DESC, name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<ArtistEntity>>

    @Query("SELECT * FROM artists WHERE id = :artistId")
    suspend fun getById(artistId: String): ArtistEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(artists: List<ArtistEntity>)

    @Query("DELETE FROM artists")
    suspend fun deleteAll()
}
