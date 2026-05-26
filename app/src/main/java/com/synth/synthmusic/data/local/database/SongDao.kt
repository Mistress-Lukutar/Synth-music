package com.synth.synthmusic.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Data access object for songs.
 */
@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE album = :album ORDER BY track_number ASC")
    fun observeByAlbum(album: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE artist = :artist ORDER BY title COLLATE NOCASE ASC")
    fun observeByArtist(artist: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE path LIKE :folderPath || '%' ORDER BY title COLLATE NOCASE ASC")
    fun observeByFolder(folderPath: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :songId")
    suspend fun getById(songId: String): SongEntity?

    @Query("SELECT * FROM songs WHERE id IN (:songIds)")
    suspend fun getByIds(songIds: List<String>): List<SongEntity>

    @Query("SELECT * FROM songs WHERE id = :songId")
    fun observeById(songId: String): Flow<SongEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(songs: List<SongEntity>)

    @Update
    suspend fun update(song: SongEntity)

    @Query("DELETE FROM songs WHERE id = :songId")
    suspend fun deleteById(songId: String)

    @Query("UPDATE songs SET rating = :rating WHERE id = :songId")
    suspend fun updateRating(songId: String, rating: Float)

    @Query("DELETE FROM songs")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM songs")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%'")
    fun search(query: String): Flow<List<SongEntity>>
}
