/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.chatWallpaper
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.noticePillBackground
import io.element.android.libraries.designsystem.theme.noticePillText
import io.element.android.libraries.designsystem.theme.systemPillBackground

internal val SystemPillShape = RoundedCornerShape(8.dp)
internal val SystemPillPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)

/**
 * The pill that carries timeline text that is not a message on the wallpaper: day separators, system events, the floating date.
 * It has a 1 dp shadow in light mode; dark mode separates it from the wallpaper by tone only.
 */
@Composable
internal fun SystemPill(
    modifier: Modifier = Modifier,
    color: Color = ElementTheme.colors.systemPillBackground,
    shape: Shape = SystemPillShape,
    contentPadding: PaddingValues = SystemPillPadding,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .then(if (ElementTheme.colors.isLight) Modifier.shadow(1.dp, shape) else Modifier)
            .background(color, shape)
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
        content = content,
    )
}

/** The amber notice pill: the beginning of the chat, warnings. */
@Composable
internal fun NoticePill(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    SystemPill(
        modifier = modifier,
        color = ElementTheme.colors.noticePillBackground,
        content = content,
    )
}

@PreviewsDayNight
@Composable
internal fun SystemPillPreview() = ElementPreview {
    Column(
        modifier = Modifier
            .background(ElementTheme.colors.chatWallpaper)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SystemPill {
            Text(
                text = "Today",
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
        NoticePill(modifier = Modifier.padding(top = 8.dp)) {
            Text(
                text = "This is the beginning of the chat.",
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.noticePillText,
            )
        }
    }
}
