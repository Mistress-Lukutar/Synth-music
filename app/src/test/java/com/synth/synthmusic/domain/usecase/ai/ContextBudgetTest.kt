package com.synth.synthmusic.domain.usecase.ai

import com.synth.synthmusic.domain.model.AiMessagePart
import com.synth.synthmusic.domain.model.AiRole
import com.synth.synthmusic.domain.model.AiTurn
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [ContextBudget] token estimation and transcript windowing.
 */
class ContextBudgetTest {

    private fun text(s: String) = AiTurn(AiRole.USER, listOf(AiMessagePart.Text(s)))
    private fun assistantToolCall(id: String) = AiTurn(
        AiRole.ASSISTANT,
        listOf(AiMessagePart.ToolCall(id, "search_songs", """{"query":"x"}"""))
    )
    private fun toolResult(id: String) = AiTurn(
        AiRole.TOOL,
        listOf(AiMessagePart.ToolResult(id, "search_songs", "[]"))
    )

    @Test
    fun `ascii text estimates at four chars per token`() {
        assertEquals(2, ContextBudget.estimateTextTokens("12345678"))
        assertEquals(0, ContextBudget.estimateTextTokens(""))
    }

    @Test
    fun `non-ascii text estimates denser`() {
        // 6 Cyrillic chars at 1.5 chars/token → 4 tokens, vs 2 for ASCII.
        assertEquals(4, ContextBudget.estimateTextTokens("привет"))
    }

    @Test
    fun `reasoning parts are never counted`() {
        val turn = AiTurn(
            AiRole.ASSISTANT,
            listOf(AiMessagePart.Reasoning("x".repeat(10_000)))
        )
        assertEquals(0, ContextBudget.estimateTurnTokens(turn))
    }

    @Test
    fun `image parts count flat`() {
        val turn = AiTurn(
            AiRole.USER,
            listOf(
                AiMessagePart.Text("hi"),
                AiMessagePart.Image("image/jpeg", "AAAA")
            )
        )
        assertEquals(1 + ContextBudget.IMAGE_TOKENS, ContextBudget.estimateTurnTokens(turn))
    }

    @Test
    fun `window cuts at block boundaries keeping tool pairs together`() {
        val turns = listOf(
            text("a".repeat(400)),      // ~100 tokens
            assistantToolCall("t1"),    // block with result ≈ 21 tokens
            toolResult("t1"),
            text("b".repeat(400))       // ~100 tokens
        )
        // Budget fits the tool block + last user turn, not the first turn.
        val window = ContextBudget.windowTurns(turns, budgetTokens = 150)
        assertEquals(turns.subList(1, 4), window)
        assertEquals(AiRole.ASSISTANT, window.first().role)
        assertEquals(AiRole.USER, window.last().role)
    }

    @Test
    fun `window never starts on an orphaned tool result`() {
        val turns = listOf(
            text("x".repeat(400)),      // ~100 tokens, dropped by the budget
            assistantToolCall("t1"),
            toolResult("t1")
        )
        val window = ContextBudget.windowTurns(turns, budgetTokens = 110)
        assertEquals(listOf(AiRole.ASSISTANT, AiRole.TOOL), window.map { it.role })
    }

    @Test
    fun `window keeps newest block even over budget`() {
        val turns = listOf(text("x".repeat(10_000)))
        val window = ContextBudget.windowTurns(turns, budgetTokens = 10)
        assertEquals(turns, window)
    }

    @Test
    fun `window with zero budget still returns the tail`() {
        val turns = listOf(
            text("a".repeat(400)),
            text("b".repeat(400))
        )
        val window = ContextBudget.windowTurns(turns, budgetTokens = 0)
        assertEquals(1, window.size)
        assertEquals(turns.last(), window.first())
    }

    @Test
    fun `window fits large budget entirely`() {
        val turns = listOf(
            text("a".repeat(400)),
            text("b".repeat(400))
        )
        assertEquals(turns, ContextBudget.windowTurns(turns, budgetTokens = 100_000))
    }

    @Test
    fun `empty turns pass through`() {
        assertEquals(emptyList<AiTurn>(), ContextBudget.windowTurns(emptyList(), 100))
    }
}
