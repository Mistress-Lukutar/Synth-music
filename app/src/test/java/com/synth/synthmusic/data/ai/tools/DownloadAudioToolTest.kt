package com.synth.synthmusic.data.ai.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Unit tests for the pure helpers of `download_audio`: MP3 signature check,
 * folder path normalization, file name suggestion and duplicate uniquification.
 */
class DownloadAudioToolTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    // --- looksLikeMp3 ---

    @Test
    fun `id3 tag header is accepted`() {
        val head = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte(), 0x04)
        assertTrue(looksLikeMp3(head))
    }

    @Test
    fun `mpeg frame sync is accepted`() {
        // 0xFF 0xFB 0x90 = MPEG1 Layer III, 128 kbps, 44.1 kHz
        assertTrue(looksLikeMp3(byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x90.toByte())))
    }

    @Test
    fun `two-byte frame sync without header body is accepted`() {
        assertTrue(looksLikeMp3(byteArrayOf(0xFF.toByte(), 0xFB.toByte())))
    }

    @Test
    fun `reserved version bits are rejected`() {
        // 0xE8 = version 01 (reserved)
        assertFalse(looksLikeMp3(byteArrayOf(0xFF.toByte(), 0xE8.toByte())))
    }

    @Test
    fun `reserved layer bits are rejected`() {
        // 0xE1 = layer 00 (reserved)
        assertFalse(looksLikeMp3(byteArrayOf(0xFF.toByte(), 0xE1.toByte())))
    }

    @Test
    fun `invalid bitrate index is rejected`() {
        assertFalse(looksLikeMp3(byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0xF0.toByte())))
    }

    @Test
    fun `reserved sample rate is rejected`() {
        assertFalse(looksLikeMp3(byteArrayOf(0xFF.toByte(), 0xFB.toByte(), 0x0C.toByte())))
    }

    @Test
    fun `non-mp3 data is rejected`() {
        assertFalse(looksLikeMp3("OggS".toByteArray()))
        assertFalse(looksLikeMp3("<html>".toByteArray()))
        assertFalse(looksLikeMp3(byteArrayOf(0xFF.toByte())))
        assertFalse(looksLikeMp3(ByteArray(0)))
    }

    // --- normalizeRelativeFolder ---

    @Test
    fun `single segment passes through`() {
        assertEquals(listOf("Rock"), normalizeRelativeFolder("Rock"))
    }

    @Test
    fun `nested path splits into segments`() {
        assertEquals(listOf("Archive", "2024"), normalizeRelativeFolder("Archive/2024"))
    }

    @Test
    fun `leading trailing and duplicate slashes are tolerated`() {
        assertEquals(listOf("Rock"), normalizeRelativeFolder("/Rock/"))
        assertEquals(listOf("A", "B"), normalizeRelativeFolder("A//B"))
    }

    @Test
    fun `illegal filename characters are stripped`() {
        assertEquals(listOf("Rock"), normalizeRelativeFolder("Ro?ck:"))
    }

    @Test
    fun `dot segments are rejected`() {
        assertNull(normalizeRelativeFolder("."))
        assertNull(normalizeRelativeFolder(".."))
        assertNull(normalizeRelativeFolder("..."))
        assertNull(normalizeRelativeFolder("a/../.."))
    }

    @Test
    fun `blank input is rejected`() {
        assertNull(normalizeRelativeFolder(""))
        assertNull(normalizeRelativeFolder("   "))
        assertNull(normalizeRelativeFolder("///"))
    }

    // --- suggestFileName ---

    @Test
    fun `explicit name wins and gets mp3 extension`() {
        assertEquals("Artist - Title.mp3", suggestFileName("Artist - Title", "http://x/f.mp3"))
    }

    @Test
    fun `explicit mp3 name is kept as is`() {
        assertEquals("song.mp3", suggestFileName("song.mp3", "http://x/f.mp3"))
    }

    @Test
    fun `url segment is decoded and query stripped`() {
        assertEquals(
            "Nightcall - Kavinsky.mp3",
            suggestFileName(null, "https://host/dl/Nightcall%20-%20Kavinsky.mp3?token=abc")
        )
    }

    @Test
    fun `empty url segment falls back to download`() {
        assertEquals("download.mp3", suggestFileName(null, "https://host/download/"))
    }

    @Test
    fun `path separators are stripped from explicit names`() {
        assertEquals("abc.mp3", suggestFileName("a/b\\c", "http://x/f.mp3"))
    }

    // --- uniqueTargetFile ---

    @Test
    fun `returns plain name when free`() {
        val dir = tempFolder.newFolder()
        assertEquals(File(dir, "Song.mp3"), uniqueTargetFile(dir, "Song.mp3"))
    }

    @Test
    fun `uniquifies on conflict`() {
        val dir = tempFolder.newFolder()
        assertTrue(File(dir, "Song.mp3").createNewFile())
        assertEquals(File(dir, "Song (1).mp3"), uniqueTargetFile(dir, "Song.mp3"))
    }

    @Test
    fun `skips taken uniquified names`() {
        val dir = tempFolder.newFolder()
        assertTrue(File(dir, "Song.mp3").createNewFile())
        assertTrue(File(dir, "Song (1).mp3").createNewFile())
        assertEquals(File(dir, "Song (2).mp3"), uniqueTargetFile(dir, "Song.mp3"))
    }
}
