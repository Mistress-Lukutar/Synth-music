package com.synth.synthmusic.data.ai.tools

import com.synth.synthmusic.data.local.database.CoverBlacklistDao
import com.synth.synthmusic.data.local.database.CoverBlacklistEntity
import com.synth.synthmusic.domain.model.AiCapability
import com.synth.synthmusic.domain.model.Song
import com.synth.synthmusic.domain.usecase.ai.ToolContext
import com.synth.synthmusic.domain.usecase.ai.ToolOutcome
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the cover blacklist tools: [sha256Hex], [isBannerShaped]
 * and the `manage_cover_blacklist` / `purge_covers` logic with in-memory
 * fakes (no Android graphics involved — covers come from fake lambdas).
 */
class CoverToolsTest {

    private class FakeBlacklistDao : CoverBlacklistDao {
        val rows = mutableListOf<CoverBlacklistEntity>()
        override suspend fun getAll(): List<CoverBlacklistEntity> = rows.toList()
        override suspend fun exists(hash: String): Boolean = rows.any { it.hash == hash }
        override suspend fun insert(entry: CoverBlacklistEntity) {
            if (rows.none { it.hash == entry.hash }) rows += entry
        }
        override suspend fun deleteByHashes(hashes: List<String>) {
            rows.removeAll { it.hash in hashes }
        }
    }

    private fun song(id: String, title: String, coverBytes: ByteArray?) =
        Song(
            id = id, title = title, artist = "Artist", album = "Album", albumArtist = "",
            durationMs = 100_000L, trackNumber = 1, year = 2020, genre = "Rock",
            comment = "", path = "/x/$id.mp3", uri = "content://x/$id", bitrate = 320,
            sampleRate = 44100, fileSize = 1_000_000L, artworkUri = null, rating = 0f,
            playCount = 0, lastPlayed = null, dateAdded = 0L, dateModified = 0L, lyrics = null
        )

    @Suppress("TestFunctionName")
    private fun context(vararg grants: AiCapability) =
        ToolContext(chatId = 1L, grants = grants.toSet())

    private fun args(json: String): kotlinx.serialization.json.JsonObject =
        Json.parseToJsonElement(json).jsonObject

