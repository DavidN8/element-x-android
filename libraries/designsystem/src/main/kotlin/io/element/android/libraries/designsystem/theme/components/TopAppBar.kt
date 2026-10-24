/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.theme.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.modifiers.backgroundVerticalGradient
import io.element.android.libraries.designsystem.preview.ElementThemedPreview
import io.element.android.libraries.designsystem.preview.PreviewGroup
import io.element.android.libraries.designsystem.theme.aliasScreenTitle
import io.element.android.libraries.designsystem.utils.LocalIsInDetailPane

/**
 * A top app bar that displays a title string, navigation icon, and actions.
 * @param titleStr The title string to display in the top app bar.
 * @param modifier The [Modifier] to be applied to this top app bar.
 * @param navigationIcon The content to display as the navigation icon.
 * @param actions The content to display in the action area of the top app bar.
 * @param windowInsets The window insets to apply to this top app bar.
 * @param colors The colors used for this top app bar.
 * @param scrollBehavior Optional scroll behavior for this top app bar.
 */
@Composable
fun TopAppBar(
    titleStr: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    TopAppBar(
        title = {
            Text(
                text = titleStr,
                modifier = Modifier.semantics { heading() },
                style = ElementTheme.typography.aliasScreenTitle,
            )
        },
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        windowInsets = windowInsets,
        colors = colors,
        scrollBehavior = scrollBehavior,
    )
}

/**
 * A top app bar that displays a title, navigation icon, and actions.
 *
 * In the detail pane of the two-pane layout ([LocalIsInDetailPane]), a bar using the default colors draws the same gradient
 * band as the room list's header, so the headers of both panes read as one continuous bar.
 *
 * @param title The content to display as the title of the top app bar. Do not forget to apply `heading()` to
 * the semantics of the title to ensure it is announced correctly by accessibility services.
 * @param modifier The [Modifier] to be applied to this top app bar.
 * @param navigationIcon The content to display as the navigation icon.
 * @param actions The content to display in the action area of the top app bar.
 * @param windowInsets The window insets to apply to this top app bar.
 * @param colors The colors used for this top app bar.
 * @param scrollBehavior Optional scroll behavior for this top app bar.
 */
@Composable
fun TopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
    colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    // Bars with custom colors (transparent step pages, media viewers...) keep their own look.
    val continuesListHeader = LocalIsInDetailPane.current && colors == TopAppBarDefaults.topAppBarColors()
    androidx.compose.material3.TopAppBar(
        title = title,
        modifier = if (continuesListHeader) {
            // Opaque canvas under the gradient: some screens scroll their content under the bar.
            Modifier
                .background(ElementTheme.colors.bgCanvasDefault)
                .backgroundVerticalGradient()
                .then(modifier)
        } else {
            modifier
        },
        navigationIcon = navigationIcon,
        actions = {
            CompositionLocalProvider(LocalContentColor provides ElementTheme.colors.textActionPrimary) {
                actions()
            }
        },
        windowInsets = windowInsets,
        colors = if (continuesListHeader) {
            colors.copy(containerColor = Color.Transparent, scrolledContainerColor = Color.Transparent)
        } else {
            colors
        },
        scrollBehavior = scrollBehavior,
    )
}

@Preview(group = PreviewGroup.AppBars)
@Composable
internal fun TopAppBarPreview() = ElementThemedPreview {
    TopAppBar(
        title = { Text(text = "Title") },
        navigationIcon = { BackButton(onClick = {}) },
        actions = {
            TextButton(text = "Action", onClick = {})
            IconButton(onClick = {}) {
                Icon(
                    imageVector = CompoundIcons.ShareAndroid(),
                    contentDescription = null,
                )
            }
        }
    )
}

@Preview(group = PreviewGroup.AppBars)
@Composable
internal fun TopAppBarInDetailPanePreview() = ElementThemedPreview {
    CompositionLocalProvider(LocalIsInDetailPane provides true) {
        TopAppBar(
            titleStr = "Title string",
            actions = {
                IconButton(onClick = {}) {
                    Icon(
                        imageVector = CompoundIcons.ShareAndroid(),
                        contentDescription = null,
                    )
                }
            }
        )
    }
}

@Preview(group = PreviewGroup.AppBars)
@Composable
internal fun TopAppBarStrPreview() = ElementThemedPreview {
    TopAppBar(
        titleStr = "Title string",
        navigationIcon = { BackButton(onClick = {}) },
        actions = {
            TextButton(text = "Action", onClick = {})
            IconButton(onClick = {}) {
                Icon(
                    imageVector = CompoundIcons.ShareAndroid(),
                    contentDescription = null,
                )
            }
        }
    )
}
