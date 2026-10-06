package com.synth.synthmusic.data.ai.tools

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for [stripSyncedTimestamps] used by `get_lyrics_online` to
 * convert synced (LRC) lyrics into clean plain text.
 */
class StripSyncedTimestampsTest {

    @Test
    fun `strips leading timestamps from every line`() {
        val lrc = "[00:12.00]First line\n[01:45.30]Second line"
        assertEquals("First line\nSecond line", stripSyncedTimestamps(lrc))
    }

    @Test
    fun `handles multiple timestamps on one line`() {
        val lrc = "[00:10.00][01:20.50]Chorus text"
        assertEquals("Chorus text", stripSyncedTimestamps(lrc))
    }

    @Test
    fun `drops metadata tags and leaves only content lines`() {
        val lrc = "[ar:Artist]\n[ti:Title]\n[by:Someone]\n[00:01.00]Real line"
        assertEquals("Real line", stripSyncedTimestamps(lrc))
    }

    @Test
    fun `keeps bracketed words inside lyric text`() {
        val lrc = "[00:05.00]Say [Chorus] loudly"
        assertEquals("Say [Chorus] loudly", stripSyncedTimestamps(lrc))
    }

    @Test
    fun `blank input yields empty string`() {
        assertEquals("", stripSyncedTimestamps("\n \n[00:00.00]\n"))
    }
}
