/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs.editor

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle

/** A block of a Markdown document. */
@Immutable
sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock

    data class Paragraph(val text: String) : MarkdownBlock

    data class ListItem(
        val depth: Int,
        /** The visible marker: a bullet, a number such as "2.", or a checkbox. */
        val marker: String,
        val text: String,
    ) : MarkdownBlock

    data class Quote(val text: String) : MarkdownBlock

    data class Code(val text: String) : MarkdownBlock

    data object Rule : MarkdownBlock

    /** The first row is the header. */
    data class Table(val rows: List<List<String>>) : MarkdownBlock
}

/**
 * A small Markdown reader for the doc preview: headings, paragraphs, bulleted, numbered and checklist items, quotes, fenced code,
 * rules and tables, with bold, italic, strikethrough, code, links and bare links inside them. Raw HTML stays text, as on the web.
 *
 * Any member writes the text, so reading it stays linear: no pattern backtracks or recurses per character, and inline searches
 * that only move forward reuse their last answer.
 *
 * ponytail: no nested quotes, setext headings or reference links; a full CommonMark library if docs need them.
 */
object MarkdownParser {
    private val heading = Regex("^ {0,3}(#{1,6})(?:[ \\t]+(.*))?$")
    private val task = Regex("^(\\s*)[-*+]\\s+\\[([ xX])]\\s+(.*)$")
    private val bullet = Regex("^(\\s*)[-*+]\\s+(.*)$")
    private val ordered = Regex("^(\\s*)(\\d{1,9})[.)]\\s+(.*)$")
    private val quote = Regex("^ {0,3}>\\s?(.*)$")
    private val fence = Regex("^ {0,3}(```|~~~)")
    private const val INDENT_PER_LEVEL = 2
    private val safeSchemes = listOf("https://", "http://", "mailto:")
    private const val ESCAPABLE = "\\`*_{}[]()#+-.!|~<>"

    fun parse(markdown: String): List<MarkdownBlock> {
        val lines = markdown.replace("\r\n", "\n").split('\n')
        val blocks = mutableListOf<MarkdownBlock>()
        val paragraph = mutableListOf<String>()
        fun flush() {
            if (paragraph.isNotEmpty()) blocks += MarkdownBlock.Paragraph(paragraph.joinToString(" ") { it.trim() })
            paragraph.clear()
        }
        var index = 0
        while (index < lines.size) {
            val line = lines[index]
            val fenceMatch = fence.find(line)
            val consumed = when {
                fenceMatch != null -> {
                    flush()
                    val closing = (index + 1 until lines.size).firstOrNull { lines[it].trimStart().startsWith(fenceMatch.groupValues[1]) } ?: lines.size
                    blocks += MarkdownBlock.Code(lines.subList(index + 1, closing).joinToString("\n"))
                    closing + 1 - index
                }
                line.isBlank() -> {
                    flush()
                    1
                }
                line.trimStart().startsWith("|") && lines.getOrNull(index + 1)?.let(::isTableDelimiter) == true -> {
                    flush()
                    val end = (index + 2 until lines.size).firstOrNull { !lines[it].trimStart().startsWith("|") } ?: lines.size
                    blocks += MarkdownBlock.Table((listOf(line) + lines.subList(index + 2, end)).map(::cells))
                    end - index
                }
                quote.matches(line) -> {
                    flush()
                    val end = (index until lines.size).firstOrNull { !quote.matches(lines[it]) } ?: lines.size
                    blocks += MarkdownBlock.Quote(lines.subList(index, end).joinToString("\n") { quote.find(it)!!.groupValues[1] })
                    end - index
                }
                else -> {
                    val block = lineBlock(line)
                    if (block == null) {
                        paragraph += line
                    } else {
                        flush()
                        blocks += block
                    }
                    1
                }
            }
            index += consumed
        }
        flush()
        return blocks
    }

