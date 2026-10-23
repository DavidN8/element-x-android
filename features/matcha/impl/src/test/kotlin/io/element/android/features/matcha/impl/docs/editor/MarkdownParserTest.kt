/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs.editor

import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MarkdownParserTest {
    @Test
    fun `blocks are read line by line`() {
        val blocks = MarkdownParser.parse(
            """
            # Title
            Some text
            on two lines.

            - item
              - nested
            - [ ] todo
            - [x] done
            3. third
            > quoted
            > more
            ---
            ```
            code *not bold*
            ```
            | A | B |
            | --- | --- |
            | 1 | 2 |
            """.trimIndent()
        )
        assertThat(blocks).containsExactly(
            MarkdownBlock.Heading(1, "Title"),
            MarkdownBlock.Paragraph("Some text on two lines."),
            MarkdownBlock.ListItem(0, "•", "item"),
            MarkdownBlock.ListItem(1, "•", "nested"),
            MarkdownBlock.ListItem(0, "☐", "todo"),
            MarkdownBlock.ListItem(0, "☑", "done"),
            MarkdownBlock.ListItem(0, "3.", "third"),
            MarkdownBlock.Quote("quoted\nmore"),
            MarkdownBlock.Rule,
            MarkdownBlock.Code("code *not bold*"),
            MarkdownBlock.Table(listOf(listOf("A", "B"), listOf("1", "2"))),
        ).inOrder()
    }

    @Test
    fun `an unclosed fence runs to the end and an empty doc has no blocks`() {
        assertThat(MarkdownParser.parse("```\nlet x")).containsExactly(MarkdownBlock.Code("let x"))
        assertThat(MarkdownParser.parse("")).isEmpty()
        assertThat(MarkdownParser.parse("\n\n")).isEmpty()
    }

    @Test
    fun `inline styles and links`() {
        val text = MarkdownParser.inline(
            "a **b** *c* ~~d~~ `e` [f](https://x.org) <https://y.org> https://z.org. [g](javascript:alert) \\*h",
            InlineStyles.plain()
        )
        assertThat(text.text).isEqualTo("a b c d e f https://y.org https://z.org. g *h")
        val bold = text.spanStyles.first { it.item.fontWeight == FontWeight.Bold }
        assertThat(text.text.substring(bold.start, bold.end)).isEqualTo("b")
        val italic = text.spanStyles.first { it.item.fontStyle == FontStyle.Italic }
        assertThat(text.text.substring(italic.start, italic.end)).isEqualTo("c")
        val links = text.getLinkAnnotations(0, text.length).map { (it.item as LinkAnnotation.Url).url }
        // A script link is shown as plain text.
        assertThat(links).containsExactly("https://x.org", "https://y.org", "https://z.org").inOrder()
    }

    @Test
    fun `snake_case words stay plain`() {
        val text = MarkdownParser.inline("use snake_case_name here", InlineStyles.plain())
        assertThat(text.text).isEqualTo("use snake_case_name here")
        assertThat(text.spanStyles).isEmpty()
    }
}
