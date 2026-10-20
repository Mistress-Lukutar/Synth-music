package com.synth.synthmusic.data.ai.tools

import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertNotNull
import junit.framework.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [formatFetchedBody], the internal helper that formats a
 * fetched response body according to its content type.
 */
class FormattedBodyTest {

    // -----------------------------------------------------------------------
    // HTML handling
    // -----------------------------------------------------------------------

    @Test
    fun `formatFetchedBody_htmlWithTitleScriptAndStyle_extractsTitleAndBodyTextWithoutTags`() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head><title>My Article Title</title>
            <script src="app.js">var x = 1;</script>
            <style>.red { color: red; }</style>
            </head>
            <body><h1>Hello World</h1><p>Some &amp; special &lt;chars&gt;</p></body>
            </html>
        """.trimIndent()

        val result = formatFetchedBody(html, "text/html")

        assertNotNull("Result must not be null for text/html", result)
        // Title must appear in the output
        assert(result!!.contains("My Article Title")) {
            "Output should contain the <title> text"
        }
        // Body text must appear
        assert(result.contains("Hello World")) {
            "Output should contain body text"
        }
        assert(result.contains("Some & special <chars>")) {
            "Output should contain decoded entity text from body"
        }
        // Tags / scripts / styles must be removed
        assert(!result.contains("<script")) {
            "Output must not contain <script"
        }
        assert(!result.contains("<style")) {
            "Output must not contain <style"
        }
        // Entity decoding legitimately re-introduces '<' from &lt; — the
        // meaningful checks are that no raw tags/scripts survive (above).
    }

    // -----------------------------------------------------------------------
    // JSON handling — standard application/json with charset parameter
    // -----------------------------------------------------------------------

    @Test
    fun `formatFetchedBody_applicationJsonWithCharset_prettyPrintsAndContainsKeys`() {
        val json = """{"name":"Synth Music","version":1}"""
        val result = formatFetchedBody(json, "application/json; charset=utf-8")

        assertNotNull("Result must not be null for application/json", result)
        // Pretty-printed output contains newlines / indentation
        assert(result!!.contains("\n")) {
            "Pretty-printed JSON must contain newlines"
        }
        // Original key-value must survive
        assert(result.contains("name")) {
            "Output must contain key 'name'"
        }
        assert(result.contains("Synth Music")) {
            "Output must contain value 'Synth Music'"
        }
    }

    // -----------------------------------------------------------------------
    // JSON handling — +json suffix (application/geo+json)
    // -----------------------------------------------------------------------

    @Test
    fun `formatFetchedBody_applicationGeoJsonSuffix_prettyPrintsAndContainsKeys`() {
        val json = """{"type":"Feature","geometry":{"type":"Point","coordinates":[0,0]}}"""
        val result = formatFetchedBody(json, "application/geo+json")

        assertNotNull("Result must not be null for +json suffix", result)
        assert(result!!.contains("\n")) {
            "Pretty-printed JSON must contain newlines"
        }
        assert(result.contains("Feature")) {
            "Output must contain the original JSON value 'Feature'"
        }
    }

    // -----------------------------------------------------------------------
    // JSON handling — invalid JSON with a JSON content type returns raw body
    // -----------------------------------------------------------------------

    @Test
    fun `formatFetchedBody_invalidJsonWithJsonContentType_returnsRawBodyUnchanged`() {
        val malformed = """{not valid json at all!!!}"""
        val result = formatFetchedBody(malformed, "application/json")

        assertEquals(
            "Invalid JSON should be returned verbatim",
            malformed,
            result
        )
    }

    @Test
    fun `formatFetchedBody_textJsonContentType_returnsRawBodyForInvalidJson`() {
        val malformed = """{broken}"""
        val result = formatFetchedBody(malformed, "text/json")

        assertEquals(
            "Invalid JSON with text/json should be returned verbatim",
            malformed,
            result
        )
    }

    // -----------------------------------------------------------------------
    // XML handling — application/rss+xml passes through
    // -----------------------------------------------------------------------

    @Test
    fun `formatFetchedBody_applicationRssXml_passesThroughRawBody`() {
        val xml = """<?xml version="1.0"?><rss version="2.0"><channel><title>Feed</title></channel></rss>"""
        val result = formatFetchedBody(xml, "application/rss+xml")

        assertEquals("XML with +xml suffix must be passed through unchanged", xml, result)
    }

    @Test
    fun `formatFetchedBody_applicationXml_passesThroughRawBody`() {
        val xml = """<?xml version="1.0"?><root><item>1</item></root>"""
        val result = formatFetchedBody(xml, "application/xml")

        assertEquals("application/xml must be passed through unchanged", xml, result)
    }

    @Test
    fun `formatFetchedBody_textXml_passesThroughRawBody`() {
        val xml = """<?xml version="1.0"?><root/>"""
        val result = formatFetchedBody(xml, "text/xml")

        assertEquals("text/xml must be passed through unchanged", xml, result)
    }

    // -----------------------------------------------------------------------
    // Other text/* — passes through raw body
    // -----------------------------------------------------------------------

    @Test
    fun `formatFetchedBody_textPlain_passesThroughRawBody`() {
        val plain = "Hello, this is plain text."
        val result = formatFetchedBody(plain, "text/plain")

        assertEquals("text/plain must be passed through unchanged", plain, result)
    }

    @Test
    fun `formatFetchedBody_textCsv_passesThroughRawBody`() {
        val csv = "name,age\nAlice,30\nBob,25"
        val result = formatFetchedBody(csv, "text/csv")

        assertEquals("text/csv must be passed through unchanged", csv, result)
    }

    // -----------------------------------------------------------------------
    // Unsupported / binary content types — returns null
    // -----------------------------------------------------------------------

    @Test
    fun `formatFetchedBody_imagePng_returnsNull`() {
        val png = "\u0089PNG\r\n\u001a\n" // minimal PNG magic bytes as string
        val result = formatFetchedBody(png, "image/png")

        assertNull("image/png must return null", result)
    }

    @Test
    fun `formatFetchedBody_applicationOctetStream_returnsNull`() {
        val data = "\u0000\u0001\u0002"
        val result = formatFetchedBody(data, "application/octet-stream")

        assertNull("application/octet-stream must return null", result)
    }

    @Test
    fun `formatFetchedBody_emptyContentTypeString_returnsNull`() {
        val body = "some content"
        val result = formatFetchedBody(body, "")

        assertNull("Empty content type string must return null", result)
    }

    // -----------------------------------------------------------------------
    // Additional edge cases
    // -----------------------------------------------------------------------

    @Test
    fun `formatFetchedBody_textJsonValid_prettyPrints`() {
        val json = """{"key":"value"}"""
        val result = formatFetchedBody(json, "text/json")

        assertNotNull("text/json must produce non-null output", result)
        assert(result!!.contains("\n")) {
            "Pretty-printed JSON must contain newlines"
        }
    }

    @Test
    fun `formatFetchedBody_htmlWithNewlinesInScript_stripsEntireScriptBlock`() {
        val html = """
            <html><head><title>Doc</title>
            <script>
            var a = 1;
            var b = 2;
            </script></head>
            <body><p>Body text</p></body></html>
        """.trimIndent()

        val result = formatFetchedBody(html, "text/html")

        assertNotNull(result)
        assert(result!!.contains("Doc")) { "Title must be present" }
        assert(result.contains("Body text")) { "Body text must be present" }
        assert(!result.contains("var a")) { "Script content must be stripped" }
    }

    @Test
    fun `formatFetchedBody_htmlWithHtmlEntities_decodesEntities`() {
        val html = """<html><body>&lt;b&gt;bold&lt;/b&gt; &amp; &quot;quotes&quot;</body></html>"""
        val result = formatFetchedBody(html, "text/html")

        assertNotNull(result)
        assert(result!!.contains("<b>bold</b>")) { "Entity-decoded text must contain <b>" }
        assert(result.contains("&")) { "ampersand must be decoded" }
        assert(result.contains("\"quotes\"")) { "quot entity must be decoded" }
    }

    @Test
    fun `formatFetchedBody_emptyBodyWithHtmlContentType_returnsEmptyOrTitleOnly`() {
        val result = formatFetchedBody("", "text/html")

        // Empty body should still return something (null is acceptable if extractText returns null)
        // but most commonly it will be an empty string or whitespace
        if (result != null) {
            assertEquals("Empty HTML body should yield minimal output", "", result.trim())
        }
    }

    @Test
    fun `formatFetchedBody_jsonWithNestedObjects_prettyPrintsPreservingStructure`() {
        val json = """{"outer":{"inner":"value","list":[1,2]}}"""
        val result = formatFetchedBody(json, "application/json")

        assertNotNull(result)
        assert(result!!.contains("outer")) { "Outer key must be present" }
        assert(result.contains("inner")) { "Nested key must be present" }
        assert(result.contains("value")) { "Nested value must be present" }
        assert(result.contains("\n")) { "Must be pretty-printed" }
    }

    @Test
    fun `formatFetchedBody_contentTypeWithWhitespaceAroundSemicolon_stripsParameter`() {
        val json = """{"a":1}"""
        val result = formatFetchedBody(json, "application/json ; charset=utf-8")

        assertNotNull(result)
        assert(result!!.contains("a")) { "Key must survive whitespace-padded content type" }
    }
}