    private fun lineBlock(line: String): MarkdownBlock? {
        heading.find(line)?.let { return MarkdownBlock.Heading(it.groupValues[1].length, headingText(it.groupValues[2])) }
        if (isRule(line)) return MarkdownBlock.Rule
        task.find(line)?.let {
            val marker = if (it.groupValues[2].isBlank()) "☐" else "☑"
            return MarkdownBlock.ListItem(depth(it.groupValues[1]), marker, it.groupValues[3])
        }
        bullet.find(line)?.let { return MarkdownBlock.ListItem(depth(it.groupValues[1]), "•", it.groupValues[2]) }
        ordered.find(line)?.let { return MarkdownBlock.ListItem(depth(it.groupValues[1]), "${it.groupValues[2]}.", it.groupValues[3]) }
        return null
    }

    /** A heading's text without its optional closing run of `#`, which only counts after a space. */
    private fun headingText(text: String): String {
        val trimmed = text.trimEnd()
        val open = trimmed.trimEnd('#')
        return if (open.isEmpty() || open.last().isWhitespace()) open.trimEnd() else trimmed
    }

    /** Three or more of the same `-`, `*` or `_`, with spaces between. */
    private fun isRule(line: String): Boolean {
        val indent = line.takeWhile { it == ' ' }.length
        val mark = line.getOrNull(indent)?.takeIf { indent <= 3 && it in "-*_" } ?: return false
        val marks = line.filterNot(Char::isWhitespace)
        return marks.length >= 3 && marks.all { it == mark }
    }

    /** A table's delimiter row, such as `| --- | :-: |`. */
    private fun isTableDelimiter(line: String): Boolean =
        line.trim().removePrefix("|").removeSuffix("|").split('|').all { cell ->
            val dashes = cell.trim().removePrefix(":").removeSuffix(":")
            dashes.isNotEmpty() && dashes.all { it == '-' }
        }

    private fun depth(indent: String) = indent.replace("\t", "    ").length / INDENT_PER_LEVEL

