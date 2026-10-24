/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue

/** The buttons of the formatting row, in their order. */
enum class FormatCommand {
    Heading,
    Bold,
    Italic,
    Strikethrough,
    BulletList,
    NumberedList,
    Quote,
    InlineCode,
    CodeBlock,
    Link,
    Checklist,
}

/**
 * Markdown formatting of the selection, after the web client's `markdownCommands.ts`: inline styles wrap or unwrap the selection,
 * block styles toggle a prefix on every selected line. Only the touched text changes, so concurrent edits elsewhere survive.
 */
object MarkdownFormatting {
    private val anyHeading = Regex("^#{1,6}\\s+")
    private val quote = Regex("^>\\s?")
    private val anyListItem = Regex("^(?:[-*+]\\s+(?:\\[[ xX]]\\s+)?|\\d+[.)]\\s+)")
    private val checklistItem = Regex("^[-*+]\\s+\\[[ xX]]\\s+")
    private val bulletItem = Regex("^[-*+]\\s+(?!\\[[ xX]])")
    private val numberedItem = Regex("^\\d+[.)]\\s+")
    private const val LINK_URL = "https://"
    private const val FENCE = "```"

    fun apply(command: FormatCommand, value: TextFieldValue): TextFieldValue = when (command) {
        FormatCommand.Bold -> wrap(value, "**")
        FormatCommand.Italic -> wrap(value, "*")
        FormatCommand.Strikethrough -> wrap(value, "~~")
        FormatCommand.InlineCode -> wrap(value, "`")
        FormatCommand.CodeBlock -> codeBlock(value)
        FormatCommand.Link -> link(value)
        FormatCommand.Heading,
        FormatCommand.BulletList,
        FormatCommand.NumberedList,
        FormatCommand.Checklist,
        FormatCommand.Quote -> prefixLines(value, command)
    }

    /** Whether the selection already has this style, to tint its button. */
    fun isActive(command: FormatCommand, value: TextFieldValue): Boolean = when (command) {
        FormatCommand.Bold -> isWrapped(value, "**")
        FormatCommand.Italic -> isWrapped(value, "*")
        FormatCommand.Strikethrough -> isWrapped(value, "~~")
        FormatCommand.InlineCode -> isWrapped(value, "`")
        FormatCommand.CodeBlock, FormatCommand.Link -> false
        else -> currentLine(value).trimStart().let { line -> sameItem(command).containsMatchIn(line) }
    }

    private fun isWrapped(value: TextFieldValue, marker: String): Boolean {
        val text = value.text
        val start = value.selection.min
        val end = value.selection.max
        return start >= marker.length && text.startsWith(marker, start - marker.length) && text.startsWith(marker, end) &&
            (marker != "*" || isItalicRun(text, start, end))
    }

    /** A single asterisk is italic only when it is not part of a bold pair: the runs around the selection must be odd. */
    private fun isItalicRun(text: String, start: Int, end: Int): Boolean {
        val before = text.substring(0, start).takeLastWhile { it == '*' }.length
        val after = text.substring(end).takeWhile { it == '*' }.length
        return before % 2 == 1 && after % 2 == 1
    }

    private fun wrap(value: TextFieldValue, marker: String): TextFieldValue {
        val text = value.text
        val start = value.selection.min
        val end = value.selection.max
        if (isWrapped(value, marker)) {
            val unwrapped = text.removeRange(end, end + marker.length).removeRange(start - marker.length, start)
            return TextFieldValue(unwrapped, TextRange(start - marker.length, end - marker.length))
        }
        val selected = text.substring(start, end)
        val includesMarkers = selected.length >= 2 * marker.length && selected.startsWith(marker) && selected.endsWith(marker) &&
            (marker != "*" || isItalicRun(selected, marker.length, selected.length - marker.length))
        if (includesMarkers) {
            val inner = selected.substring(marker.length, selected.length - marker.length)
            return TextFieldValue(text.replaceRange(start, end, inner), TextRange(start, start + inner.length))
        }
        return TextFieldValue(text.replaceRange(start, end, marker + selected + marker), TextRange(start + marker.length, end + marker.length))
    }

