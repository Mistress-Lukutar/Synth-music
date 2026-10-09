package com.synth.synthmusic.domain.usecase.ai

import com.synth.synthmusic.domain.model.AiActionOutcome
import com.synth.synthmusic.domain.model.AiCapability
import com.synth.synthmusic.domain.model.AiMessagePart
import com.synth.synthmusic.domain.model.AiToolCall
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Permission-matrix and audit tests for [ToolDispatcherImpl].
 */
class ToolDispatcherImplTest {

    private class FakeTool(
        override val name: String,
        override val requiredGrants: Set<AiCapability>,
        override val confirmationLevel: ConfirmationLevel = ConfirmationLevel.NONE,
        private val result: ToolOutcome = ToolOutcome("ok"),
        private val available: Boolean = true
    ) : AiTool {
        override val description = "fake"
        override val paramsSchema: JsonObject = buildJsonObject {
            put("type", "object")
        }
        var executed = false
        override suspend fun isAvailable() = available
        override suspend fun execute(args: JsonObject, context: ToolContext): ToolOutcome {
            executed = true
            return result
        }
    }

    private class FakeActionLog : com.synth.synthmusic.domain.repository.AiActionLogRepository {
        val outcomes = mutableListOf<Pair<String, AiActionOutcome>>()
        override fun observeAll() = kotlinx.coroutines.flow.flowOf(emptyList<com.synth.synthmusic.domain.model.AiActionLogEntry>())
        override fun observeForChat(chatId: Long) = kotlinx.coroutines.flow.flowOf(emptyList<com.synth.synthmusic.domain.model.AiActionLogEntry>())
        override suspend fun log(
            chatId: Long?,
            toolName: String,
            argsJson: String,
            outcome: AiActionOutcome,
            summary: String
        ) {
            outcomes.add(toolName to outcome)
        }
    }

    private class FakeSettings(
        var confirmEdits: Boolean = true
    ) : com.synth.synthmusic.domain.repository.AiSettingsRepository {
        override val settings = kotlinx.coroutines.flow.flowOf(
            com.synth.synthmusic.domain.model.AiSettings(confirmEdits = confirmEdits)
        )
        override suspend fun current() =
            com.synth.synthmusic.domain.model.AiSettings(confirmEdits = confirmEdits)
        override suspend fun setActiveModel(providerId: Long, modelId: String) = Unit
        override suspend fun clearActiveModel() = Unit
        override suspend fun setCapabilityEnabled(
            capability: com.synth.synthmusic.domain.model.AiCapability,
            enabled: Boolean
        ) = Unit
        override suspend fun setConfirmEdits(enabled: Boolean) {
            confirmEdits = enabled
        }
        override suspend fun setWebSearchProvider(
            provider: com.synth.synthmusic.domain.model.WebSearchProvider
        ) = Unit
        override suspend fun setWebSearchKey(plaintext: String?) = Unit
        override suspend fun getWebSearchKey(): String? = null
    }

    private fun dispatcher(vararg tools: AiTool) =
        ToolDispatcherImpl(tools.toList(), FakeActionLog(), FakeSettings())

    private fun context(grants: Set<AiCapability>) = ToolContext(chatId = 1L, grants = grants)

    private fun call(name: String) = AiToolCall("call_1", name, "{}")

    @Test
    fun `unknown tool returns error result`() = runTest {
        val d = dispatcher()
        val result = d.execute(call("nope"), context(emptySet()))
        assertTrue(result.isError)
        assertEquals("Unknown tool: nope", result.content)
    }

    @Test
    fun `missing grant denies without executing`() = runTest {
        val tool = FakeTool("t", requiredGrants = setOf(AiCapability.WRITE_METADATA))
        val d = dispatcher(tool)
        val result = d.execute(call("t"), context(setOf(AiCapability.READ_LIBRARY)))
        assertTrue(result.isError)
        assertEquals(false, tool.executed)
    }

    @Test
    fun `granted tool executes`() = runTest {
        val tool = FakeTool("t", requiredGrants = setOf(AiCapability.WRITE_METADATA))
        val d = dispatcher(tool)
        val result = d.execute(call("t"), context(setOf(AiCapability.WRITE_METADATA)))
        assertEquals("ok", result.content)
        assertEquals(true, tool.executed)
    }

    @Test
    fun `specs filtered by grants`() = runTest {
        val read = FakeTool("read", requiredGrants = setOf(AiCapability.READ_LIBRARY))
        val write = FakeTool("write", requiredGrants = setOf(AiCapability.WRITE_METADATA))
        val d = dispatcher(read, write)
        val specs = d.specsFor(setOf(AiCapability.READ_LIBRARY))
        assertEquals(listOf("read"), specs.map { it.name })
    }

