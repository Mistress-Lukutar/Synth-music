package com.synth.synthmusic.data.ai.tools

import com.synth.synthmusic.data.repository.FakeArtistRepository
import com.synth.synthmusic.domain.model.AiCapability
import com.synth.synthmusic.domain.model.Artist
import com.synth.synthmusic.domain.usecase.ai.ConfirmationLevel
import com.synth.synthmusic.domain.usecase.ai.ToolContext
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [GetArtistInfoTool] and [SetArtistBioTool].
 */
class ArtistInfoToolsTest {

    private val fakeRepo = FakeArtistRepository()

    /** Predefined artists used across all tests. */
    private val nightwish = Artist(
        id = "1", name = "Nightwish", songCount = 120, albumCount = 13,
        artworkUri = null, bio = "Finnish symphonic metal band formed in 1996."
    )
    private val nightfall = Artist(
        id = "2", name = "Nightfall", songCount = 15, albumCount = 3,
        artworkUri = null, bio = null
    )
    private val metallica = Artist(
        id = "3", name = "Metallica", songCount = 200, albumCount = 14,
        artworkUri = null, bio = "American heavy metal band from Los Angeles."
    )

    @After
    fun tearDown() {
        fakeRepo.reset()
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private fun context(vararg grants: AiCapability) =
        ToolContext(chatId = 1L, grants = grants.toSet())

    private fun args(json: String): kotlinx.serialization.json.JsonObject =
        Json.parseToJsonElement(json).jsonObject

    /** Build a JSON object with an [artist_name] field. */
    private fun artistArgs(name: String) = buildJsonObject { put("artist_name", name) }

    // =======================================================================
    // GetArtistInfoTool — property tests
    // =======================================================================

    @Test
    fun `GetArtistInfoTool name is get_artist_info`() {
        val tool = GetArtistInfoTool(fakeRepo)
        assertEquals("get_artist_info", tool.name)
    }

    @Test
    fun `GetArtistInfoTool requiredGrants contains READ_LIBRARY`() {
        val tool = GetArtistInfoTool(fakeRepo)
        assertEquals(setOf(AiCapability.READ_LIBRARY), tool.requiredGrants)
    }

    @Test
    fun `GetArtistInfoTool confirmationLevel is NONE`() {
        val tool = GetArtistInfoTool(fakeRepo)
        assertEquals(ConfirmationLevel.NONE, tool.confirmationLevel)
    }

    @Test
    fun `GetArtistInfoTool summarize contains artist name`() {
        val tool = GetArtistInfoTool(fakeRepo)
        val summary = tool.summarize(args("""{"artist_name":"Nightwish"}"""))
        assertTrue(summary.contains("Nightwish"))
    }

    // =======================================================================
    // GetArtistInfoTool — execute tests
    // =======================================================================

    @Test
    fun `GetArtistInfoTool missing artist_name returns error`() = runTest {
        val tool = GetArtistInfoTool(fakeRepo)
        val outcome = tool.execute(buildJsonObject {}, context(AiCapability.READ_LIBRARY))
        assertTrue(outcome.isError)
        assertTrue(outcome.content.contains("artist_name"))
    }

    @Test
    fun `GetArtistInfoTool exact case-insensitive match returns bio and hasInfo true`() = runTest {
        fakeRepo.seedArtists(listOf(nightwish, nightfall, metallica))
        val tool = GetArtistInfoTool(fakeRepo)
        val outcome = tool.execute(
            artistArgs("NIGHTWISH"), context(AiCapability.READ_LIBRARY)
        )
        assertFalse(outcome.isError)

        val payload = Json.parseToJsonElement(outcome.content).jsonObject
        assertEquals("Nightwish", payload["name"]!!.jsonPrimitive.content)
        assertEquals(120, payload["songCount"]!!.jsonPrimitive.intOrNull!!)
        assertEquals(13, payload["albumCount"]!!.jsonPrimitive.intOrNull!!)
        assertTrue(payload["hasInfo"]!!.jsonPrimitive.booleanOrNull!!)
        assertEquals(
            "Finnish symphonic metal band formed in 1996.",
            payload["info"]!!.jsonPrimitive.content
        )
    }

    @Test
    fun `GetArtistInfoTool substring single match resolves correctly`() = runTest {
        fakeRepo.seedArtists(listOf(nightwish, nightfall, metallica))
        val tool = GetArtistInfoTool(fakeRepo)
        val outcome = tool.execute(
            artistArgs("Metallic"), context(AiCapability.READ_LIBRARY)
        )
        assertFalse(outcome.isError)

        val payload = Json.parseToJsonElement(outcome.content).jsonObject
        assertEquals("Metallica", payload["name"]!!.jsonPrimitive.content)
    }

    @Test
    fun `GetArtistInfoTool ambiguous name returns error with candidate list`() = runTest {
        fakeRepo.seedArtists(listOf(nightwish, nightfall, metallica))
        val tool = GetArtistInfoTool(fakeRepo)
        val outcome = tool.execute(
            artistArgs("night"), context(AiCapability.READ_LIBRARY)
        )
        assertTrue(outcome.isError)
        assertTrue(outcome.content.contains("Nightwish"))
        assertTrue(outcome.content.contains("Nightfall"))
    }

    @Test
    fun `GetArtistInfoTool not found returns error`() = runTest {
        fakeRepo.seedArtists(listOf(nightwish, nightfall, metallica))
        val tool = GetArtistInfoTool(fakeRepo)
        val outcome = tool.execute(
            artistArgs("zzzz"), context(AiCapability.READ_LIBRARY)
        )
        assertTrue(outcome.isError)
        assertTrue(outcome.content.contains("zzzz"))
    }

    @Test
    fun `GetArtistInfoTool artist with null bio hasInfo false`() = runTest {
        fakeRepo.seedArtists(listOf(nightfall))
        val tool = GetArtistInfoTool(fakeRepo)
        val outcome = tool.execute(
            artistArgs("Nightfall"), context(AiCapability.READ_LIBRARY)
        )
        assertFalse(outcome.isError)

        val payload = Json.parseToJsonElement(outcome.content).jsonObject
        assertEquals("Nightfall", payload["name"]!!.jsonPrimitive.content)
        assertFalse(payload["hasInfo"]!!.jsonPrimitive.booleanOrNull!!)
        // info should be JsonNull → represented as "null" in the JSON string
        assertTrue(payload.containsKey("info"))
    }

    @Test
    fun `GetArtistInfoTool not found returns name and hasInfo false with null bio`() = runTest {
        fakeRepo.seedArtists(listOf(nightfall))
        val tool = GetArtistInfoTool(fakeRepo)
        // "Nightfall" matches exactly — should return hasInfo=false (bio is null)
        val outcome = tool.execute(
            artistArgs("Nightfall"), context(AiCapability.READ_LIBRARY)
        )
        assertFalse(outcome.isError)
        val payload = Json.parseToJsonElement(outcome.content).jsonObject
        assertEquals(false, payload["hasInfo"]!!.jsonPrimitive.booleanOrNull!!)
    }

    // =======================================================================
    // SetArtistBioTool — property tests
    // =======================================================================

    @Test
    fun `SetArtistBioTool name is set_artist_bio`() {
        val tool = SetArtistBioTool(fakeRepo)
        assertEquals("set_artist_bio", tool.name)
    }

    @Test
    fun `SetArtistBioTool requiredGrants contains WRITE_METADATA`() {
        val tool = SetArtistBioTool(fakeRepo)
        assertEquals(setOf(AiCapability.WRITE_METADATA), tool.requiredGrants)
    }

    @Test
    fun `SetArtistBioTool confirmationLevel is CONFIRM`() {
        val tool = SetArtistBioTool(fakeRepo)
        assertEquals(ConfirmationLevel.CONFIRM, tool.confirmationLevel)
    }

    @Test
    fun `SetArtistBioTool summarize shows (append) when append true`() {
        val tool = SetArtistBioTool(fakeRepo)
        val withAppend = tool.summarize(args("""{"artist_name":"X","bio":"Y","append":true}"""))
        assertTrue(withAppend.contains("(append)"))
        val withoutAppend = tool.summarize(args("""{"artist_name":"X","bio":"Y"}"""))
        assertFalse(withoutAppend.contains("(append)"))
    }

    // =======================================================================
    // SetArtistBioTool — execute tests
    // =======================================================================

    @Test
    fun `SetArtistBioTool missing bio returns error`() = runTest {
        fakeRepo.seedArtists(listOf(nightwish))
        val tool = SetArtistBioTool(fakeRepo)
        val outcome = tool.execute(
            args("""{"artist_name":"Nightwish"}"""), context(AiCapability.WRITE_METADATA)
        )
        assertTrue(outcome.isError)
        assertTrue(outcome.content.contains("bio"))
    }

    @Test
    fun `SetArtistBioTool blank bio returns error`() = runTest {
        fakeRepo.seedArtists(listOf(nightwish))
        val tool = SetArtistBioTool(fakeRepo)
        val outcome = tool.execute(
            args("""{"artist_name":"Nightwish","bio":"   "}"""), context(AiCapability.WRITE_METADATA)
        )
        assertTrue(outcome.isError)
        assertTrue(outcome.content.contains("empty"))
    }

    @Test
    fun `SetArtistBioTool bio exceeding 10000 chars returns error`() = runTest {
        fakeRepo.seedArtists(listOf(nightwish))
        val tool = SetArtistBioTool(fakeRepo)
        val longBio = "x".repeat(10_001)
        val outcome = tool.execute(
            args("""{"artist_name":"Nightwish","bio":"${longBio}"}"""), context(AiCapability.WRITE_METADATA)
        )
        assertTrue(outcome.isError)
        assertTrue(outcome.content.contains("10000"))
    }

    @Test
    fun `SetArtistBioTool bio of exactly 10000 chars succeeds`() = runTest {
        fakeRepo.seedArtists(listOf(nightwish))
        val tool = SetArtistBioTool(fakeRepo)
        val longBio = "x".repeat(10_000)
        val outcome = tool.execute(
            args("""{"artist_name":"Nightwish","bio":"${longBio}"}"""), context(AiCapability.WRITE_METADATA)
        )
        assertFalse(outcome.isError)

        val payload = Json.parseToJsonElement(outcome.content).jsonObject
        assertEquals("Nightwish", payload["name"]!!.jsonPrimitive.content)
        assertEquals(10_000, payload["lengthChars"]!!.jsonPrimitive.longOrNull!!)
        assertEquals("replaced", payload["mode"]!!.jsonPrimitive.content)
    }

    @Test
    fun `SetArtistBioTool replace mode overwrites existing bio`() = runTest {
        fakeRepo.seedArtists(listOf(nightwish))
        val tool = SetArtistBioTool(fakeRepo)
        val outcome = tool.execute(
            args("""{"artist_name":"Nightwish","bio":"New bio text"}"""), context(AiCapability.WRITE_METADATA)
        )
        assertFalse(outcome.isError)

        // Verify the bio was actually saved in the fake repository
        assertEquals("New bio text", fakeRepo.savedBios["1"])

        val payload = Json.parseToJsonElement(outcome.content).jsonObject
        assertEquals("replaced", payload["mode"]!!.jsonPrimitive.content)
    }

    @Test
    fun `SetArtistBioTool append mode joins with separator when existing bio present`() = runTest {
        fakeRepo.seedArtists(listOf(nightwish))
        val tool = SetArtistBioTool(fakeRepo)
        val outcome = tool.execute(
            args("""{"artist_name":"Nightwish","bio":"Additional info","append":true}"""),
            context(AiCapability.WRITE_METADATA)
        )
        assertFalse(outcome.isError)

        val expected = "Finnish symphonic metal band formed in 1996.\n\nAdditional info"
        assertEquals(expected, fakeRepo.savedBios["1"])

        val payload = Json.parseToJsonElement(outcome.content).jsonObject
        assertEquals("appended", payload["mode"]!!.jsonPrimitive.content)
    }

    @Test
    fun `SetArtistBioTool append mode replaces when no existing bio`() = runTest {
        fakeRepo.seedArtists(listOf(nightfall)) // nightfall has null bio
        val tool = SetArtistBioTool(fakeRepo)
        val outcome = tool.execute(
            args("""{"artist_name":"Nightfall","bio":"Brand new bio","append":true}"""),
            context(AiCapability.WRITE_METADATA)
        )
        assertFalse(outcome.isError)

        // With no existing bio, append should just set the value (no separator prefix)
        assertEquals("Brand new bio", fakeRepo.savedBios["2"])

        val payload = Json.parseToJsonElement(outcome.content).jsonObject
        assertEquals("replaced", payload["mode"]!!.jsonPrimitive.content)
    }

    @Test
    fun `SetArtistBioTool not found returns error`() = runTest {
        fakeRepo.seedArtists(listOf(nightwish))
        val tool = SetArtistBioTool(fakeRepo)
        val outcome = tool.execute(
            args("""{"artist_name":"zzzz","bio":"Hello"}"""), context(AiCapability.WRITE_METADATA)
        )
        assertTrue(outcome.isError)
        assertEquals(emptyMap<String, String?>(), fakeRepo.savedBios)
    }

    @Test
    fun `SetArtistBioTool ambiguous returns error and nothing saved`() = runTest {
        fakeRepo.seedArtists(listOf(nightwish, nightfall, metallica))
        val tool = SetArtistBioTool(fakeRepo)
        val outcome = tool.execute(
            args("""{"artist_name":"night","bio":"Hello"}"""), context(AiCapability.WRITE_METADATA)
        )
        assertTrue(outcome.isError)
        // Neither artist's bio should have been saved
        assertEquals(emptyMap<String, String?>(), fakeRepo.savedBios)
    }

    @Test
    fun `SetArtistBioTool payload contains correct lengthChars`() = runTest {
        fakeRepo.seedArtists(listOf(nightwish))
        val tool = SetArtistBioTool(fakeRepo)
        val bio = "Hello world"
        val outcome = tool.execute(
            args("""{"artist_name":"Nightwish","bio":"$bio"}"""), context(AiCapability.WRITE_METADATA)
        )
        assertFalse(outcome.isError)

        val payload = Json.parseToJsonElement(outcome.content).jsonObject
        assertEquals(bio.length.toLong(), payload["lengthChars"]!!.jsonPrimitive.longOrNull!!)
    }

    @Test
    fun `SetArtistBioTool append payload length reflects combined length`() = runTest {
        fakeRepo.seedArtists(listOf(nightwish))
        val tool = SetArtistBioTool(fakeRepo)
        val newLine = "New line"
        val outcome = tool.execute(
            args("""{"artist_name":"Nightwish","bio":"$newLine","append":true}"""),
            context(AiCapability.WRITE_METADATA)
        )
        assertFalse(outcome.isError)

        val payload = Json.parseToJsonElement(outcome.content).jsonObject
        val expectedLength = nightwish.bio!!.length + 2 + newLine.length // "\n\n" = 2 chars
        assertEquals(expectedLength.toLong(), payload["lengthChars"]!!.jsonPrimitive.longOrNull!!)
    }

    @Test
    fun `SetArtistBioTool not found preserves existing bios`() = runTest {
        fakeRepo.seedArtists(listOf(nightwish))
        val tool = SetArtistBioTool(fakeRepo)
        // Seed a prior bio save
        fakeRepo.savedBios["1"] = "prior"

        val outcome = tool.execute(
            args("""{"artist_name":"Unknown","bio":"X"}"""), context(AiCapability.WRITE_METADATA)
        )
        assertTrue(outcome.isError)
        assertEquals("prior", fakeRepo.savedBios["1"])
    }
}
