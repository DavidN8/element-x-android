/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.utils

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf

/**
 * True inside the detail (end) pane of the two-pane layout, where the screens' top bars continue the room list's header band.
 * Only the two-pane layout provides it, so phones and single-pane windows always read false.
 */
val LocalIsInDetailPane = staticCompositionLocalOf { false }

@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun hasCompactHeightWindowSize(): Boolean {
    return currentWindowAdaptiveInfo().windowSizeClass.heightSizeClass == WindowHeightSizeClass.Compact
}

/**
 * True when the window is wide and tall enough for a list/detail two-pane layout
 * (tablets, unfolded foldables). Phones, including in landscape, return false.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun hasTwoPaneWindowSize(): Boolean {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass
    return windowSizeClass.widthSizeClass != WindowWidthSizeClass.Compact &&
        windowSizeClass.heightSizeClass != WindowHeightSizeClass.Compact
}
