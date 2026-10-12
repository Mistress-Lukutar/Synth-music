package com.synth.synthmusic.data.ai.customtools

import com.synth.synthmusic.domain.usecase.ai.ConfirmationLevel
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomToolManifestTest {

    private val json = Json { ignoreUnknownKeys = true }
    private val builtIns = setOf("download_audio", "search_songs")

    private fun validManifestJson(
        name: String = "my_tool",
        extra: String = ""
    ): String = """
        {
          "formatVersion": 1,
          "name": "$name",
          "description": "Example tool",
          "grants": ["INTERNET"],
          "confirmation": "none",
          "paramsSchema": {"type": "object", "properties": {"q": {"type": "string"}}},
          "http": {
            "url": "https://example.com/api?q={q}",
            "method": "GET"
          }
          $extra
        }
    """.trimIndent()

    private fun parse(text: String): CustomToolManifest =
        parseManifestJson(json, text)

    @Test
    fun `valid manifest passes validation`() {
        val validated = validateManifest(parse(validManifestJson()), builtIns)
        assertEquals("my_tool", validated.manifest.name)
        assertEquals(1, validated.grants.size)
        assertEquals(ConfirmationLevel.NONE, validated.confirmationLevel)
        assertEquals(setOf("q"), validated.referencedParams)
    }

    @Test
    fun `title defaults to name and unknown manifest keys are ignored`() {
        val text = validManifestJson().replaceFirst(
            "{", """{"futureField": 42, "title": "Pretty", """
        )
        val validated = validateManifest(parse(text), builtIns)
        assertEquals("Pretty", validated.manifest.displayName)
    }

    @Test
    fun `bad names are rejected`() {
        for (name in listOf("BadName", "1tool", "x", "has space", "")) {
            val error = runCatching {
                validateManifest(parse(validManifestJson(name = name)), builtIns)
            }.exceptionOrNull()
            assertTrue("name '$name' must be rejected", error is CustomToolValidationException)
        }
    }

    @Test
    fun `builtin name collision is rejected`() {
        val error = runCatching {
            validateManifest(parse(validManifestJson(name = "download_audio")), builtIns)
        }.exceptionOrNull()
        assertTrue(error is CustomToolValidationException)
        assertTrue(error!!.message!!.contains("collides"))
    }

    @Test
    fun `unknown grants are rejected`() {
        val text = validManifestJson().replace("\"INTERNET\"", "\"TELEPORT\"")
        val error = runCatching { validateManifest(parse(text), builtIns) }.exceptionOrNull()
        assertTrue(error is CustomToolValidationException)
        assertTrue(error!!.message!!.contains("unknown grant"))
    }

    @Test
    fun `plain http requires allowHttp`() {
        val text = validManifestJson().replace("https://", "http://")
        val error = runCatching { validateManifest(parse(text), builtIns) }.exceptionOrNull()
        assertTrue(error is CustomToolValidationException)

        val explicit = validManifestJson().replaceFirst("{", """{"allowHttp": true, """)
        validateManifest(parse(explicit), builtIns)
    }

    @Test
    fun `placeholders must reference schema properties`() {
        val text = validManifestJson().replace("{q}", "{missing}")
        val error = runCatching { validateManifest(parse(text), builtIns) }.exceptionOrNull()
        assertTrue(error is CustomToolValidationException)
    }

    @Test
    fun `http and script are mutually exclusive`() {
        val text = validManifestJson(
            extra = """,
          "script": "tool.js"
        """.trimIndent()
        )
        val error = runCatching { validateManifest(parse(text), builtIns) }.exceptionOrNull()
        assertTrue(error is CustomToolValidationException)
        assertTrue(error!!.message!!.contains("mutually exclusive"))
    }

    @Test
    fun `missing execution is rejected`() {
        val text = """
            {
              "formatVersion": 1,
              "name": "my_tool",
              "description": "d",
              "grants": ["INTERNET"],
              "paramsSchema": {"type": "object"}
            }
        """.trimIndent()
        val error = runCatching { validateManifest(parse(text), builtIns) }.exceptionOrNull()
        assertTrue(error is CustomToolValidationException)
    }

    @Test
    fun `secret references are collected from templates`() {
        val text = """
            {
              "formatVersion": 1,
              "name": "my_tool",
              "description": "d",
              "grants": ["INTERNET"],
              "paramsSchema": {"type": "object", "properties": {}},
              "http": {
                "url": "https://example.com/api",
                "headers": {"X-Key": "{{secret:radio_key}}"}
              }
            }
        """.trimIndent()
        val validated = validateManifest(parse(text), builtIns)
        assertEquals(setOf("radio_key"), validated.secretRefs)
    }

    @Test
    fun `params schema properties are read for placeholder checks`() {
        val schema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("q") { put("type", "string") }
            }
        }
        assertEquals(setOf("q"), paramsSchemaProperties(schema))
    }
}
