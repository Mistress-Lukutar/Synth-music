package com.synth.synthmusic.data.local.database

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the user-data merge used by [SongDao.upsertSongs]: lyrics
 * from an explicit write must reach the database (set_lyrics / metadata
 * editing), while scan rows — which never carry lyrics — must not erase a
 * stored text.
 */
class SongDaoMergeTest {

    private fun entity(
        lyrics: String?,
        rating: Float = 4f,
        isFavorite: Boolean = true
    ) = SongEntity(
        id = "1",
        title = "Living in a Fantasy",
        artist = "BWO",
        album = "Prototype",
        albumArtist = "BWO",
        durationMs = 192_078L,
        trackNumber = 12,
        year = 2005,
        genre = "Pop",
        comment = "",
        path = "/storage/emulated/0/Music/Archive/BWO - Living in a Fantasy.mp3",
        uri = "content://media/external/audio/media/1",
        bitrate = 320,
        sampleRate = 44100,
        fileSize = 6_331_454L,
        artworkUri = null,
        rating = rating,
        playCount = 7,
        lastPlayed = 1_000L,
        dateAdded = 2_000L,
        dateModified = 3_000L,
        lyrics = lyrics,
        isFavorite = isFavorite
    )

    @Test
    fun `explicit lyrics write wins over stored null`() {
        val merged = mergePreservingUserData(
            incoming = entity(lyrics = "Look outside the fantasy…"),
            stored = entity(lyrics = null)
        )
        assertEquals("Look outside the fantasy…", merged.lyrics)
    }

    @Test
    fun `explicit lyrics write replaces stored text`() {
        val merged = mergePreservingUserData(
            incoming = entity(lyrics = "Updated lyrics"),
            stored = entity(lyrics = "Old lyrics")
        )
        assertEquals("Updated lyrics", merged.lyrics)
    }

    @Test
    fun `scan row without lyrics keeps stored text`() {
        val merged = mergePreservingUserData(
            incoming = entity(lyrics = null),
            stored = entity(lyrics = "Look outside the fantasy…")
        )
        assertEquals("Look outside the fantasy…", merged.lyrics)
    }

    @Test
    fun `user data always keeps stored values`() {
        val merged = mergePreservingUserData(
            incoming = entity(lyrics = "text", rating = 0f, isFavorite = false),
            stored = entity(lyrics = null, rating = 4f, isFavorite = true)
        )
        assertEquals(4f, merged.rating)
        assertEquals(true, merged.isFavorite)
        assertEquals(7, merged.playCount)
        assertEquals(1_000L, merged.lastPlayed)
    }
}
