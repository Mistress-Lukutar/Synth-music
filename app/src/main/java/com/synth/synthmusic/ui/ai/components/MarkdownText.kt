package com.synth.synthmusic.ui.ai.components

import android.content.ClipData
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.synth.synthmusic.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.intellij.markdown.MarkdownElementTypes
import org.intellij.markdown.MarkdownTokenTypes
import org.intellij.markdown.ast.ASTNode
import org.intellij.markdown.flavours.gfm.GFMElementTypes
import org.intellij.markdown.flavours.gfm.GFMFlavourDescriptor
import org.intellij.markdown.flavours.gfm.GFMTokenTypes
import org.intellij.markdown.parser.MarkdownParser

/**
 * Markdown renderer for AI chat messages, backed by the JetBrains markdown
 * parser with the GFM flavour. Supported: headings, paragraphs, ordered /
 * unordered / nested / task lists, fenced and indented code blocks with a
 * copy button, GFM tables, block quotes, horizontal rules, inline bold /
 * italic / strikethrough / inline code and links.
 *
 * @param markdown raw markdown text.
 * @param modifier the modifier to be applied to the container.
 */
@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier
) {
    val tree = remember(markdown) {
        MarkdownParser(GFMFlavourDescriptor()).buildMarkdownTreeFromString(markdown)
    }
    val colorScheme = MaterialTheme.colorScheme
    val styles = remember(colorScheme) {
        InlineStyles(
            code = SpanStyle(
                fontFamily = FontFamily.Monospace,
                background = colorScheme.surfaceContainerHighest
            ),
            link = SpanStyle(
                color = colorScheme.primary,
                textDecoration = TextDecoration.Underline
            )
        )
    }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        tree.children.forEach { child -> MarkdownBlock(child, markdown, styles) }
    }
}

/** Theme-derived span styles shared by all inline rendering. */
internal class InlineStyles(
    val code: SpanStyle,
    val link: SpanStyle
)

