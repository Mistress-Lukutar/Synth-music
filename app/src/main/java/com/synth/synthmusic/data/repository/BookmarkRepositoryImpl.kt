package com.synth.synthmusic.data.repository

import com.synth.synthmusic.data.local.database.BookmarkDao
import com.synth.synthmusic.data.local.database.BookmarkEntity
import com.synth.synthmusic.domain.model.Bookmark
import com.synth.synthmusic.domain.repository.BookmarkRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Implementation of [BookmarkRepository] using Room.
 */
class BookmarkRepositoryImpl(
    private val bookmarkDao: BookmarkDao
) : BookmarkRepository {

    override fun observeAllBookmarks(): Flow<List<Bookmark>> =
        bookmarkDao.observeAll().map { list ->
            list.map { it.toDomain() }
        }

    override fun observeBookmarksBySong(songId: String): Flow<List<Bookmark>> =
        bookmarkDao.observeBySong(songId).map { list ->
            list.map { it.toDomain() }
        }

    override suspend fun addBookmark(bookmark: Bookmark): Long {
        return bookmarkDao.insert(bookmark.toEntity())
    }

    override suspend fun updateBookmark(bookmark: Bookmark) {
        bookmarkDao.update(bookmark.toEntity())
    }

    override suspend fun deleteBookmark(bookmarkId: Long) {
        bookmarkDao.deleteById(bookmarkId)
    }
}

private fun BookmarkEntity.toDomain(): Bookmark = Bookmark(
    id = id,
    songId = songId,
    positionMs = positionMs,
    label = label,
    createdAt = createdAt
)

private fun Bookmark.toEntity(): BookmarkEntity = BookmarkEntity(
    id = id,
    songId = songId,
    positionMs = positionMs,
    label = label,
    createdAt = createdAt
)
