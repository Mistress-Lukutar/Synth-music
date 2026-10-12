package com.synth.synthmusic.data.ai.customtools

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure-JVM fake of the script host surface. */
private class FakeHost(
    private val responseText: String = """{"hello": "world"}""",
    private val secretValue: String? = null
) : ScriptHostContext {

    val logs = mutableListOf<String>()
    var lastHeaders: Map<String, String> = emptyMap()
    var lastUrl: String? = null

    override fun httpGetText(url: String, headers: Map<String, String>): String {
        lastUrl = url
        lastHeaders = headers
        return responseText
    }

    override fun httpGet(
        url: String,
        headers: Map<String, String>
    ): ScriptHostContext.HttpTextResponse {
        httpGetText(url, headers)
        return ScriptHostContext.HttpTextResponse(200, "application/json", responseText)
    }

    override fun httpPost(
        url: String,
        body: String,
        contentType: String?,
        headers: Map<String, String>
    ): ScriptHostContext.HttpTextResponse =
        ScriptHostContext.HttpTextResponse(200, "application/json", responseText)

    override fun downloadAudio(
        url: String,
        folder: String?,
        fileName: String?,
        headers: Map<String, String>
    ): Map<String, Any?> = mapOf("message" to "saved", "savedAs" to fileName)

    override fun librarySearchSongs(query: String, limit: Int): List<Map<String, Any?>> = listOf(
        mapOf("id" to "42", "title" to query, "artist" to "Artist", "album" to "Album", "durationMs" to 1234L)
    )

    override fun secret(name: String): String? = if (name == "key") secretValue else null

    override fun log(message: String) {
        logs.add(message)
    }
}

class ToolScriptRunnerTest {

    private fun args(vararg pairs: Pair<String, Any>) = buildJsonObject {
        pairs.forEach { (key, value) ->
            put(key, when (value) {
                is Number -> JsonPrimitive(value)
                is Boolean -> JsonPrimitive(value)
                else -> JsonPrimitive(value.toString())
            })
        }
    }

    @Test
    fun `string result becomes content`() {
        val outcome = ToolScriptRunner.run("function execute(args, ctx) { return 'hello ' + args.name; }", args("name" to "world"), FakeHost())
        assertEquals("hello world", outcome.content)
    }

    @Test
    fun `object result carries content and isError`() {
        val script = "function execute(args, ctx) { return {content: 'nope', isError: true}; }"
        val outcome = ToolScriptRunner.run(script, args(), FakeHost())
        assertEquals("nope", outcome.content)
        assertTrue(outcome.isError)
    }

    @Test
    fun `ctx http getText reaches the host and headers flow through`() {
        val host = FakeHost(responseText = "body-text")
        val script = """
            function execute(args, ctx) {
                var text = ctx.http.getText('https://example.com/x', {'X-Key': 'secret'});
                return text + ':' + args.suffix;
            }
        """.trimIndent()
        val outcome = ToolScriptRunner.run(script, args("suffix" to "!"), host)
        assertEquals("body-text:!", outcome.content)
        assertEquals("https://example.com/x", host.lastUrl)
        assertEquals("secret", host.lastHeaders["X-Key"])
    }

    @Test
    fun `ctx http getJson parses the response into a JS object`() {
        val host = FakeHost(responseText = """{"items": [{"title": "Song"}]}""")
        val script = """
            function execute(args, ctx) {
                var json = ctx.http.getJson('https://example.com/api');
                return json.items[0].title;
            }
        """.trimIndent()
        val outcome = ToolScriptRunner.run(script, args(), host)
        assertEquals("Song", outcome.content)
    }

    @Test
    fun `ctx library search returns song objects`() {
        val script = """
            function execute(args, ctx) {
                var songs = ctx.library.searchSongs('rush', 5);
                return songs[0].id + ':' + songs[0].title;
            }
        """.trimIndent()
        val outcome = ToolScriptRunner.run(script, args(), FakeHost())
        assertEquals("42:rush", outcome.content)
    }

    @Test
    fun `ctx secret returns configured values and null otherwise`() {
        val script = """
            function execute(args, ctx) {
                return String(ctx.secret('key')) + '|' + ctx.secret('missing');
            }
        """.trimIndent()
        val outcome = ToolScriptRunner.run(script, args(), FakeHost(secretValue = "s3cret"))
        assertEquals("s3cret|null", outcome.content)
    }

    @Test
    fun `ctx log lines are appended to error outcomes`() {
        val script = """
            function execute(args, ctx) {
                ctx.log('step one');
                return {content: 'failed', isError: true};
            }
        """.trimIndent()
        val outcome = ToolScriptRunner.run(script, args(), FakeHost())
        assertTrue(outcome.content.contains("[log] step one"))
    }

    @Test
    fun `missing execute function is reported`() {
        val outcome = ToolScriptRunner.run("var x = 1;", args(), FakeHost())
        assertTrue(outcome.isError)
        assertTrue(outcome.content.contains("execute"))
    }

    @Test
    fun `syntax errors become error outcomes`() {
        val outcome = ToolScriptRunner.run("function execute( {", args(), FakeHost())
        assertTrue(outcome.isError)
    }

    @Test
    fun `infinite loops hit the timeout`() {
        val script = "function execute(args, ctx) { while (true) { } return 'never'; }"
        val start = System.currentTimeMillis()
        val outcome = ToolScriptRunner.run(script, args(), FakeHost(), timeoutMs = 500)
        assertTrue(outcome.isError)
        assertTrue(outcome.content.contains("limit"))
        assertTrue(System.currentTimeMillis() - start < 10_000)
    }

    @Test
    fun `java access is denied by the class shutter`() {
        val script = "function execute(args, ctx) { return String(java.lang.Runtime.getRuntime()); }"
        val outcome = ToolScriptRunner.run(script, args(), FakeHost())
        assertTrue(outcome.isError)
    }

    @Test
    fun `host exceptions become readable error outcomes`() {
        val script = "function execute(args, ctx) { return ctx.http.getText('https://example.com/'); }"
        val failing = object : ScriptHostContext by FakeHost() {
            override fun httpGetText(url: String, headers: Map<String, String>): String {
                throw ToolHostException("Denied: no internet grant")
            }
        }
        val outcome = ToolScriptRunner.run(script, args(), failing)
        assertTrue(outcome.isError)
        assertTrue(outcome.content.contains("internet grant"))
    }

    private object NoSecrets : CustomSecrets {
        override suspend fun get(toolName: String, secretName: String): String? = null
    }

    @Test
    fun `summarize is optional and returns a string`() {
        val script = """
            function execute(args, ctx) { return ''; }
            function summarize(args) { return 'Do: ' + args.q; }
        """.trimIndent()
        assertEquals("Do: test", ToolScriptRunner.summarize(script, args("q" to "test")))
    }

    @Test
    fun `json arguments are converted to native JS values`() {
        val script = """
            function execute(args, ctx) {
                return typeof args.count + ':' + args.count + ':' + args.flag + ':' + args.tags[1] + ':' + args.inner.name;
            }
        """.trimIndent()
        val complex = buildJsonObject {
            put("count", 3)
            put("flag", true)
            put("tags", kotlinx.serialization.json.JsonArray(listOf(JsonPrimitive("a"), JsonPrimitive("b"))))
            put("inner", buildJsonObject { put("name", "deep") })
        }
        val outcome = ToolScriptRunner.run(script, complex, FakeHost())
        assertEquals("number:3:true:b:deep", outcome.content)
    }
}