@Composable
private fun MarkdownBlock(node: ASTNode, source: String, styles: InlineStyles) {
    when (node.type) {
        MarkdownElementTypes.ATX_1 ->
            Heading(node, source, styles, MaterialTheme.typography.headlineSmall)
        MarkdownElementTypes.ATX_2, MarkdownElementTypes.SETEXT_1 ->
            Heading(node, source, styles, MaterialTheme.typography.titleLarge)
        MarkdownElementTypes.ATX_3, MarkdownElementTypes.SETEXT_2 ->
            Heading(node, source, styles, MaterialTheme.typography.titleMedium)
        MarkdownElementTypes.ATX_4 ->
            Heading(node, source, styles, MaterialTheme.typography.titleSmall)
        MarkdownElementTypes.ATX_5, MarkdownElementTypes.ATX_6 ->
            Heading(node, source, styles, MaterialTheme.typography.bodyLarge)
        MarkdownElementTypes.PARAGRAPH ->
            Text(
                text = annotateNodes(node.children, source, styles),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        MarkdownElementTypes.UNORDERED_LIST ->
            ListBlock(node, source, styles, ordered = false)
        MarkdownElementTypes.ORDERED_LIST ->
            ListBlock(node, source, styles, ordered = true)
        MarkdownElementTypes.CODE_FENCE ->
            CodeBlock(code = fencedCode(node, source), styles = styles)
        MarkdownElementTypes.CODE_BLOCK ->
            CodeBlock(
                code = node.children
                    .filter { it.type == MarkdownTokenTypes.CODE_LINE }
                    .joinToString("") { it.textIn(source) }
                    .trimEnd('\n'),
                styles = styles
            )
        MarkdownElementTypes.BLOCK_QUOTE ->
            BlockQuote(node, source, styles)
        GFMElementTypes.TABLE ->
            TableBlock(node, source, styles)
        MarkdownTokenTypes.HORIZONTAL_RULE ->
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        MarkdownElementTypes.HTML_BLOCK ->
            Text(
                text = node.textIn(source),
                style = MaterialTheme.typography.bodyMedium,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        else ->
            // Unknown block: render its text so nothing silently disappears.
            Text(
                text = annotateNodes(node.children, source, styles),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
    }
}

@Composable
private fun Heading(
    node: ASTNode,
    source: String,
    styles: InlineStyles,
    style: TextStyle
) {
    Text(
        text = annotateNodes(
            node.children.filter { it.type != MarkdownTokenTypes.ATX_HEADER },
            source,
            styles
        ),
        style = style,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
private fun ListBlock(
    node: ASTNode,
    source: String,
    styles: InlineStyles,
    ordered: Boolean
) {
    val items = node.children.filter { it.type == MarkdownElementTypes.LIST_ITEM }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items.forEachIndexed { index, item ->
            Row {
                Text(
                    text = if (ordered) "${index + 1}." else "•",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(24.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val checkBox = item.children.firstOrNull {
                        it.type == GFMTokenTypes.CHECK_BOX
                    }
                    if (checkBox != null) {
                        val done = checkBox.textIn(source).contains('x', ignoreCase = true)
                        Row {
                            Text(
                                text = if (done) "☑ " else "☐ ",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = annotateNodes(
                                    item.children.filter {
                                        it.type != GFMTokenTypes.CHECK_BOX &&
                                            it.type != MarkdownTokenTypes.WHITE_SPACE &&
                                            it.type != MarkdownTokenTypes.EOL
                                    },
                                    source,
                                    styles
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                textDecoration = if (done) TextDecoration.LineThrough else null,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    } else {
                        item.children.forEach { child ->
                            when (child.type) {
                                MarkdownTokenTypes.LIST_BULLET,
                                MarkdownTokenTypes.LIST_NUMBER,
                                MarkdownTokenTypes.WHITE_SPACE,
                                MarkdownTokenTypes.EOL -> Unit
                                else -> MarkdownBlock(child, source, styles)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockQuote(node: ASTNode, source: String, styles: InlineStyles) {
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(
                    MaterialTheme.colorScheme.outlineVariant,
                    RoundedCornerShape(2.dp)
                )
        )
        Spacer(Modifier.width(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            node.children
                .filter {
                    it.type != MarkdownTokenTypes.BLOCK_QUOTE &&
                        it.type != MarkdownTokenTypes.EOL
                }
                .forEach { MarkdownBlock(it, source, styles) }
        }
    }
}

@Composable
private fun TableBlock(node: ASTNode, source: String, styles: InlineStyles) {
    val headerRow = node.children.firstOrNull { it.type == GFMElementTypes.HEADER }
    val bodyRows = node.children.filter { it.type == GFMElementTypes.ROW }
    fun cells(row: ASTNode?) = row?.children
        ?.filter { it.type == GFMTokenTypes.CELL }
        ?.map { it.textIn(source).trim() }
        .orEmpty()

    val headerCells = cells(headerRow)
    val bodyCellLists = bodyRows.map(::cells)
    val columnCount = maxOf(
        headerCells.size,
        bodyCellLists.maxOfOrNull { it.size } ?: 0
    )
    if (columnCount == 0) return

    val colorScheme = MaterialTheme.colorScheme
    val cellStyle = MaterialTheme.typography.bodyMedium
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val columnWidths = remember(headerCells, bodyCellLists, density, cellStyle) {
        (0 until columnCount).map { column ->
            val texts = sequence {
                yield(headerCells.getOrNull(column))
                bodyCellLists.forEach { yield(it.getOrNull(column)) }
            }.filterNotNull()
            val widest = texts.maxOfOrNull { cell ->
                textMeasurer.measure(
                    text = AnnotatedString(cell),
                    style = cellStyle,
                    maxLines = 1
                ).size.width
            } ?: 0
            with(density) {
                (widest.toDp() + 24.dp).coerceIn(48.dp, 240.dp)
            }
        }
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, colorScheme.outlineVariant),
        color = colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            if (headerCells.isNotEmpty()) {
                Row(
                    modifier = Modifier.background(colorScheme.surfaceContainerHighest)
                ) {
                    headerCells.forEachIndexed { index, cell ->
                        TableCell(
                            text = annotateInline(cell, styles),
                            width = columnWidths[index],
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface
                        )
                    }
                }
            }
            bodyCellLists.forEach { row ->
                HorizontalDivider(
                    thickness = 1.dp,
                    color = colorScheme.outlineVariant
                )
                Row {
                    row.forEachIndexed { index, cell ->
                        TableCell(
                            text = annotateInline(cell, styles),
                            width = columnWidths[index],
                            fontWeight = null,
                            color = colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TableCell(
    text: AnnotatedString,
    width: Dp,
    fontWeight: FontWeight?,
    color: Color
) {
    Box(
        modifier = Modifier
            .width(width)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = fontWeight,
            color = color
        )
    }
}

@Composable
private fun CodeBlock(code: String, styles: InlineStyles) {
    var copied by remember { mutableStateOf(false) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copyLabel = stringResource(R.string.ai_message_copy_code)

    LaunchedEffect(copied) {
        if (copied) {
            delay(1500)
            copied = false
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    RoundedCornerShape(8.dp)
                )
                .padding(start = 12.dp, end = 36.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Text(
                text = code,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.horizontalScroll(rememberScrollState())
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 4.dp, end = 4.dp)
                .clip(CircleShape)
                .clickable {
                    scope.launch {
                        clipboard.setClipEntry(
                            ClipEntry(ClipData.newPlainText("code", code))
                        )
                    }
                    copied = true
                }
                .padding(4.dp)
        ) {
            Icon(
                imageVector = if (copied) Icons.Default.Check else Icons.Outlined.ContentCopy,
                contentDescription = copyLabel,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * Extracts the body of a fenced code block (the lines between the fences)
 * from [source]. Works for unclosed fences too (streaming).
 */
private fun fencedCode(node: ASTNode, source: String): String {
    val lines = node.textIn(source).lines()
    if (lines.isEmpty()) return ""
    val body = if (lines.size >= 2 && lines.last().trimStart().startsWith("```")) {
        lines.subList(1, lines.size - 1)
    } else {
        lines.subList(1, lines.size)
    }
    return body.joinToString("\n").trimEnd('\n')
}

private fun ASTNode.textIn(source: String): String =
    source.substring(startOffset, endOffset.coerceAtMost(source.length))

/**
 * Renders a list of AST nodes into an [AnnotatedString] with inline styling.
 */
private fun annotateNodes(
    nodes: List<ASTNode>,
    source: String,
    styles: InlineStyles
): AnnotatedString = buildAnnotatedString {
    nodes.forEach { appendNode(it, source, this, styles) }
}

/**
 * Renders a single AST node (with its children) into an [AnnotatedString].
 */
private fun annotateNode(node: ASTNode, source: String, styles: InlineStyles): AnnotatedString =
    buildAnnotatedString { appendNode(node, source, this, styles) }

private fun appendNode(
    node: ASTNode,
    source: String,
    builder: AnnotatedString.Builder,
    styles: InlineStyles
) {
    when (node.type) {
        MarkdownTokenTypes.TEXT,
        MarkdownTokenTypes.WHITE_SPACE,
        MarkdownTokenTypes.ESCAPED_BACKTICKS ->
            builder.append(node.textIn(source))
        MarkdownTokenTypes.EOL, MarkdownTokenTypes.HARD_LINE_BREAK ->
            builder.append('\n')
        MarkdownTokenTypes.ATX_CONTENT -> {
            // Strip closing hashes from "closed" ATX headings ("## Title ##").
            builder.append(node.textIn(source).trim().trimEnd('#').trim())
        }
        MarkdownElementTypes.STRONG -> builder.withStyle(
            SpanStyle(fontWeight = FontWeight.Bold)
        ) {
            node.children.forEach { appendNode(it, source, builder, styles) }
        }
        MarkdownElementTypes.EMPH -> builder.withStyle(
            SpanStyle(fontStyle = FontStyle.Italic)
        ) {
            node.children.forEach { appendNode(it, source, builder, styles) }
        }
        GFMElementTypes.STRIKETHROUGH -> builder.withStyle(
            SpanStyle(textDecoration = TextDecoration.LineThrough)
        ) {
            node.children.forEach { appendNode(it, source, builder, styles) }
        }
        MarkdownElementTypes.CODE_SPAN -> builder.withStyle(styles.code) {
            builder.append(
                node.children
                    .filter { it.type != MarkdownTokenTypes.BACKTICK }
                    .joinToString("") { it.textIn(source) }
            )
        }
        MarkdownElementTypes.INLINE_LINK,
        MarkdownElementTypes.IMAGE,
        GFMTokenTypes.GFM_AUTOLINK,
        MarkdownElementTypes.AUTOLINK ->
            appendLink(node, source, builder, styles)
        else -> {
            if (node.children.isNotEmpty()) {
                node.children.forEach { appendNode(it, source, builder, styles) }
            } else {
                builder.append(node.textIn(source))
            }
        }
    }
}

/**
 * Renders a link as a clickable [LinkAnnotation]; falls back to plain label
 * text when no destination can be resolved (e.g. unresolved reference links).
 */
private fun appendLink(
    node: ASTNode,
    source: String,
    builder: AnnotatedString.Builder,
    styles: InlineStyles
) {
    val destination = when (node.type) {
        MarkdownElementTypes.INLINE_LINK, MarkdownElementTypes.IMAGE ->
            node.children
                .firstOrNull { it.type == MarkdownElementTypes.LINK_DESTINATION }
                ?.textIn(source)
        GFMTokenTypes.GFM_AUTOLINK, MarkdownElementTypes.AUTOLINK ->
            node.textIn(source).trim('<', '>')
        else -> null
    }
    val labelText = node.children
        .firstOrNull { it.type == MarkdownElementTypes.LINK_TEXT }
        ?.let { linkText ->
            linkText.children
                .filter {
                    it.type != MarkdownTokenTypes.LBRACKET &&
                        it.type != MarkdownTokenTypes.RBRACKET
                }
                .joinToString("") { it.textIn(source) }
        }
        ?: node.textIn(source).trim('<', '>')

    val url = destination?.takeIf {
        it.startsWith("http://") || it.startsWith("https://") ||
            it.startsWith("mailto:")
    }
    if (url == null) {
        builder.append(labelText)
    } else {
        builder.withLink(LinkAnnotation.Url(url, TextLinkStyles(styles.link))) {
            append(labelText)
        }
    }
}

/**
 * Applies lightweight inline markdown styling (bold, italic, code) to a plain
 * string. Used for GFM table cells, which the parser treats as raw text.
 */
internal fun annotateInline(text: String, styles: InlineStyles): AnnotatedString =
    buildAnnotatedString {
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
                    withStyle(styles.code) { append(text.substring(next + 1, end)) }
                    index = end + 1
                }
                bold -> {
                    val end = text.indexOf("**", next + 2)
                    if (end == -1) {
                        append(text.substring(next))
                        break
                    }
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(next + 2, end))
                    }
                    index = end + 2
                }
                else -> {
                    val end = text.indexOf('*', next + 1)
                    if (end == -1) {
                        append(text.substring(next))
                        break
                    }
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                        append(text.substring(next + 1, end))
                    }
                    index = end + 1
                }
            }
        }
    }
