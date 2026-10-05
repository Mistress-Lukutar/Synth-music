package com.synth.synthmusic.data.ai.client

import com.synth.synthmusic.domain.model.AiMessagePart
import com.synth.synthmusic.domain.model.AiRole
import com.synth.synthmusic.domain.model.AiToolSpec
import com.synth.synthmusic.domain.model.ChatStreamEvent
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Request/response mapping tests for the OpenAI wire format.
 */
class ChatClientSupportTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `user turn with images becomes content array`() {
        val turn = com.synth.synthmusic.domain.model.AiTurn(
            role = AiRole.USER,
            parts = listOf(
                AiMessagePart.Text("What is this?"),
                AiMessagePart.Image("image/jpeg", "abc123")
            )
        )
        val messages = turn.toOpenAiMessages()
        assertEquals(1, messages.size)
        val content = messages[0]["content"]
        assertTrue(content.toString().contains("image_url"))
        assertTrue(content.toString().contains("data:image/jpeg;base64,abc123"))
    }

    @Test
    fun `tool results expand to role tool messages`() {
        val turn = com.synth.synthmusic.domain.model.AiTurn(
            role = AiRole.TOOL,
            parts = listOf(
                AiMessagePart.ToolResult("c1", "search_songs", "[]"),
                AiMessagePart.ToolResult("c2", "library_stats", "{}")
            )
        )
        val messages = turn.toOpenAiMessages()
        assertEquals(2, messages.size)
        assertEquals("tool", messages[0]["role"]!!.toString().trim('"'))
        assertTrue(messages[0].toString().contains("tool_call_id"))
    }

    @Test
    fun `tool specs map to function tool format`() {
        val specs = listOf(
            AiToolSpec(
                name = "search_songs",
                description = "Search songs",
                paramsSchema = buildJsonObject { put("type", "object") }
            )
        )
        val tools = toolsToOpenAiJson(specs)
        assertTrue(tools.toString().contains("\"type\":\"function\""))
        assertTrue(tools.toString().contains("\"parameters\":{\"type\":\"object\"}"))
    }

    @Test
    fun `openai chunk parses text delta and finish`() {
        val events = parseOpenAiChunk(
            """
            {"choices":[{"delta":{"content":"Hi"},"finish_reason":null}],
             "usage":{"prompt_tokens":10,"completion_tokens":2}}
            """.trimIndent(),
            json
        )
        assertTrue(events.contains(ChatStreamEvent.TextDelta("Hi")))
        assertTrue(events.contains(ChatStreamEvent.Usage(10, 2)))
    }

    @Test
    fun `openai chunk parses tool calls`() {
        val events = parseOpenAiChunk(
            """
            {"choices":[{"delta":{"tool_calls":[{"id":"call_1","type":"function",
             "function":{"name":"search_songs","arguments":"{}"}}]},
             "finish_reason":"tool_calls"}]}
            """.trimIndent(),
            json
        )
        val call = events.filterIsInstance<ChatStreamEvent.ToolCallReceived>().first()
        assertEquals("search_songs", call.call.name)
        assertEquals("call_1", call.call.id)
        assertTrue(events.contains(ChatStreamEvent.Completed(com.synth.synthmusic.domain.model.StopReason.TOOL_USE)))
    }

    @Test
    fun `openai error chunk surfaces failure`() {
        val events = parseOpenAiChunk(
            """{"error":{"message":"Incorrect API key"}}""",
            json
        )
        val failure = events.filterIsInstance<ChatStreamEvent.Failed>().first()
        assertTrue(failure.throwable.message!!.contains("Incorrect API key"))
    }

    @Test
    fun `malformed args fall back to empty object`() {
        assertEquals(buildJsonObject { }, parseArgsOrEmpty("not json"))
    }
}
