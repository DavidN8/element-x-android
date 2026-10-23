/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Text

/** A doc's Markdown, rendered with the theme: 16/24 body text, 16 dp gutters, links in the link colour, code on a grey fill. */
@Composable
fun MarkdownPreview(
    markdown: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(16.dp),
) {
    val blocks = remember(markdown) { MarkdownParser.parse(markdown) }
    val colors = ElementTheme.colors
    val styles = remember(colors) {
        InlineStyles(
            link = TextLinkStyles(SpanStyle(color = colors.textLinkExternal, textDecoration = TextDecoration.Underline)),
            code = SpanStyle(fontFamily = FontFamily.Monospace, background = colors.bgSubtleSecondary),
        )
    }
    val body = ElementTheme.typography.fontBodyLgRegular.copy(lineHeight = 24.sp, color = colors.textPrimary)
    SelectionContainer(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(blocks) { _, block -> MarkdownBlockView(block = block, body = body, styles = styles) }
        }
    }
}

@Composable
private fun MarkdownBlockView(block: MarkdownBlock, body: TextStyle, styles: InlineStyles) {
    when (block) {
        is MarkdownBlock.Heading -> Text(
            text = MarkdownParser.inline(block.text, styles),
            style = when (block.level) {
                1 -> ElementTheme.typography.fontHeadingMdBold
                2 -> ElementTheme.typography.fontHeadingSmMedium
                else -> ElementTheme.typography.fontBodyLgMedium
            },
            color = ElementTheme.colors.textPrimary,
            modifier = Modifier
                .padding(top = 8.dp)
                .semantics { heading() },
        )
        is MarkdownBlock.Paragraph -> Text(text = MarkdownParser.inline(block.text, styles), style = body)
        is MarkdownBlock.ListItem -> Row(modifier = Modifier.padding(start = (block.depth * 24).dp)) {
            Text(text = block.marker, style = body, modifier = Modifier.widthIn(min = 24.dp))
            Text(text = MarkdownParser.inline(block.text, styles), style = body)
        }
        is MarkdownBlock.Quote -> Row(modifier = Modifier.height(IntrinsicSize.Min)) {
            Spacer(
                Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(ElementTheme.colors.borderInteractiveSecondary, RoundedCornerShape(2.dp))
            )
            Text(
                text = MarkdownParser.inline(block.text, styles),
                style = body.copy(color = ElementTheme.colors.textSecondary),
                modifier = Modifier.padding(start = 12.dp),
            )
        }
        is MarkdownBlock.Code -> Text(
            text = block.text,
            style = ElementTheme.typography.fontBodyMdRegular.copy(fontFamily = FontFamily.Monospace, color = ElementTheme.colors.textPrimary),
            softWrap = false,
            modifier = Modifier
                .fillMaxWidth()
                .background(ElementTheme.colors.bgSubtleSecondary, RoundedCornerShape(8.dp))
                .horizontalScroll(rememberScrollState())
                .padding(12.dp),
        )
        MarkdownBlock.Rule -> HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        is MarkdownBlock.Table -> MarkdownTable(block, body, styles)
    }
}

@Composable
private fun MarkdownTable(block: MarkdownBlock.Table, body: TextStyle, styles: InlineStyles) {
    val border = ElementTheme.colors.separatorPrimary
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, border, RoundedCornerShape(8.dp))
    ) {
        val columns = block.rows.maxOf { it.size }
        block.rows.forEachIndexed { index, row ->
            if (index > 0) HorizontalDivider(color = border)
            Row {
                for (column in 0 until columns) {
                    Text(
                        text = MarkdownParser.inline(row.getOrElse(column) { "" }, styles),
                        style = if (index == 0) body.copy(fontWeight = FontWeight.Medium) else body,
                        modifier = Modifier
                            .weight(1f)
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}

class MarkdownPreviewParam : PreviewParameterProvider<String> {
    override val values = sequenceOf(
        """
        # Trip plans
        We leave on **Friday** at 9 and meet at the *station*. See https://example.org for the ~~old~~ new times.

        ## Packing
        - Tickets
        - [ ] Snacks
        - [x] Charger
        1. Book the hotel
        2. Tell `Ana`

        > Bring a coat, it gets cold at night.

        ```
        Platform 4, coach B
        ```

        | Day | Plan |
        | --- | --- |
        | Fri | Travel |
        | Sat | Hike |
        """.trimIndent(),
    )
}

@PreviewsDayNight
@Composable
internal fun MarkdownPreviewPreview(@PreviewParameter(MarkdownPreviewParam::class) markdown: String) = ElementPreview {
    MarkdownPreview(markdown = markdown)
}
