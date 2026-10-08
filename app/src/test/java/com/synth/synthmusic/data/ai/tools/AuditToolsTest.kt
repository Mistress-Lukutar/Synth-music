package com.synth.synthmusic.data.ai.tools

import com.synth.synthmusic.domain.model.AiCapability
import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.domain.usecase.ai.ToolContext
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jaudiotagger.tag.id3.ID3v23Tag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertNotNull

/**
 * Unit tests for the `audit_tracks` tool and its helpers: [auditIssues],
 * [isMissingMetadata], [tagIssues], [deepFileIssues] error handling and the
 * playlist id validation of [AuditTracksTool].
 */
class AuditToolsTest {

    private fun song(
        title: String = "Song",
        artist: String = "Artist",
        album: String = "Album",
        genre: String = "Rock",
        year: Int = 2018,
        trackNumber: Int = 3,
        lyrics: String? = "la la",
        artworkUri: String? = "content://x",
        durationMs: Long = 200_000L
    ) = Song(
        id = "1",
        title = title,
        artist = artist,
        album = album,
        albumArtist = "",
        durationMs = durationMs,
        trackNumber = trackNumber,
        year = year,
        genre = genre,
        comment = "",
        path = "/nonexistent/song.mp3",
        uri = "content://audio",
        bitrate = 320,
        sampleRate = 44100,
        fileSize = 5_000_000L,
        artworkUri = artworkUri,
        rating = 0f,
        playCount = 0,
        lastPlayed = null,
        dateAdded = 0L,
        dateModified = 0L,
        lyrics = lyrics
    )

    @Test
    fun `complete song has no issues`() {
        assertTrue(auditIssues(song()).isEmpty())
    }

    @Test
    fun `missing fields are reported with stable codes`() {
        val issues = auditIssues(
            song(
                title = "<unknown>",
                artist = "",
                album = "Unknown Album",
                genre = "",
                year = 0,
                trackNumber = 0,
                lyrics = null,
                artworkUri = null,
                durationMs = 0L
            )
        )
        assertEquals(
            listOf(
                "no_title", "no_artist", "no_album", "no_genre", "no_year",
                "no_track_number", "no_lyrics", "no_cover", "no_duration"
            ),
            issues
        )
    }

    @Test
    fun `unknown sentinels are case insensitive`() {
        assertTrue(isMissingMetadata("<UNKNOWN>"))
        assertTrue(isMissingMetadata(" Unknown Artist "))
        assertFalse(isMissingMetadata("Unknown pleasurable things"))
    }

    @Test
    fun `null tag reports every file-verifiable issue`() {
        assertEquals(AuditReasons.FILE_VERIFIABLE.toList(), tagIssues(null))
    }

    @Test
    fun `empty id3 tag reports all file-verifiable issues`() {
        val issues = tagIssues(ID3v23Tag())
        assertEquals(AuditReasons.FILE_VERIFIABLE.toList(), issues)
    }

    @Test
    fun `filled id3 tag only reports missing cover`() {
        val tag = ID3v23Tag()
        tag.setField(org.jaudiotagger.tag.FieldKey.TITLE, "Rush E")
        tag.setField(org.jaudiotagger.tag.FieldKey.ARTIST, "Sheet Music Boss")
        tag.setField(org.jaudiotagger.tag.FieldKey.ALBUM, "Rush E")
        tag.setField(org.jaudiotagger.tag.FieldKey.GENRE, "Meme")
        tag.setField(org.jaudiotagger.tag.FieldKey.YEAR, "2018")
        tag.setField(org.jaudiotagger.tag.FieldKey.TRACK, "1")
        val issues = tagIssues(tag)
        assertEquals(listOf(AuditReasons.NO_COVER), issues)
    }

    @Test
    fun `sentinel tag values count as missing`() {
        val tag = ID3v23Tag()
        tag.setField(org.jaudiotagger.tag.FieldKey.TITLE, "Rush E")
        tag.setField(org.jaudiotagger.tag.FieldKey.ARTIST, "<unknown>")
        val issues = tagIssues(tag)
        assertTrue("no_artist" in issues)
        assertFalse("no_title" in issues)
    }

    @Test
    fun `unreadable file yields unreadable_file`() = runTest {
        val issues = deepFileIssues(song())
        assertEquals(listOf(AuditReasons.UNREADABLE_FILE), issues)
        assertNotNull(issues)
    }

    private fun toolContext() =
        ToolContext(chatId = 1L, grants = setOf(AiCapability.READ_LIBRARY))

    private fun toolArgs(json: String): kotlinx.serialization.json.JsonObject =
        Json.parseToJsonElement(json).jsonObject

    @Test
    fun `audit_tracks unknown playlist id returns self-correcting error`() = runTest {
        val tool = AuditTracksTool(
            allSongs = { emptyList() },
            playlistSongs = { emptyList() },
            allPlaylists = { listOf(Triple(6L, "Archive", 89)) }
        )
        val outcome = tool.execute(
            toolArgs("""{"scope":"playlist","playlistId":99}"""),
            toolContext()
        )
        assertTrue(outcome.isError)
        val parsed = Json.parseToJsonElement(outcome.content).jsonObject
        assertEquals("playlist_not_found", parsed["error"]!!.jsonPrimitive.content)
        assertEquals("Archive", parsed["playlists"]!!.jsonArray.first().jsonObject["name"]!!
            .jsonPrimitive.content)
    }

    @Test
    fun `audit_tracks known playlist id audits its songs`() = runTest {
        val tool = AuditTracksTool(
            allSongs = { emptyList() },
            playlistSongs = { if (it == 6L) listOf(song(lyrics = null)) else emptyList() },
            allPlaylists = { listOf(Triple(6L, "Archive", 89)) }
        )
        val outcome = tool.execute(
            toolArgs("""{"scope":"playlist","playlistId":6}"""),
            toolContext()
        )
        assertFalse(outcome.isError)
        val parsed = Json.parseToJsonElement(outcome.content).jsonObject
        assertEquals("1", parsed["checked"]!!.jsonPrimitive.content)
        assertEquals("1", parsed["problematic"]!!.jsonPrimitive.content)
    }
}