    @Test
    fun `sha256 known vectors`() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            sha256Hex("abc".toByteArray())
        )
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sha256Hex(ByteArray(0))
        )
    }

    @Test
    fun `banner shape detection`() {
        assertTrue(isBannerShaped(900, 150))
        assertTrue(isBannerShaped(100, 400))
        assertFalse(isBannerShaped(600, 600))
        assertFalse(isBannerShaped(500, 500))
        assertFalse(isBannerShaped(0, 0))
    }

    @Test
    fun `add by songId hashes embedded cover`() = runTest {
        val dao = FakeBlacklistDao()
        val song = song("s1", "Song One", "banner".toByteArray())
        val tool = ManageCoverBlacklistTool(
            blacklistDao = dao,
            readCover = { EmbeddedCover(it.path.toByteArray(), 900, 150) },
            getSongById = { if (it == "s1") song else null }
        )
        val outcome = tool.execute(
            args("""{"action":"add","songIds":["s1"],"reason":"site banner"}"""),
            context(AiCapability.READ_LIBRARY, AiCapability.WRITE_METADATA)
        )
        assertFalse(outcome.isError)
        assertEquals(1, dao.rows.size)
        assertEquals(900, dao.rows[0].width)
        assertEquals("site banner", dao.rows[0].reason)
        val parsed = Json.parseToJsonElement(outcome.content).jsonObject
        assertEquals(1, parsed["added"]!!.jsonPrimitive.content.toInt())
    }

    @Test
    fun `add is idempotent for the same image`() = runTest {
        val dao = FakeBlacklistDao()
        val song = song("s1", "Song One", "banner".toByteArray())
        val tool = ManageCoverBlacklistTool(
            blacklistDao = dao,
            readCover = { EmbeddedCover(ByteArray(0), 500, 500) },
            getSongById = { song }
        )
        val call = suspend {
            tool.execute(
                args("""{"action":"add","songIds":["s1"]}"""),
                context(AiCapability.READ_LIBRARY, AiCapability.WRITE_METADATA)
            )
        }
        call()
        val second = call()
        val parsed = Json.parseToJsonElement(second.content).jsonObject
        assertEquals(0, parsed["added"]!!.jsonPrimitive.content.toInt())
        assertEquals(1, parsed["duplicates"]!!.jsonPrimitive.content.toInt())
        assertEquals(1, dao.rows.size)
    }

    @Test
    fun `add without write grant is denied`() = runTest {
        val tool = ManageCoverBlacklistTool(
            blacklistDao = FakeBlacklistDao(),
            readCover = { null },
            getSongById = { null }
        )
        val outcome = tool.execute(
            args("""{"action":"add","songIds":["s1"]}"""),
            context(AiCapability.READ_LIBRARY)
        )
        assertTrue(outcome.isError)
        assertTrue("WRITE_METADATA" in outcome.content)
    }

    @Test
    fun `list works with read grant only`() = runTest {
        val dao = FakeBlacklistDao()
        dao.insert(
            CoverBlacklistEntity("ab".repeat(32), 900, 150, 42, null, "site banner", 1L)
        )
        val tool = ManageCoverBlacklistTool(
            blacklistDao = dao, readCover = { null }, getSongById = { null }
        )
        val outcome = tool.execute(args("""{"action":"list"}"""), context(AiCapability.READ_LIBRARY))
        assertFalse(outcome.isError)
        assertEquals(1, Json.parseToJsonElement(outcome.content).jsonObject["count"]!!
            .jsonPrimitive.content.toInt())
    }

    @Test
    fun `purge dry run reports groups without touching files`() = runTest {
        val dao = FakeBlacklistDao()
        val bannerHash = sha256Hex("s1".toByteArray())
        dao.insert(CoverBlacklistEntity(bannerHash, 900, 150, 6, null, "site banner", 1L))
        val removed = mutableListOf<String>()
        val songBad = song("s1", "Bannered", null)
        val songOk = song("s2", "Clean", null)
        val tool = PurgeCoversTool(
            blacklistDao = dao,
            allSongs = { listOf(songBad, songOk) },
            playlistSongs = { emptyList() },
            readCover = { s -> EmbeddedCover(s.id.toByteArray(), 900, 150) },
            removeCover = { s -> removed += s.id; Result.success(Unit) }
        )
        val outcome = tool.execute(
            args("""{"scope":"all","dry_run":true}"""),
            context(AiCapability.WRITE_METADATA)
        )
        assertFalse(outcome.isError)
        assertTrue(removed.isEmpty())
        val parsed = Json.parseToJsonElement(outcome.content).jsonObject
        assertEquals("1", parsed["matched_tracks"]!!.jsonPrimitive.content)
        val group = parsed["groups"]!!.jsonArray.first().jsonObject
        assertEquals(bannerHash, group["hash"]!!.jsonPrimitive.content)
        assertEquals("true", group["banner_shaped"]!!.jsonPrimitive.content)
    }

    @Test
    fun `purge apply removes only blacklisted covers`() = runTest {
        val dao = FakeBlacklistDao()
        dao.insert(
            CoverBlacklistEntity(sha256Hex("s1".toByteArray()), 0, 0, 0, null, "x", 1L)
        )
        val removed = mutableListOf<String>()
        val tool = PurgeCoversTool(
            blacklistDao = dao,
            allSongs = { listOf(song("s1", "Bad", null), song("s2", "Good", null)) },
            playlistSongs = { emptyList() },
            readCover = { s -> EmbeddedCover(s.id.toByteArray(), 500, 500) },
            removeCover = { s -> removed += s.id; Result.success(Unit) }
        )
        val outcome = tool.execute(
            args("""{"scope":"all","dry_run":false}"""),
            context(AiCapability.WRITE_METADATA)
        )
        assertFalse(outcome.isError)
        assertEquals(listOf("s1"), removed)
        val parsed = Json.parseToJsonElement(outcome.content).jsonObject
        assertEquals("1", parsed["updated"]!!.jsonPrimitive.content)
        assertEquals("0", parsed["failed"]!!.jsonPrimitive.content)
    }

    @Test
    fun `purge failure is reported per track`() = runTest {
        val dao = FakeBlacklistDao()
        dao.insert(
            CoverBlacklistEntity(sha256Hex("s1".toByteArray()), 0, 0, 0, null, "x", 1L)
        )
        val tool = PurgeCoversTool(
            blacklistDao = dao,
            allSongs = { listOf(song("s1", "Bad", null)) },
            playlistSongs = { emptyList() },
            readCover = { s -> EmbeddedCover(s.id.toByteArray(), 500, 500) },
            removeCover = { _ -> Result.failure(IllegalStateException("locked")) }
        )
        val outcome = tool.execute(
            args("""{"scope":"all","dry_run":false}"""),
            context(AiCapability.WRITE_METADATA)
        )
        assertFalse(outcome.isError)
        val parsed = Json.parseToJsonElement(outcome.content).jsonObject
        assertEquals("0", parsed["updated"]!!.jsonPrimitive.content)
        assertEquals("1", parsed["failed"]!!.jsonPrimitive.content)
        assertTrue("locked" in parsed["failures"]!!.jsonArray.first().jsonPrimitive.content)
    }

    @Test
    fun `readEmbeddedCover returns null for unreadable file`() = runTest {
        assertNull(readEmbeddedCover(song("s1", "x", null)))
    }
}
