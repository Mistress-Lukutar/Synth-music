package com.synth.synthmusic.data.ai.tools

/**
 * Punctuation-tolerant fuzzy matcher behind the AI `search_songs` tool.
 *
 * Handles library entries whose titles come from raw filenames such as
 * `Sheet_Music_Boss_-_RUSH_E_(SkySound.cc)`: every non letter/digit character
 * is treated as a separator, so the query "rush e" matches "RUSH_E".
 * Individual tokens additionally tolerate small typos via bounded Levenshtein
 * distance, and fields are weighted so title matches outrank artist/album
 * matches.
 *
 * Match semantics: a song matches when a single field (title, artist or
 * album) covers ALL query tokens. [partialScore] relaxes this to the best
 * average token coverage of any field and is meant as a fallback pass when
 * the strict pass finds nothing.
 */
internal class SongSearchMatcher(query: String) {

    private val tokens = normalizeSearchText(query)
        .split(' ')
        .filter { it.isNotEmpty() }

    /**
     * Strict score: returns the best weighted field score (0..1) when some
     * field covers every query token, or null when the song does not match.
     */
    fun strictScore(title: String, artist: String, album: String): Double? =
        fieldScores(title, artist, album)
            .filter { it.coverage == 1.0 }
            .maxByOrNull { it.score }
            ?.score

    /**
     * Relaxed score: best field score even when no field covers all tokens.
     * Returns null when fewer than half of the tokens are covered by the
     * best field, to keep partial results meaningful.
     */
    fun partialScore(title: String, artist: String, album: String): Double? =
        fieldScores(title, artist, album)
            .filter { it.coverage >= 0.5 }
            .maxByOrNull { it.score }
            ?.score

    private fun fieldScores(
        title: String,
        artist: String,
        album: String
    ): List<FieldScore> = listOf(
        fieldScore(title, TITLE_WEIGHT),
        fieldScore(artist, ARTIST_WEIGHT),
        fieldScore(album, ALBUM_WEIGHT)
    )

    private fun fieldScore(field: String, weight: Double): FieldScore {
        val normalizedField = normalizeSearchText(field)
        val words = normalizedField.split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty() || words.isEmpty()) return FieldScore(0.0, 0.0)
        val tokenScores = tokens.map { token -> tokenBestScore(token, words) }
        val coverage = tokenScores.count { it > 0.0 }.toDouble() / tokens.size
        val base = tokenScores.sum() / tokens.size
        // A field containing the whole normalized query is a perfect match
        // regardless of how the tokens scored individually.
        val phrase = if (normalizedField.contains(tokens.joinToString(" "))) 1.0 else base
        return FieldScore(phrase * weight, coverage)
    }

    /** Best score for one query token against every word of the field. */
    private fun tokenBestScore(token: String, words: List<String>): Double =
        words.maxOf { word -> wordScore(word, token) }

    private fun wordScore(word: String, token: String): Double = when {
        word == token -> EXACT
        token.length >= MIN_CONTAIN_LEN && word.contains(token) -> WORD_CONTAINS_TOKEN
        word.length >= MIN_CONTAIN_LEN && token.contains(word) -> TOKEN_CONTAINS_WORD
        levenshtein(word, token) <= fuzzyThreshold(word.length, token.length) -> FUZZY
        else -> 0.0
    }

    /** Edit-distance tolerance grows with token length; very short tokens must match exactly. */
    private fun fuzzyThreshold(wordLength: Int, tokenLength: Int): Int = when (maxOf(wordLength, tokenLength)) {
        in 0..2 -> 0
        in 3..5 -> 1
        else -> 2
    }

    private data class FieldScore(val score: Double, val coverage: Double)

    private companion object {
        const val EXACT = 1.0
        const val WORD_CONTAINS_TOKEN = 0.9
        const val TOKEN_CONTAINS_WORD = 0.8
        const val FUZZY = 0.7
        const val MIN_CONTAIN_LEN = 3
        const val TITLE_WEIGHT = 1.0
        const val ARTIST_WEIGHT = 0.85
        const val ALBUM_WEIGHT = 0.7
    }
}

/**
 * Shared text normalization: lowercases and collapses every run of
 * non letter/digit characters (underscores, dashes, dots, brackets,
 * whitespace) into a single space. Unicode-aware, so Cyrillic survives.
 */
internal fun normalizeSearchText(value: String): String =
    Regex("[^\\p{L}\\p{N}]+").replace(value.lowercase(), " ").trim()

/** Levenshtein edit distance (small inputs only — search tokens, not songs). */
internal fun levenshtein(a: String, b: String): Int {
    if (a == b) return 0
    if (a.isEmpty()) return b.length
    if (b.isEmpty()) return a.length
    var previous = IntArray(b.length + 1) { it }
    var current = IntArray(b.length + 1)
    for (i in 1..a.length) {
        current[0] = i
        for (j in 1..b.length) {
            current[j] = minOf(
                previous[j] + 1,
                current[j - 1] + 1,
                previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
            )
        }
        val swap = previous
        previous = current
        current = swap
    }
    return previous[b.length]
}