    @Test
    fun `unavailable tool is not advertised`() = runTest {
        val off = FakeTool("off", requiredGrants = emptySet(), available = false)
        val on = FakeTool("on", requiredGrants = emptySet())
        val d = dispatcher(off, on)
        val specs = d.specsFor(emptySet())
        assertEquals(listOf("on"), specs.map { it.name })
    }

    @Test
    fun `confirmation denied returns user denied`() = runTest {
        val tool = FakeTool(
            "t", requiredGrants = emptySet(),
            confirmationLevel = ConfirmationLevel.CONFIRM
        )
        val d = dispatcher(tool)
        val deny = object : ApprovalBridge {
            override suspend fun requestConfirmation(request: ConfirmationRequest) = false
            override fun isAlwaysAllowed(toolName: String) = false
        }
        val result = d.execute(call("t"), ToolContext(1L, emptySet(), deny))
        assertTrue(result.isError)
        assertTrue(result.content.contains("denied"))
    }

    @Test
    fun `always allowed in chat skips confirmation`() = runTest {
        val tool = FakeTool(
            "t", requiredGrants = emptySet(),
            confirmationLevel = ConfirmationLevel.CONFIRM
        )
        val d = dispatcher(tool)
        val allow = object : ApprovalBridge {
            override suspend fun requestConfirmation(request: ConfirmationRequest) = true
            override fun isAlwaysAllowed(toolName: String) = true
        }
        val result = d.execute(call("t"), ToolContext(1L, emptySet(), allow))
        assertEquals("ok", result.content)
    }

    @Test
    fun `always confirm level is asked even when always allowed`() = runTest {
        val tool = FakeTool(
            "t", requiredGrants = emptySet(),
            confirmationLevel = ConfirmationLevel.ALWAYS_CONFIRM
        )
        val d = dispatcher(tool)
        val deny = object : ApprovalBridge {
            override suspend fun requestConfirmation(request: ConfirmationRequest) = false
            override fun isAlwaysAllowed(toolName: String) = true
        }
        val result = d.execute(call("t"), ToolContext(1L, emptySet(), deny))
        assertTrue(result.isError)
    }

    @Test
    fun `confirm-edits off auto-approves CONFIRM tools`() = runTest {
        val tool = FakeTool(
            "t", requiredGrants = emptySet(),
            confirmationLevel = ConfirmationLevel.CONFIRM
        )
        val settings = FakeSettings(confirmEdits = false)
        val d = ToolDispatcherImpl(listOf(tool), FakeActionLog(), settings)
        val result = d.execute(call("t"), context(emptySet()))
        assertEquals("ok", result.content)
        assertEquals(true, tool.executed)
    }

    @Test
    fun `headless context auto-denies confirmation`() = runTest {
        val tool = FakeTool(
            "t", requiredGrants = emptySet(),
            confirmationLevel = ConfirmationLevel.CONFIRM
        )
        val d = dispatcher(tool)
        val result = d.execute(call("t"), context(emptySet()))
        assertTrue(result.isError)
    }

    @Test
    fun `every call is audited`() = runTest {
        val log = FakeActionLog()
        val d = ToolDispatcherImpl(
            listOf(FakeTool("t", requiredGrants = emptySet())), log, FakeSettings()
        )
        d.execute(call("t"), context(emptySet()))
        d.execute(call("unknown"), context(emptySet()))
        assertEquals(2, log.outcomes.size)
        assertEquals(AiActionOutcome.EXECUTED, log.outcomes[0].second)
        assertEquals(AiActionOutcome.FAILED, log.outcomes[1].second)
    }

    @Test
    fun `oversized results are truncated`() {
        val big = "x".repeat(MAX_TOOL_RESULT_CHARS + 100)
        val truncated = truncateToolResult(big)
        assertTrue(truncated.length <= MAX_TOOL_RESULT_CHARS + TRUNCATION_MARKER.length)
        assertTrue(truncated.endsWith(TRUNCATION_MARKER))
    }

    @Test
    fun `image sink receives vision results`() = runTest {
        val image = com.synth.synthmusic.domain.model.AiMessagePart.Image("image/jpeg", "abc")
        val tool = FakeTool(
            "vision", requiredGrants = emptySet(),
            result = ToolOutcome("attached", image = image)
        )
        val d = dispatcher(tool)
        val sink = mutableListOf<AiMessagePart.Image>()
        d.execute(call("vision"), ToolContext(1L, emptySet(), imageSink = sink))
        assertEquals(1, sink.size)
    }
}
