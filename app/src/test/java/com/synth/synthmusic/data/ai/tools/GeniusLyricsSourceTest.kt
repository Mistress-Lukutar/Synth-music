package com.synth.synthmusic.data.ai.tools

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the Genius source of `get_lyrics_online`: page extraction,
 * HTML entity decoding and hit selection.
 */
class GeniusLyricsSourceTest {

    @Test
    fun `extracts lyrics, drops header subtree and decodes entities`() {
        val page = """
            <html><body>
            <div data-lyrics-container="true" class="Lyrics__Container">
              <div data-exclude-from-selection="true" class="LyricsHeader__Container">
                <div class="Dropdown"><a href="https://genius.com/x">Русский (Russian)</a></div>
                <h2 class="LyricsHeader__Title">Ugly Drunken Woman Lyrics</h2>
              </div>[Verse 1]<br/>One Friday night I was strolling out<br/>&#x27;Cause a lady came<br/><br/>[Chorus]<br/>&#x27;Cause this ugly drunken woman
            </div>
            <div class="RightSidebar"><div>You might also like</div></div>
            <div data-lyrics-container="true" class="Lyrics__Container"></div>
            <div data-lyrics-container="true" class="Lyrics__Container">[Outro]<br/>La, la-la, la-la<br/></div>
            </body></html>
        """.trimIndent()
        val expected = "[Verse 1]\nOne Friday night I was strolling out\n'Cause a lady came\n\n" +
            "[Chorus]\n'Cause this ugly drunken woman\n[Outro]\nLa, la-la, la-la"
        assertEquals(expected, extractGeniusLyrics(page))
    }

    @Test
    fun `handles nested divs inside a lyric container`() {
        val page = "<div data-lyrics-container=\"true\">" +
            "<div class=\"line\">Hello <div class=\"ref\">world</div> again</div>tail</div>"
        // A closing </div> is a block boundary, so it becomes a line break.
        assertEquals("Hello world\nagain\ntail", extractGeniusLyrics(page))
    }

    @Test
    fun `returns null when the page has no lyric containers`() {
        assertNull(extractGeniusLyrics("<html><body><p>No lyrics here</p></body></html>"))
        assertNull(extractGeniusLyrics("<div data-lyrics-container=\"true\">   </div>"))
    }

    @Test
    fun `decodes numeric and named entities`() {
        assertEquals("'Cause", decodeHtmlEntities("&#x27;Cause"))
        assertEquals("'Cause", decodeHtmlEntities("&#39;Cause"))
        assertEquals("T", decodeHtmlEntities("&#84;"))
        assertEquals("R&B", decodeHtmlEntities("R&amp;B"))
        assertEquals("a < b", decodeHtmlEntities("a &lt; b"))
        assertEquals("\"q\"", decodeHtmlEntities("&quot;q&quot;"))
        assertEquals("a b", decodeHtmlEntities("a&nbsp;b"))
        assertEquals("she’s", decodeHtmlEntities("she&#x2019;s"))
        assertEquals("&unknown;", decodeHtmlEntities("&unknown;"))
        assertEquals("plain & raw", decodeHtmlEntities("plain & raw"))
    }

    @Test
    fun `drops control characters from numeric references`() {
        assertEquals("", decodeHtmlEntities("&#x0;"))
        assertEquals("tab\tkept", decodeHtmlEntities("tab&#9;kept"))
    }

    @Test
    fun `picks exact title and artist hit over translations and partials`() {
        val exact = songHit(
            title = "Ugly Drunken Woman",
            artist = "Paddy and the Rats"
        )
        val translation = songHit(
            title = "Ugly Drunken Woman (Русский перевод)",
            artist = "Genius Russian Translations (Русский перевод)"
        )
        val remaster = songHit(
            title = "Ugly Drunken Woman (Remastered)",
            artist = "Paddy and the Rats"
        )
        val picked = pickGeniusSongHit(listOf(translation, remaster, exact), "Ugly Drunken Woman", "Paddy and the Rats")
        assertEquals(exact, picked)
    }

    @Test
    fun `skips instrumental and pending hits`() {
        val instrumental = songHit(title = "Song A", artist = "Artist", instrumental = true)
        val pending = songHit(title = "Song A", artist = "Artist", lyricsState = "pending")
        assertNull(pickGeniusSongHit(listOf(instrumental, pending), "Song A", "Artist"))
    }

    @Test
    fun `artist-only match is not enough`() {
        val wrongSong = songHit(title = "Completely Different Song", artist = "Paddy and the Rats")
        assertNull(pickGeniusSongHit(listOf(wrongSong), "Ugly Drunken Woman", "Paddy and the Rats"))
    }

    @Test
    fun `partial title containment still matches`() {
        val hit = songHit(
            title = "Ugly Drunken Woman (feat. Someone)",
            artist = "Paddy and the Rats"
        )
        val picked = pickGeniusSongHit(listOf(hit), "Ugly Drunken Woman", "Paddy and the Rats")
        assertEquals(hit, picked)
    }

    @Test
    fun `parses song hits from the search payload`() {
        val body = """
            {"meta":{"status":200},"response":{"sections":[
              {"type":"top_hit","hits":[{"type":"song","result":{"_type":"song","title":"Ugly Drunken Woman","url":"https://genius.com/a-lyrics"}}]},
              {"type":"song","hits":[{"type":"artist","result":{"_type":"artist","name":"Paddy and the Rats"}}]}
            ]}}
        """.trimIndent()
        val hits = parseGeniusSongHits(body)
        assertEquals(1, hits.size)
        assertEquals("Ugly Drunken Woman", hits.single()["title"]?.toString()?.trim('"'))
    }

    private fun songHit(
        title: String,
        artist: String,
        instrumental: Boolean = false,
        lyricsState: String = "complete"
    ) = buildJsonObject {
        put("title", title)
        put("title_with_featured", title)
        put("primary_artist_names", artist)
        put("artist_names", artist)
        put("instrumental", instrumental)
        put("lyrics_state", lyricsState)
        put("url", "https://genius.com/test-lyrics")
    }
}
