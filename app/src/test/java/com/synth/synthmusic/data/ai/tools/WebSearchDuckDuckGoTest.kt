package com.synth.synthmusic.data.ai.tools

import junit.framework.Assert.assertEquals
import junit.framework.Assert.assertTrue
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test

/**
 * Unit tests for the keyless DuckDuckGo fallback of [WebSearchTool]:
 * HTML result parsing ([parseDuckDuckGoResults]) and redirect-wrapper
 * resolution ([resolveDuckDuckGoUrl]).
 */
class WebSearchDuckDuckGoTest {

    private fun resultBlock(titleHref: String, title: String, snippet: String): String = """
        <div class="result results_links results_links_deep web-result">
          <h2 class="result__title">
            <a rel="nofollow" class="result__a" href="$titleHref">$title</a>
          </h2>
          <a class="result__snippet" href="$titleHref">$snippet</a>
        </div>
    """.trimIndent()

    private val sampleHtml = resultBlock(
        titleHref = "//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Falpha&amp;rut=abc123",
        title = "Alpha <b>Result</b>",
        snippet = "First &amp; only snippet"
    ) + "\n" + resultBlock(
        titleHref = "https://direct.example.org/beta",
        title = "Direct Link Result",
        snippet = "Second snippet"
    )

    // -----------------------------------------------------------------------
    // parseDuckDuckGoResults
    // -----------------------------------------------------------------------

    @Test
    fun `parseDuckDuckGoResults_titlesSnippetsAndRedirectLinks_arePairedAndResolved`() {
        val results = parseDuckDuckGoResults(sampleHtml)
        assertEquals(2, results.size)
        val first = results[0].jsonObject
        assertEquals("Alpha Result", first["title"]!!.jsonPrimitive.content)
        assertEquals("https://example.com/alpha", first["url"]!!.jsonPrimitive.content)
        assertEquals("First & only snippet", first["snippet"]!!.jsonPrimitive.content)
        val second = results[1].jsonObject
        assertEquals("Direct Link Result", second["title"]!!.jsonPrimitive.content)
        assertEquals("https://direct.example.org/beta", second["url"]!!.jsonPrimitive.content)
        assertEquals("Second snippet", second["snippet"]!!.jsonPrimitive.content)
    }

    @Test
    fun `parseDuckDuckGoResults_adBlockIsDroppedWithItsSnippet`() {
        val adBlock = resultBlock(
            titleHref = "https://duckduckgo.com/y.js?ad_provider=x&ad_domain=ads.example.com",
            title = "Sponsored",
            snippet = "Ad snippet that must not leak"
        )
        val realBlock = resultBlock(
            titleHref = "//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Freal&amp;rut=1",
            title = "Real Result",
            snippet = "Real snippet"
        )
        val results = parseDuckDuckGoResults(adBlock + "\n" + realBlock)
        assertEquals(1, results.size)
        val only = results[0].jsonObject
        assertEquals("Real Result", only["title"]!!.jsonPrimitive.content)
        assertEquals("https://example.com/real", only["url"]!!.jsonPrimitive.content)
        assertEquals("Real snippet", only["snippet"]!!.jsonPrimitive.content)
    }

    @Test
    fun `parseDuckDuckGoResults_moreResultsThanLimit_areTruncatedToFive`() {
        val many = (1..8).joinToString("\n") { i ->
            resultBlock("https://example.com/$i", "Result $i", "Snippet $i")
        }
        val results = parseDuckDuckGoResults(many)
        assertEquals(5, results.size)
        assertEquals("Result 1", results[0].jsonObject["title"]!!.jsonPrimitive.content)
    }

    @Test
    fun `parseDuckDuckGoResults_emptyOrNonResultHtml_yieldsEmptyList`() {
        assertTrue(parseDuckDuckGoResults("").isEmpty())
        assertTrue(parseDuckDuckGoResults("<html><body>No results</body></html>").isEmpty())
    }

    // -----------------------------------------------------------------------
    // resolveDuckDuckGoUrl
    // -----------------------------------------------------------------------

    @Test
    fun `resolveDuckDuckGoUrl_protocolRelativeWrapper_isDecoded`() {
        assertEquals(
            "https://example.com/page?q=test&x=1",
            resolveDuckDuckGoUrl("//duckduckgo.com/l/?uddg=https%3A%2F%2Fexample.com%2Fpage%3Fq%3Dtest%26x%3D1&rut=abc")
        )
    }

    @Test
    fun `resolveDuckDuckGoUrl_relativeWrapper_isDecoded`() {
        assertEquals(
            "https://example.org/",
            resolveDuckDuckGoUrl("/l/?uddg=https%3A%2F%2Fexample.org%2F&rut=1")
        )
    }

    @Test
    fun `resolveDuckDuckGoUrl_directHref_passesThroughUnchanged`() {
        val href = "https://example.com/plain"
        assertEquals(href, resolveDuckDuckGoUrl(href))
    }
}
