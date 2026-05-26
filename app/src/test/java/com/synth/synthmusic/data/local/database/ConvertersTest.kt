package com.synth.synthmusic.data.local.database

import org.junit.Assert.assertEquals
import org.junit.Test

class ConvertersTest {

    private val converters = Converters()

    @Test
    fun `toIntList converts comma separated string to list`() {
        val result = converters.fromIntList("1, 2, 3")
        assertEquals(listOf(1, 2, 3), result)
    }

    @Test
    fun `toIntList returns empty list for blank string`() {
        val result = converters.fromIntList("")
        assertEquals(emptyList<Int>(), result)
    }

    @Test
    fun `fromIntList converts list to comma separated string`() {
        val result = converters.toIntList(listOf(1, 2, 3))
        assertEquals("1,2,3", result)
    }

    @Test
    fun `toFloatList converts comma separated string to list`() {
        val result = converters.fromFloatList("1.5, 2.5, 3.5")
        assertEquals(listOf(1.5f, 2.5f, 3.5f), result)
    }

    @Test
    fun `fromFloatList converts list to comma separated string`() {
        val result = converters.toFloatList(listOf(1.5f, 2.5f, 3.5f))
        assertEquals("1.5,2.5,3.5", result)
    }
}
