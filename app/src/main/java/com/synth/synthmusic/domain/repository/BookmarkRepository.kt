package com.synth.synthmusic.domain.repository

import com.synth.synthmusic.domain.model.Bookmark
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for bookmark operations.
 */
interface BookmarkRepository {
    fun observeAllBookmarks(): Flow<List<Bookmark>>
    fun observeBookmarksBySong(songId: String): Flow<List<Bookmark>>
    suspend fun addBookmark(bookmark: Bookmark): Long
    suspend fun updateBookmark(bookmark: Bookmark)
    suspend fun deleteBookmark(bookmarkId: Long)
}