    private fun codeBlock(value: TextFieldValue): TextFieldValue {
        val text = value.text
        val start = value.selection.min
        val end = value.selection.max
        val lineBreakBefore = if (start > 0 && text[start - 1] != '\n') "\n" else ""
        val lineBreakAfter = if (end < text.length && text[end] != '\n') "\n" else ""
        val before = lineBreakBefore + FENCE + "\n"
        val after = "\n" + FENCE + lineBreakAfter
        val selected = text.substring(start, end)
        return TextFieldValue(text.replaceRange(start, end, before + selected + after), TextRange(start + before.length, end + before.length))
    }

    private fun link(value: TextFieldValue): TextFieldValue {
        val text = value.text
        val start = value.selection.min
        val end = value.selection.max
        val selected = text.substring(start, end)
        val inserted = "[$selected]($LINK_URL)"
        val newText = text.replaceRange(start, end, inserted)
        // With a label, select the address to type over it; without one, type the label first.
        return if (selected.isEmpty()) {
            TextFieldValue(newText, TextRange(start + 1))
        } else {
            val urlStart = start + selected.length + 3
            TextFieldValue(newText, TextRange(urlStart, urlStart + LINK_URL.length))
        }
    }

    private fun sameItem(command: FormatCommand) = when (command) {
        FormatCommand.Heading -> anyHeading
        FormatCommand.Quote -> quote
        FormatCommand.Checklist -> checklistItem
        FormatCommand.NumberedList -> numberedItem
        else -> bulletItem
    }

    private fun currentLine(value: TextFieldValue): String {
        val text = value.text
        val start = text.lastIndexOf('\n', (value.selection.min - 1).coerceAtLeast(0)).let { if (value.selection.min == 0 || it < 0) 0 else it + 1 }
        val end = text.indexOf('\n', value.selection.min).let { if (it < 0) text.length else it }
        return text.substring(start, end)
    }

    private fun prefixLines(value: TextFieldValue, command: FormatCommand): TextFieldValue {
        val text = value.text
        val lineStarts = buildList {
            add(0)
            text.forEachIndexed { index, char -> if (char == '\n') add(index + 1) }
        }
        val selectionEnd = if (value.selection.collapsed) value.selection.max else maxOf(value.selection.min, value.selection.max - 1)
        val first = lineStarts.indexOfLast { it <= value.selection.min }
        val last = lineStarts.indexOfLast { it <= selectionEnd }
        val lines = text.split('\n')
        val selected = lines.subList(first, last + 1)
        val same = sameItem(command)
        val existing = when (command) {
            FormatCommand.Heading -> anyHeading
            FormatCommand.Quote -> quote
            else -> anyListItem
        }
        val nonEmpty = selected.filter { it.isNotBlank() }
        val remove = nonEmpty.isNotEmpty() && nonEmpty.all { same.containsMatchIn(it.trimStart()) }
        var number = 1
        val changed = selected.map { line ->
            if (line.isBlank() && selected.size > 1) return@map line
            val indent = line.takeWhile { it.isWhitespace() }
            val rest = line.substring(indent.length)
            val oldPrefix = existing.find(rest)?.takeIf { it.range.first == 0 }?.value.orEmpty()
            val marker = when {
                remove -> ""
                command == FormatCommand.Heading -> "# "
                command == FormatCommand.Quote -> "> "
                command == FormatCommand.Checklist -> "- [ ] "
                command == FormatCommand.NumberedList -> "${number++}. "
                else -> "- "
            }
            indent + marker + rest.substring(oldPrefix.length)
        }
        val newText = (lines.subList(0, first) + changed + lines.subList(last + 1, lines.size)).joinToString("\n")
        val firstStart = lineStarts[first]
        val delta = newText.length - text.length
        return if (value.selection.collapsed && first == last) {
            // Keep the cursor on the same character of the line, after the new prefix.
            val lineDelta = changed.single().length - selected.single().length
            TextFieldValue(newText, TextRange((value.selection.start + lineDelta).coerceIn(firstStart, firstStart + changed.single().length)))
        } else {
            val changedEnd = firstStart + changed.joinToString("\n").length
            TextFieldValue(newText, TextRange(firstStart, changedEnd.coerceAtMost(text.length + delta)))
        }
    }
}
