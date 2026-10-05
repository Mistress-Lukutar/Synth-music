package com.synth.synthmusic.data.ai.client

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for the shared SSE line parser.
 */
class SseParserTest {

    private fun parse(lines: List<String>) = lines.asSequence().asSseFrames().toList()

    @Test
    fun `parses simple data frames`() {
        val frames = parse(
            listOf(
                "data: {\"a\":1}",
                "",
                "data: {\"a\":2}",
                ""
            )
        )
        assertEquals(2, frames.size)
        assertEquals("{\"a\":1}", frames[0].data)
        assertNull(frames[0].event)
    }

    @Test
    fun `parses anthropic event frames`() {
        val frames = parse(
            listOf(
                "event: content_block_delta",
                "data: {\"delta\":{\"text\":\"Hi\"}}",
                "",
                "event: message_stop",
                "data: {}",
                ""
            )
        )
        assertEquals("content_block_delta", frames[0].event)
        assertEquals("message_stop", frames[1].event)
    }

    @Test
    fun `accumulates multi-line data payloads`() {
        val frames = parse(
            listOf(
                "data: line1",
                "data: line2",
                ""
            )
        )
        assertEquals(1, frames.size)
        assertEquals("line1\nline2", frames[0].data)
    }

    @Test
    fun `handles split mid-event input`() {
        // A stream interrupted after the data line but before the blank line
        // still yields the pending frame at EOF.
        val frames = parse(listOf("data: {\"partial\":true}"))
        assertEquals(1, frames.size)
        assertEquals("{\"partial\":true}", frames[0].data)
    }

    @Test
    fun `ignores comment lines and openai done marker passthrough`() {
        val frames = parse(
            listOf(
                ": keep-alive comment",
                "data: [DONE]",
                ""
            )
        )
        assertEquals(1, frames.size)
        assertEquals("[DONE]", frames[0].data)
    }

    @Test
    fun `handles empty stream`() {
        assertEquals(0, parse(emptyList()).size)
    }
}
