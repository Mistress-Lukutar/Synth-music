package com.synth.synthmusic.domain.usecase.ai

import com.synth.synthmusic.domain.model.AiMessagePart
import com.synth.synthmusic.domain.model.AiRole
import com.synth.synthmusic.domain.model.AiTurn

/**
 * Rough on-device context budgeting for chat requests: estimates the token
 * footprint of a transcript and slides the request window to fit a model's
 * context limit. There is no real tokenizer on the phone — estimates use
 * chars-per-token ratios per script class, which is accurate enough to keep
 * requests inside the window while wasting only a small margin.
 */
object ContextBudget {

    /** English-like text tokenizes at roughly 4 chars per token. */
    private const val ASCII_CHARS_PER_TOKEN = 4.0

    /**
     * Cyrillic/CJK text tokenizes much coarser (~1.5–2.5 chars per token
     * across common tokenizers), so non-ASCII is counted denser.
     */
    private const val NON_ASCII_CHARS_PER_TOKEN = 1.5

    /** Flat estimate for one image part (~1024 px vision input). */
    const val IMAGE_TOKENS = 1500

    /**
     * Estimates the token count of a plain string.
     */
    fun estimateTextTokens(text: String): Int {
        if (text.isEmpty()) return 0
        var ascii = 0
        var nonAscii = 0
        for (c in text) {
            if (c.code < 0x80) ascii++ else nonAscii++
        }
        val tokens = ascii / ASCII_CHARS_PER_TOKEN + nonAscii / NON_ASCII_CHARS_PER_TOKEN
        return kotlin.math.ceil(tokens).toInt()
    }

    /**
     * Estimates the token count of one turn as sent to the provider:
     * text, tool-call arguments and tool-result contents count fully, each
     * image counts [IMAGE_TOKENS], and [AiMessagePart.Reasoning] counts zero
     * because clients never replay reasoning back to the provider.
     */
    fun estimateTurnTokens(turn: AiTurn): Int =
        turn.parts.sumOf { part ->
            when (part) {
                is AiMessagePart.Text -> estimateTextTokens(part.text)
                is AiMessagePart.Image -> IMAGE_TOKENS
                is AiMessagePart.ToolCall -> estimateTextTokens(part.argumentsJson) + 8
                is AiMessagePart.ToolResult -> estimateTextTokens(part.content) + 8
                is AiMessagePart.Reasoning -> 0
            }
        }

    /**
     * Returns the longest suffix of [turns] whose estimated token count fits
     * [budgetTokens], cut at safe block boundaries.
     *
     * A block is the atomic unit of the protocol: an assistant turn with tool
     * calls plus the tool turns that answer it must travel together, so the
     * window never starts on an orphaned tool result and never ends on an
     * assistant turn whose tool calls went unanswered. The newest block is
     * always included even when it alone exceeds the budget — an empty
     * transcript is not a valid request, and the provider gets to decide how
     * to handle an oversized first message.
     */
    fun windowTurns(
        turns: List<AiTurn>,
        budgetTokens: Int,
        minTailTurns: Int = 1
    ): List<AiTurn> {
        if (turns.isEmpty()) return turns
        val blocks = buildBlocks(turns)
        // Walk blocks newest → oldest accumulating while they fit. The newest
        // block always travels even when it alone exceeds the budget — an
        // empty transcript is not a valid request.
        var firstIncluded = blocks.size
        var total = 0
        for (i in blocks.indices.reversed()) {
            if (total + blocks[i].tokens > budgetTokens && firstIncluded < blocks.size) break
            total += blocks[i].tokens
            firstIncluded = i
        }
        val start = blocks[firstIncluded].start
        // Guarantee a usable minimum even when the budget is tiny or zero.
        val safeStart = minOf(start, turns.size - minTailTurns).coerceAtLeast(0)
        return turns.subList(safeStart, turns.size)
    }

    /**
     * Splits [turns] into atomic blocks: a run of consecutive TOOL turns is
     * glued to the assistant turn with tool calls that precedes it; every
     * other turn forms its own block. The result never starts a block with a
     * TOOL turn (its request would be orphaned).
     */
    private fun buildBlocks(turns: List<AiTurn>): List<Block> {
        val blocks = mutableListOf<Block>()
        var index = 0
        while (index < turns.size) {
            val start = index
            var tokens = estimateTurnTokens(turns[index])
            index++
            if (turns[start].role == AiRole.ASSISTANT &&
                turns[start].toolCalls.isNotEmpty()
            ) {
                while (index < turns.size && turns[index].role == AiRole.TOOL) {
                    tokens += estimateTurnTokens(turns[index])
                    index++
                }
            }
            blocks.add(Block(start, tokens))
        }
        return blocks
    }

    private data class Block(val start: Int, val tokens: Int)
}
