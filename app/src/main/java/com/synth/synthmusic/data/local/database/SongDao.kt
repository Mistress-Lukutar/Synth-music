package com.synth.synthmusic.data.local.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
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

    @Query("SELECT * FROM songs WHERE path LIKE :folderPath || '/%' ORDER BY title COLLATE NOCASE ASC")
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

    @Update
    suspend fun updateAll(songs: List<SongEntity>)

    @Transaction
    suspend fun upsertSongs(songs: List<SongEntity>) {
        val existing = getAll().associateBy { it.id }
        val newSongs = songs.filter { it.id !in existing }
        val updatedSongs = songs.mapNotNull { song ->
            existing[song.id]?.let { mergePreservingUserData(song, it) }
        }
        if (newSongs.isNotEmpty()) insertAll(newSongs)
        if (updatedSongs.isNotEmpty()) updateAll(updatedSongs)
    }

    @Query("DELETE FROM songs WHERE id = :songId")
    suspend fun deleteById(songId: String)

    @Query("UPDATE songs SET rating = :rating WHERE id = :songId")
    suspend fun updateRating(songId: String, rating: Float)

    @Query("UPDATE songs SET is_favorite = :isFavorite WHERE id = :songId")
    suspend fun updateFavorite(songId: String, isFavorite: Boolean)

    @Query("UPDATE songs SET play_count = play_count + 1, last_played = :timestamp WHERE id = :songId")
    suspend fun incrementPlayCount(songId: String, timestamp: Long)

    @Query("UPDATE songs SET lyrics = :lyrics WHERE id = :songId")
    suspend fun updateLyrics(songId: String, lyrics: String?)

    @Query("DELETE FROM songs")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM songs")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM songs WHERE is_favorite = 1 ORDER BY title COLLATE NOCASE ASC")
    fun observeFavorites(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY play_count DESC, title COLLATE NOCASE ASC LIMIT 100")
    fun observeTopSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY play_count DESC, title COLLATE NOCASE ASC LIMIT :limit")
    fun observeTopSongs(limit: Int): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs ORDER BY date_added DESC LIMIT 100")
    fun observeRecentSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE last_played IS NOT NULL ORDER BY last_played DESC LIMIT 100")
    fun observeHistory(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE last_played IS NOT NULL ORDER BY last_played DESC LIMIT :limit")
    fun observeHistory(limit: Int): Flow<List<SongEntity>>

    @Query("SELECT DISTINCT genre FROM songs WHERE genre IS NOT NULL AND genre != '' ORDER BY genre COLLATE NOCASE ASC")
    fun observeGenres(): Flow<List<String>>

    @Query("SELECT * FROM songs WHERE genre = :genre ORDER BY title COLLATE NOCASE ASC")
    fun observeByGenre(genre: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE title LIKE '%' || :query || '%' OR artist LIKE '%' || :query || '%' OR album LIKE '%' || :query || '%'")
    fun search(query: String): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs")
    suspend fun getAll(): List<SongEntity>

    @Query("UPDATE songs SET artwork_uri = :artworkUri WHERE id = :songId")
    suspend fun updateArtworkUri(songId: String, artworkUri: String?)

    @Query("UPDATE songs SET replay_gain_track_db = :trackDb, replay_gain_album_db = :albumDb WHERE id = :songId")
    suspend fun updateReplayGain(songId: String, trackDb: Float?, albumDb: Float?)
}

/**
 * Merges an [incoming] row onto the [stored] row for the same song id,
 * protecting user data from bulk overwrites.
 *
 * Rating, play count, favorite and last-played always keep the stored value:
 * they are only changed through dedicated update methods, and the scan
 * re-extracts them as zeros. Lyrics are kept only when the incoming row does
 * not carry one (the scan never reads lyrics and passes null); an explicit
 * non-null lyrics value — from metadata editing or `set_lyrics` — must win,
 * otherwise the tag is written to the file while the text is silently dropped
 * from the database.
 */
internal fun mergePreservingUserData(incoming: SongEntity, stored: SongEntity): SongEntity =
    incoming.copy(
        rating = stored.rating,
        playCount = stored.playCount,
        lastPlayed = stored.lastPlayed,
        lyrics = incoming.lyrics ?: stored.lyrics,
        isFavorite = stored.isFavorite
    )
