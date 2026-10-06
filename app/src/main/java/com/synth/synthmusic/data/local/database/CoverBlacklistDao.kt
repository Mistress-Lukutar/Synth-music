package com.synth.synthmusic.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Data access object for the cover blacklist.
 */
@Dao
interface CoverBlacklistDao {

    @Query("SELECT * FROM cover_blacklist ORDER BY blacklisted_at DESC")
    suspend fun getAll(): List<CoverBlacklistEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM cover_blacklist WHERE hash = :hash)")
    suspend fun exists(hash: String): Boolean

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: CoverBlacklistEntity)

    @Query("DELETE FROM cover_blacklist WHERE hash IN (:hashes)")
    suspend fun deleteByHashes(hashes: List<String>)
}
