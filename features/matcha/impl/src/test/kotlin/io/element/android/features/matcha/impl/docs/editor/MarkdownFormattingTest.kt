/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MarkdownFormattingTest {
    private fun value(text: String, start: Int, end: Int = start) = TextFieldValue(text, TextRange(start, end))

    private fun apply(command: FormatCommand, value: TextFieldValue) = MarkdownFormatting.apply(command, value).let { it.text to it.selection }

    @Test
    fun `inline styles wrap the selection and toggle off again`() {
        val bold = MarkdownFormatting.apply(FormatCommand.Bold, value("say hi now", 4, 6))
        assertThat(bold.text).isEqualTo("say **hi** now")
        assertThat(bold.selection).isEqualTo(TextRange(6, 8))
        assertThat(MarkdownFormatting.isActive(FormatCommand.Bold, bold)).isTrue()
        assertThat(MarkdownFormatting.isActive(FormatCommand.Italic, bold)).isFalse()
        assertThat(apply(FormatCommand.Bold, bold)).isEqualTo("say hi now" to TextRange(4, 6))
        assertThat(apply(FormatCommand.Bold, value("say **hi** now", 4, 10))).isEqualTo("say hi now" to TextRange(4, 6))
        assertThat(apply(FormatCommand.Italic, value("a", 1))).isEqualTo("a**" to TextRange(2))
        assertThat(apply(FormatCommand.Strikethrough, value("old", 0, 3)).first).isEqualTo("~~old~~")
        assertThat(apply(FormatCommand.InlineCode, value("run x", 4, 5)).first).isEqualTo("run `x`")
    }

    @Test
    fun `italic never eats half of a bold pair`() {
        val inBold = value("**hi**", 2, 4)
        assertThat(MarkdownFormatting.isActive(FormatCommand.Italic, inBold)).isFalse()
        assertThat(apply(FormatCommand.Italic, inBold).first).isEqualTo("***hi***")
        assertThat(apply(FormatCommand.Italic, value("***hi***", 3, 5)).first).isEqualTo("**hi**")
    }

    @Test
    fun `line styles toggle a prefix on every selected line`() {
        assertThat(apply(FormatCommand.BulletList, value("one\ntwo", 0, 7)).first).isEqualTo("- one\n- two")
        assertThat(apply(FormatCommand.BulletList, value("- one\n- two", 0, 11)).first).isEqualTo("one\ntwo")
        assertThat(apply(FormatCommand.NumberedList, value("- one\n- two", 0, 11)).first).isEqualTo("1. one\n2. two")
        assertThat(apply(FormatCommand.Checklist, value("1. one", 3)).first).isEqualTo("- [ ] one")
        assertThat(apply(FormatCommand.Quote, value("a\n\nb", 0, 4)).first).isEqualTo("> a\n\n> b")
        assertThat(apply(FormatCommand.Heading, value("Title", 2))).isEqualTo("# Title" to TextRange(4))
        assertThat(apply(FormatCommand.Heading, value("## Title", 5))).isEqualTo("Title" to TextRange(2))
        assertThat(MarkdownFormatting.isActive(FormatCommand.Checklist, value("text\n- [x] done", 12))).isTrue()
        assertThat(MarkdownFormatting.isActive(FormatCommand.BulletList, value("text\n- [x] done", 12))).isFalse()
        // Only the selected line changes.
        assertThat(apply(FormatCommand.Quote, value("one\ntwo\nthree", 5)).first).isEqualTo("one\n> two\nthree")
    }

    @Test
    fun `links and code blocks`() {
        assertThat(apply(FormatCommand.Link, value("see docs", 4, 8))).isEqualTo("see [docs](https://)" to TextRange(11, 19))
        assertThat(apply(FormatCommand.Link, value("", 0))).isEqualTo("[](https://)" to TextRange(1))
        assertThat(apply(FormatCommand.CodeBlock, value("a b", 2, 3)).first).isEqualTo("a \n```\nb\n```")
        assertThat(apply(FormatCommand.CodeBlock, value("x", 0, 1)).first).isEqualTo("```\nx\n```")
    }
}
