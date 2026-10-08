package com.synth.synthmusic.data.ai.tools

import com.synth.synthmusic.domain.model.AiCapability
import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.domain.usecase.ai.ToolContext
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the library browse/playlist tools: playlist ids surfaced by
 * `browse_collections` and the self-correcting `playlist_not_found` error of
 * `get_playlist`.
 */
class LibraryToolsTest {

    private fun song(
        id: String = "1",
        album: String = "Album",
        albumArtist: String = "Album Artist"
    ) = Song(
        id = id,
        title = "Song $id",
        artist = "Artist",
        album = album,
        albumArtist = albumArtist,
        durationMs = 200_000L,
        trackNumber = 3,
        year = 2018,
        genre = "Rock",
        comment = "",
        path = "/nonexistent/song.mp3",
        uri = "content://audio",
        bitrate = 320,
        sampleRate = 44100,
        fileSize = 5_000_000L,
        artworkUri = "content://x",
        rating = 0f,
        playCount = 0,
        lastPlayed = null,
        dateAdded = 0L,
        dateModified = 0L,
        lyrics = null
    )

    private fun playlists() = listOf(
        Triple(6L, "Archive", 89),
        Triple(7L, "Saved", 24)
    )

    private fun context() =
        ToolContext(chatId = 1L, grants = setOf(AiCapability.READ_LIBRARY))

    private fun args(json: String): kotlinx.serialization.json.JsonObject =
        Json.parseToJsonElement(json).jsonObject

    @Test
    fun `browse playlists returns ids`() = runTest {
        val tool = BrowseCollectionsTool(
            allSongs = { emptyList() },
            genres = { emptyList() },
            playlists = { playlists() }
        )
        val outcome = tool.execute(args("""{"type":"playlists"}"""), context())
        assertFalse(outcome.isError)
        val items = Json.parseToJsonElement(outcome.content).jsonObject["items"]!!.jsonArray
            .map { it.jsonObject }
        val archive = items.first { it["name"]!!.jsonPrimitive.content == "Archive" }
        assertEquals("6", archive["id"]!!.jsonPrimitive.content)
        assertEquals("89", archive["song_count"]!!.jsonPrimitive.content)
        val saved = items.first { it["name"]!!.jsonPrimitive.content == "Saved" }
        assertEquals("7", saved["id"]!!.jsonPrimitive.content)
    }

    @Test
    fun `filtered browse playlists keeps ids`() = runTest {
        val tool = BrowseCollectionsTool(
            allSongs = { emptyList() },
            genres = { emptyList() },
            playlists = { playlists() }
        )
        val outcome = tool.execute(
            args("""{"type":"playlists","query":"sav"}"""),
            context()
        )
        assertFalse(outcome.isError)
        val parsed = Json.parseToJsonElement(outcome.content).jsonObject
        assertEquals("1", parsed["count"]!!.jsonPrimitive.content)
        val item = parsed["items"]!!.jsonArray.single().jsonObject
        assertEquals("Saved", item["name"]!!.jsonPrimitive.content)
        assertEquals("7", item["id"]!!.jsonPrimitive.content)
    }

    @Test
    fun `browse albums returns album_artist`() = runTest {
        val tool = BrowseCollectionsTool(
            allSongs = { listOf(song(album = "Rush E", albumArtist = "Sheet Music Boss")) },
            genres = { emptyList() },
            playlists = { emptyList() }
        )
        val outcome = tool.execute(args("""{"type":"albums"}"""), context())
        assertFalse(outcome.isError)
        val item = Json.parseToJsonElement(outcome.content).jsonObject["items"]!!
            .jsonArray.single().jsonObject
        assertEquals("Rush E", item["name"]!!.jsonPrimitive.content)
        assertEquals("Sheet Music Boss", item["album_artist"]!!.jsonPrimitive.content)
    }

    @Test
    fun `get_playlist unknown id lists existing playlists`() = runTest {
        val tool = GetPlaylistTool(
            playlistSongs = { emptyList() },
            allPlaylists = { playlists() }
        )
        val outcome = tool.execute(args("""{"playlistId":42}"""), context())
        assertTrue(outcome.isError)
        val parsed = Json.parseToJsonElement(outcome.content).jsonObject
        assertEquals("playlist_not_found", parsed["error"]!!.jsonPrimitive.content)
        assertEquals("42", parsed["playlist_id"]!!.jsonPrimitive.content)
        val items = parsed["playlists"]!!.jsonArray.map { it.jsonObject }
        assertEquals(listOf("6", "7"), items.map { it["id"]!!.jsonPrimitive.content })
        assertEquals("Archive", items[0]["name"]!!.jsonPrimitive.content)
        assertEquals("89", items[0]["song_count"]!!.jsonPrimitive.content)
    }

    @Test
    fun `get_playlist returns songs for a known id`() = runTest {
        val tool = GetPlaylistTool(
            playlistSongs = { if (it == 7L) listOf(song("s1")) else emptyList() },
            allPlaylists = { playlists() }
        )
        val outcome = tool.execute(args("""{"playlistId":7}"""), context())
        assertFalse(outcome.isError)
        val parsed = Json.parseToJsonElement(outcome.content).jsonObject
        assertEquals("7", parsed["playlist_id"]!!.jsonPrimitive.content)
        assertEquals("1", parsed["count"]!!.jsonPrimitive.content)
    }
}
