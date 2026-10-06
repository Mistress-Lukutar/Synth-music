package com.synth.synthmusic.data.ai.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [SongSearchMatcher], the fuzzy matcher behind the AI
 * `search_songs` tool.
 */
class SongSearchMatcherTest {

    private val junkTitle = "Sheet_Music_Boss_-_RUSH_E_(SkySound.cc)"

    @Test
    fun `separator tolerance - underscored filename matches spaced query`() {
        val matcher = SongSearchMatcher("rush e")
        val score = matcher.strictScore(junkTitle, "<unknown>", "Saved")
        assertNotNull(score)
        assertTrue(score!! > 0.0)
    }

    @Test
    fun `normalization strips punctuation and lowercases`() {
        assertEquals("rush e 2018 mp3", normalizeSearchText("RUSH_E  (2018).mp3"))
        assertEquals("карелия 2024", normalizeSearchText("Карелия-2024"))
    }

    @Test
    fun `typo tolerance - single edit distance matches`() {
        val matcher = SongSearchMatcher("rushw e")
        assertNotNull(matcher.strictScore(junkTitle, "<unknown>", "Saved"))
    }

    @Test
    fun `very short tokens require exact match`() {
        val matcher = SongSearchMatcher("e")
        assertNull(matcher.strictScore("The Firm", "<unknown>", "Saved"))
    }

    @Test
    fun `full phrase match outranks per-token match`() {
        val matcher = SongSearchMatcher("brush your teeth")
        val phrase = matcher.strictScore("Brush Your Teeth", "Ridiculon", "Mewgenics")
        val scattered = matcher.strictScore("Teeth", "Brush", "Brush Your Teeth")
        assertNotNull(phrase)
        assertNotNull(scattered)
        assertTrue(phrase!! > scattered!!)
    }

    @Test
    fun `title match outranks album match`() {
        val matcher = SongSearchMatcher("mewgenics")
        val titleHit = matcher.strictScore("Mewgenics Theme", "Ridiculon", "Other")
        val albumHit = matcher.strictScore("Unrelated", "Ridiculon", "Mewgenics")
        assertNotNull(titleHit)
        assertNotNull(albumHit)
        assertTrue(titleHit!! > albumHit!!)
    }

    @Test
    fun `strict pass rejects query tokens spread across fields`() {
        val matcher = SongSearchMatcher("rush ridiculon")
        assertNull(matcher.strictScore(junkTitle, "Ridiculon", "Saved"))
    }

    @Test
    fun `partial pass accepts query tokens spread across fields`() {
        val matcher = SongSearchMatcher("rush sheet boss")
        val score = matcher.partialScore(junkTitle, "Sheet Music Boss", "Saved")
        assertNotNull(score)
        assertTrue(score!! > 0.0)
    }

    @Test
    fun `partial pass rejects matches below half coverage`() {
        val matcher = SongSearchMatcher("rush e completely unrelated words")
        assertNull(matcher.partialScore(junkTitle, "<unknown>", "Saved"))
    }

    @Test
    fun `levenshtein basics`() {
        assertEquals(0, levenshtein("rush", "rush"))
        assertEquals(1, levenshtein("rush", "rusk"))
        assertEquals(3, levenshtein("kitten", "sitting"))
        assertEquals(3, levenshtein("", "abc"))
    }

    @Test
    fun `empty query never matches`() {
        val matcher = SongSearchMatcher("   ")
        assertNull(matcher.strictScore(junkTitle, "x", "y"))
        assertNull(matcher.partialScore(junkTitle, "x", "y"))
    }
}
