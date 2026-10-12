package com.synth.synthmusic.data.ai.customtools

import com.synth.synthmusic.domain.model.AiCapability
import com.synth.synthmusic.domain.usecase.ai.ToolContext
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class HttpCustomToolResponseTest {

    @Test
    fun `json path walks objects arrays and indexes`() {
        val body = """{"a":{"list":[{"name":"first"},{"name":"second"}]}}"""
        val root = Json.parseToJsonElement(body)

        assertEquals(JsonPrimitive("second"), navigateJsonPath(root, "$.a.list[1].name"))
        assertEquals(JsonPrimitive("first"), navigateJsonPath(root, "$.a.list[0].name"))
        assertNull(navigateJsonPath(root, "$.a.list[5].name"))
        assertNull(navigateJsonPath(root, "$.a.missing"))
        assertNull(navigateJsonPath(root, "invalid"))
    }

    @Test
    fun `extract returns raw body without a spec`() {
        val outcome = HttpCustomTool.extractResponse("plain", null)
        assertEquals("plain", outcome.content)
    }

    @Test
    fun `json path extraction unwraps primitives`() {
        val outcome = HttpCustomTool.extractResponse("""{"v": 42}""", "$.v")
        assertEquals("42", outcome.content)
    }

    @Test
    fun `regex extraction uses the first capture group`() {
        val outcome = HttpCustomTool.extractResponse("url=/get/music/123", "regex:/get/music/(\\d+)")
        assertEquals("123", outcome.content)
    }

    @Test
    fun `invalid regex reports an error`() {
        val outcome = HttpCustomTool.extractResponse("x", "regex:[unclosed")
        assertTrue(outcome.isError)
    }
}

class CustomToolPackageReaderTest {

    private val reader = CustomToolPackageReader(Json { ignoreUnknownKeys = true })

    private val manifestJson = """
        {
          "formatVersion": 1,
          "name": "my_tool",
          "description": "d",
          "grants": ["INTERNET"],
          "paramsSchema": {"type": "object", "properties": {}},
          "http": {"url": "https://example.com/"}
        }
    """.trimIndent()

    @Test
    fun `plain json import works`() {
        val pkg = reader.read(manifestJson.toByteArray(), "my_tool.json")
        assertEquals("my_tool", pkg.manifest.name)
        assertNull(pkg.scriptSource)
    }

    @Test
    fun `script declared without a zip is rejected`() {
        val text = manifestJson.replaceFirst(
            "{", """{"script": "tool.js", """
        )
        val error = runCatching { reader.read(text.toByteArray(), "t.json") }.exceptionOrNull()
        assertTrue(error!!.message!!.contains("package the tool as a zip"))
    }

    @Test
    fun `zip import resolves the script`() {
        val zip = zipOf(
            "manifest.json" to manifestJson,
            "tool.js" to "function execute() {}"
        )
        val pkg = reader.read(zip, "tool.zip")
        assertEquals("tool.js", pkg.manifest.script)
        assertEquals("function execute() {}", pkg.scriptSource)
    }

    @Test
    fun `zip without manifest is rejected`() {
        val zip = zipOf("tool.js" to "x")
        val error = runCatching { reader.read(zip, "t.zip") }.exceptionOrNull()
        assertTrue(error!!.message!!.contains("manifest.json"))
    }

    @Test
    fun `zip with a missing declared script is rejected`() {
        val text = manifestJson.replaceFirst("{", """{"script": "run.js", """)
        val zip = zipOf("manifest.json" to text, "tool.js" to "x")
        val error = runCatching { reader.read(zip, "t.zip") }.exceptionOrNull()
        assertTrue(error!!.message!!.contains("run.js"))
    }

    @Test
    fun `path traversal entries are rejected`() {
        val zip = zipOf("../evil.js" to "x", "manifest.json" to manifestJson)
        val error = runCatching { reader.read(zip, "t.zip") }.exceptionOrNull()
        assertTrue(error!!.message!!.contains("Unsafe archive entry"))
    }

    private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray())
                zip.closeEntry()
            }
        }
        return output.toByteArray()
    }
}

class HttpCustomToolEndToEndTest {

    private object NoSecrets : CustomSecrets {
        override suspend fun get(toolName: String, secretName: String): String? = null
    }

    @Test
    fun `declarative tool substitutes placeholders and extracts the response`() = runBlocking {
        val server = MockWebServer()
        server.enqueue(MockResponse.Builder().body("""{"title": "Nightcall"}""").build())
        server.start()
        try {
            val manifestText = """
                {"formatVersion": 1, "name": "e2e", "description": "d", "allowHttp": true,
                 "grants": ["INTERNET"],
                 "paramsSchema": {"type": "object", "properties": {"q": {"type": "string"}}},
                 "http": {"url": "${server.url("/api")}/{q}", "method": "GET",
                          "headers": {"X-Trace": "t-{q}"},
                          "responseExtract": "$.title"}}
                """.trimIndent()
            val tool = HttpCustomTool(
                validateManifest(parseManifestJson(Json { ignoreUnknownKeys = true }, manifestText), emptySet()),
                OkHttpClient(),
                NoSecrets
            )
            val outcome = kotlinx.coroutines.runBlocking {
                tool.execute(
                    buildJsonObject { put("q", "kavinsky") },
                    ToolContext(0L, setOf(AiCapability.INTERNET))
                )
            }
            assertEquals("Nightcall", outcome.content)

            val request = server.takeRequest()
            assertEquals("/api/kavinsky", request.url.encodedPath)
            assertEquals("t-kavinsky", request.headers["X-Trace"])
        } finally {
            server.close()
        }
    }
}
