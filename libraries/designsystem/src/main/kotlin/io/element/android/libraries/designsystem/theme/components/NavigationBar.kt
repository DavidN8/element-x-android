/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.theme.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.preview.ElementThemedPreview
import io.element.android.libraries.designsystem.preview.PreviewGroup
import io.element.android.libraries.designsystem.theme.raisedSurface

/**
 * A WhatsApp-style bottom navigation bar: 80 dp tall, labels always shown, a tonal green pill behind the selected icon.
 * In light mode it sits on the canvas with a hairline top edge; in dark mode it is a raised surface without an edge.
 */

@Composable
fun NavigationBar(
    modifier: Modifier = Modifier,
    containerColor: Color = ElementNavigationBarDefaults.containerColor,
    contentColor: Color = ElementTheme.colors.textPrimary,
    tonalElevation: Dp = ElementNavigationBarDefaults.tonalElevation,
    windowInsets: WindowInsets = ElementNavigationBarDefaults.windowInsets,
    content: @Composable RowScope.() -> Unit
) {
    val topEdgeColor = ElementNavigationBarDefaults.topEdgeColor
    androidx.compose.material3.NavigationBar(
        modifier = modifier.drawWithContent {
            drawContent()
            if (topEdgeColor != Color.Transparent) {
                drawRect(color = topEdgeColor, size = Size(size.width, 1.dp.toPx()))
            }
        },
        containerColor = containerColor,
        contentColor = contentColor,
        tonalElevation = tonalElevation,
        windowInsets = windowInsets,
        content = content
    )
}

object ElementNavigationBarDefaults {
    val containerColor: Color
        @Composable get() = if (ElementTheme.isLightTheme) {
            ElementTheme.colors.bgCanvasDefault
        } else {
            ElementTheme.colors.raisedSurface
        }

    /** A hairline between the content and the bar, in light mode only: in dark mode the raised tone separates them. */
    val topEdgeColor: Color
        @Composable get() = if (ElementTheme.isLightTheme) {
            ElementTheme.colors.separatorPrimary
        } else {
            Color.Transparent
        }

    // Flat: the bar is separated by its edge or its tone, never by a shadow or a tonal tint.
    val tonalElevation: Dp = 0.dp

    val windowInsets: WindowInsets
        @Composable get() = NavigationBarDefaults.windowInsets
}

@Preview(group = PreviewGroup.AppBars)
@Composable
internal fun NavigationBarPreview() = ElementThemedPreview {
    NavigationBar {
        NavigationBarItem(
            icon = {
                NavigationBarIcon(
                    imageVector = CompoundIcons.ChatSolid(),
                    count = 5,
                    isCritical = false,
                )
            },
            label = {
                NavigationBarText(
                    text = "Chats"
                )
            },
            selected = true,
            onClick = {},
        )
        NavigationBarItem(
            icon = {
                NavigationBarIcon(
                    imageVector = CompoundIcons.Chat(),
                    count = 5,
                    isCritical = true,
                )
            },
            label = {
                NavigationBarText(
                    text = "Teams"
                )
            },
            selected = false,
            onClick = {},
        )
        NavigationBarItem(
            icon = {
                NavigationBarIcon(
                    imageVector = CompoundIcons.Chat(),
                    count = 0,
                    isCritical = false,
                )
            },
            label = {
                NavigationBarText(
                    text = "Other"
                )
            },
            selected = false,
            onClick = {},
        )
    }
}