    private fun cells(line: String) = line.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }

    /** Inline Markdown as styled text, with links that open in the browser. */
    fun inline(text: String, styles: InlineStyles): AnnotatedString = buildAnnotatedString { appendInline(text, styles) }

    private fun AnnotatedString.Builder.appendInline(text: String, styles: InlineStyles) {
        val search = ForwardSearch(text)
        var index = 0
        while (index < text.length) {
            index = appendToken(text, search, index, styles)
        }
    }

    /**
     * [String.indexOf] for searches whose start only moves forward: each needle's last answer still holds until the start passes
     * it, so text full of unmatched markers is read in linear time.
     */
    private class ForwardSearch(private val text: String) {
        private val last = HashMap<String, Pair<Int, Int>>()

        fun indexOf(needle: String, start: Int): Int {
            last[needle]?.let { (from, found) -> if (start >= from && (found < 0 || start <= found)) return found }
            return text.indexOf(needle, start).also { last[needle] = start to it }
        }
    }

    /** Appends the token starting at [start] and returns where the next one starts. */
    @Suppress("CyclomaticComplexMethod", "ReturnCount")
    private fun AnnotatedString.Builder.appendToken(text: String, search: ForwardSearch, start: Int, styles: InlineStyles): Int {
        val char = text[start]
        if (char == '\\' && start + 1 < text.length && text[start + 1] in ESCAPABLE) {
            append(text[start + 1])
            return start + 2
        }
        if (char == '`') {
            var runEnd = start
            while (runEnd < text.length && text[runEnd] == '`') runEnd++
            val run = text.substring(start, runEnd)
            val end = search.indexOf(run, runEnd)
            if (end > runEnd) {
                withStyle(styles.code) { append(text.substring(runEnd, end).trim()) }
                return end + run.length
            }
            // A run that never closes is plain text, all of it.
            append(run)
            return runEnd
        }
        for ((marker, style) in listOf(
                "**" to SpanStyle(fontWeight = FontWeight.Bold),
                "__" to SpanStyle(fontWeight = FontWeight.Bold),
                "~~" to styles.strike
            )) {
            if (text.startsWith(marker, start)) {
                val end = search.indexOf(marker, start + marker.length)
                if (end > start + marker.length) {
                    withStyle(style) { appendInline(text.substring(start + marker.length, end), styles) }
                    return end + marker.length
                }
            }
        }
        if (opensEmphasis(text, start)) {
            val end = search.indexOf(char.toString(), start + 1)
            if (end > start + 1 && !text[end - 1].isWhitespace()) {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { appendInline(text.substring(start + 1, end), styles) }
                return end + 1
            }
        }
        if (char == '[' || text.startsWith("![", start)) {
            val labelStart = if (char == '!') start + 2 else start + 1
            val labelEnd = search.indexOf("](", labelStart)
            val urlEnd = if (labelEnd < 0) -1 else search.indexOf(")", labelEnd + 2)
            if (labelEnd >= 0 && urlEnd > labelEnd) {
                val url = text.substring(labelEnd + 2, urlEnd).trim().substringBefore(' ')
                // Images never load: their description stands in, as a link to the image.
                appendLink(text.substring(labelStart, labelEnd), url, styles)
                return urlEnd + 1
            }
        }
        if (char == '<') {
            // An autolink holds no space and no '<', so the scan stops at the next one.
            val end = (start + 1 until text.length).firstOrNull { text[it] == '>' || text[it] == '<' || text[it].isWhitespace() }
            val url = end?.takeIf { text[it] == '>' }?.let { text.substring(start + 1, it) }
            if (end != null && url != null && isSafe(url)) {
                appendLink(url, url, styles)
                return end + 1
            }
        }
        if (isBareLink(text, start)) {
            val end = (start until text.length).firstOrNull { text[it].isWhitespace() || text[it] == '<' } ?: text.length
            val url = text.substring(start, end).trimEnd('.', ',', ';', ':', '!', '?', ')')
            appendLink(url, url, styles)
            return start + url.length
        }
        append(char)
        return start + 1
    }

    /** A link; a bare address is its own label and is not read again as Markdown. */
    private fun AnnotatedString.Builder.appendLink(label: String, url: String, styles: InlineStyles) {
        val isBare = label.isEmpty() || label == url
        when {
            isSafe(url) -> withLink(LinkAnnotation.Url(url, styles.link)) { if (isBare) append(url) else appendInline(label, styles) }
            isBare -> append(url)
            else -> appendInline(label, styles)
        }
    }

    /** `*` opens emphasis before a non-space; `_` only at the start of a word, so snake_case stays plain. */
    private fun opensEmphasis(text: String, start: Int): Boolean {
        val char = text[start]
        val isMarker = char == '*' || char == '_' && isWordStart(text, start)
        return isMarker && start + 1 < text.length && !text[start + 1].isWhitespace()
    }

    private fun isBareLink(text: String, start: Int) =
        isWordStart(text, start) && (text.startsWith("https://", start, ignoreCase = true) || text.startsWith("http://", start, ignoreCase = true))

    private fun isSafe(url: String) = safeSchemes.any { url.startsWith(it, ignoreCase = true) } && url.none { it.isWhitespace() }

    private fun isWordStart(text: String, index: Int) = index == 0 || !text[index - 1].isLetterOrDigit()
}

/** How inline Markdown looks, from the theme. */
data class InlineStyles(
    val link: TextLinkStyles,
    val code: SpanStyle,
    val strike: SpanStyle = SpanStyle(textDecoration = TextDecoration.LineThrough),
) {
    companion object {
        fun plain() = InlineStyles(
            link = TextLinkStyles(SpanStyle(textDecoration = TextDecoration.Underline)),
            code = SpanStyle(fontFamily = FontFamily.Monospace),
        )
    }
}
