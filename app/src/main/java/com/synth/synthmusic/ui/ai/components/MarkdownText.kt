package com.synth.synthmusic.ui.ai.components

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Minimal markdown renderer for chat bubbles: paragraphs, `**bold**`,
 * `*italic*`, `` `code` ``, fenced code blocks and `- ` bullet lists.
 * Deliberately dependency-free.
 *
 * @param markdown raw markdown text.
 * @param modifier the modifier to be applied to the container.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(markdown) { parseBlocks(markdown) }
    Column(modifier = modifier) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Code -> Text(
                    text = block.code,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceContainerHighest,
                            RoundedCornerShape(8.dp)
                        )
                        .horizontalScroll(rememberScrollState())
                        .padding(10.dp)
                )
                is MarkdownBlock.Paragraph -> Text(
                    text = annotateInline(block.text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

private sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock
    data class Code(val code: String) : MarkdownBlock
}

private fun parseBlocks(markdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraph = StringBuilder()
    var inCode = false
    val code = StringBuilder()
    for (line in markdown.lines()) {
        when {
            line.trimStart().startsWith("```") -> {
                if (inCode) {
                    blocks.add(MarkdownBlock.Code(code.toString().trimEnd()))
                    code.setLength(0)
                    inCode = false
                } else {
                    flushParagraph(paragraph, blocks)
                    inCode = true
                }
            }
            inCode -> {
                code.appendLine(line)
            }
            line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") -> {
                flushParagraph(paragraph, blocks)
                blocks.add(MarkdownBlock.Paragraph("• " + line.trimStart().drop(2)))
            }
            line.isBlank() -> flushParagraph(paragraph, blocks)
            else -> {
                if (paragraph.isNotEmpty()) paragraph.append('\n')
                paragraph.append(line)
            }
        }
    }
    if (inCode && code.isNotEmpty()) {
        blocks.add(MarkdownBlock.Code(code.toString().trimEnd()))
    }
    flushParagraph(paragraph, blocks)
    return blocks
}

private fun flushParagraph(paragraph: StringBuilder, blocks: MutableList<MarkdownBlock>) {
    if (paragraph.isNotEmpty()) {
        blocks.add(MarkdownBlock.Paragraph(paragraph.toString()))
        paragraph.setLength(0)
    }
}

/**
 * Applies inline markdown styling (bold, italic, code) to a paragraph.
 */
internal fun annotateInline(text: String): AnnotatedString = buildAnnotatedString {
    var index = 0
    while (index < text.length) {
        val bold = text.indexOf("**", index)
        val code = text.indexOf('`', index)
        val italic = text.indexOf('*', index)
        val next = listOfNotNull(
            bold.takeIf { it >= 0 },
            code.takeIf { it >= 0 },
            italic.takeIf { it >= 0 }
        ).minOrNull()

        if (next == null) {
            append(text.substring(index))
            break
        }
        append(text.substring(index, next))
        when (next) {
            code -> {
                val end = text.indexOf('`', next + 1)
                if (end == -1) {
                    append(text.substring(next))
                    break
                }
                pushStyle(SpanStyle(fontFamily = FontFamily.Monospace))
                append(text.substring(next + 1, end))
                pop()
                index = end + 1
            }
            bold -> {
                val end = text.indexOf("**", next + 2)
                if (end == -1) {
                    append(text.substring(next))
                    break
                }
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
                append(text.substring(next + 2, end))
                pop()
                index = end + 2
            }
            else -> {
                val end = text.indexOf('*', next + 1)
                if (end == -1) {
                    append(text.substring(next))
                    break
                }
                pushStyle(SpanStyle(fontStyle = FontStyle.Italic))
                append(text.substring(next + 1, end))
                pop()
                index = end + 1
            }
        }
    }
}
