package com.synth.synthmusic.ui.ai.components

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.parser.MarkdownParser
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Inline-markdown rendering tests: emphasis delimiters must never leak into
 * the rendered text (see [annotateNodes]).
 */
class MarkdownTextInlineTest {

    private val styles = InlineStyles(
        code = SpanStyle(fontFamily = FontFamily.Monospace),
        link = SpanStyle()
    )

    private fun render(markdown: String): String {
        val tree = MarkdownParser(GFMFlavourDescriptor())
            .buildMarkdownTreeFromString(markdown)
        val paragraph = tree.children.first()
        return annotateNodes(paragraph.children, markdown, styles).text
    }

    @Test
    fun `double-star bold strips delimiters`() {
        assertEquals("bold word stays", render("**bold word** stays"))
    }

    @Test
    fun `single-star italic strips delimiters`() {
        assertEquals("a italic b", render("a *italic* b"))
    }

    @Test
    fun `underscore emphasis strips delimiters`() {
        assertEquals("a under-bold b", render("a _under-bold_ b"))
    }

    @Test
    fun `strikethrough strips delimiters`() {
        assertEquals("a strike b", render("a ~~strike~~ b"))
    }

    @Test
    fun `bold containing code span keeps only content`() {
        assertEquals("a bold with code inside b", render("a **bold with `code` inside** b"))
    }

    @Test
    fun `unclosed markers render verbatim`() {
        assertEquals("a * b ** c", render("a * b ** c"))
    }
}
