package com.synth.synthmusic.ui.ai.components

import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [visibleBlocks], the filter that keeps blank-line tokens
 * left by the parser out of the rendered block list.
 */
class MarkdownTextTest {

    private fun parse(markdown: String): List<ASTNode> =
        MarkdownParser(GFMFlavourDescriptor())
            .buildMarkdownTreeFromString(markdown)
            .children

    private fun paragraphs(markdown: String) =
        visibleBlocks(parse(markdown)).filter {
            it.type == MarkdownElementTypes.PARAGRAPH
        }

    @Test
    fun `paragraph separated by blank line has no whitespace tokens`() {
        val blocks = visibleBlocks(parse("Line one\n\nLine two"))
        assertTrue(blocks.none { it.type == MarkdownTokenTypes.EOL })
        assertTrue(blocks.none { it.type == MarkdownTokenTypes.WHITE_SPACE })
        assertEquals(2, blocks.size)
    }

    @Test
    fun `multiple consecutive blank lines collapse to blocks only`() {
        val blocks = visibleBlocks(parse("Line one\n\n\n\n\nLine two"))
        assertTrue(blocks.all { it.type == MarkdownElementTypes.PARAGRAPH })
        assertEquals(2, blocks.size)
    }

    @Test
    fun `leading and trailing blank lines are dropped`() {
        val blocks = visibleBlocks(parse("\n\n\nLine one\n\n\n"))
        assertEquals(listOf(MarkdownElementTypes.PARAGRAPH), blocks.map { it.type })
    }

    @Test
    fun `paragraph text is preserved across a blank line`() {
        val markdown = "First\n\nSecond"
        val texts = paragraphs(markdown).map { node ->
            markdown.substring(node.startOffset, node.endOffset)
        }
        assertEquals(listOf("First", "Second"), texts)
    }
}
