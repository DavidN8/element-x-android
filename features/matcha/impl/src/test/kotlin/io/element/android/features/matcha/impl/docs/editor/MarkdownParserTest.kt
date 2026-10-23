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
    fun `a closing run of hashes is dropped only after a space`() {
        assertThat(MarkdownParser.parse("## Plans ##\n# C#\n# ###\n#\n#hashtag")).containsExactly(
            MarkdownBlock.Heading(2, "Plans"),
            MarkdownBlock.Heading(1, "C#"),
            MarkdownBlock.Heading(1, ""),
            MarkdownBlock.Heading(1, ""),
            MarkdownBlock.Paragraph("#hashtag"),
        ).inOrder()
    }

    @Test
    fun `rules and table delimiters`() {
        assertThat(MarkdownParser.parse("* * *\n___\n   ---\n    ---\n-- -x")).containsExactly(
            MarkdownBlock.Rule,
            MarkdownBlock.Rule,
            MarkdownBlock.Rule,
            MarkdownBlock.Paragraph("--- -- -x"),
        ).inOrder()
        assertThat(MarkdownParser.parse("| A | B |\n|:--|--:|\n| 1 | 2 |").single()).isInstanceOf(MarkdownBlock.Table::class.java)
        assertThat(MarkdownParser.parse("| A |\n| - - |").single()).isInstanceOf(MarkdownBlock.Paragraph::class.java)
    }

    @Test
    fun `hostile text is read in linear time`() {
        val long = 10_000
        val lines = listOf(
            "# a" + " ".repeat(long) + "b",
            "- ".repeat(long) + "x",
            "|---".repeat(long) + "x",
            "[".repeat(long),
            "<".repeat(long) + ">",
            "`".repeat(long),
            "[a](".repeat(long),
            "*a ".repeat(long),
        )
        val started = System.nanoTime()
        lines.forEach { line ->
            MarkdownParser.parse(line).forEach { block -> if (block is MarkdownBlock.Paragraph) MarkdownParser.inline(block.text, InlineStyles.plain()) }
        }
        assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(2_000)
        assertThat(MarkdownParser.parse(lines[0]).single()).isEqualTo(MarkdownBlock.Heading(1, "a" + " ".repeat(long) + "b"))
        assertThat(MarkdownParser.inline("`".repeat(long), InlineStyles.plain()).text).hasLength(long)
    }

    @Test
    fun `snake_case words stay plain`() {
        val text = MarkdownParser.inline("use snake_case_name here", InlineStyles.plain())
        assertThat(text.text).isEqualTo("use snake_case_name here")
        assertThat(text.spanStyles).isEmpty()
    }
}
