/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.theme.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemColors
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import io.element.android.compound.theme.ElementTheme

@Composable
fun RowScope.NavigationBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    label: @Composable (() -> Unit)? = null,
    alwaysShowLabel: Boolean = true,
    colors: NavigationBarItemColors = ElementNavigationBarItemDefaults.colors(),
    interactionSource: MutableInteractionSource? = null
) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = icon,
        modifier = modifier,
        enabled = enabled,
        label = label?.let {
            {
                ProvideTextStyle(ElementNavigationBarItemDefaults.labelStyle(selected)) {
                    it()
                }
            }
        },
        alwaysShowLabel = alwaysShowLabel,
        colors = colors,
        interactionSource = interactionSource,
    )
}

object ElementNavigationBarItemDefaults {
    /**
     * The selected item gets a tonal green pill (64 x 32 dp, the Material 3 indicator) behind a dark green icon.
     * Callers pass the solid icon variant when selected and the outlined one otherwise.
     */
    @Composable
    fun colors() = NavigationBarItemDefaults.colors().copy(
        selectedIconColor = ElementTheme.colors.textBadgeAccent,
        selectedTextColor = ElementTheme.colors.textPrimary,
        selectedIndicatorColor = ElementTheme.colors.bgAccentSubtle,
        unselectedIconColor = ElementTheme.colors.iconSecondary,
        unselectedTextColor = ElementTheme.colors.textSecondary,
        disabledIconColor = ElementTheme.colors.iconDisabled,
        disabledTextColor = ElementTheme.colors.textDisabled,
    )

    @Composable
    fun labelStyle(selected: Boolean): TextStyle = if (selected) {
        ElementTheme.typography.fontBodySmMedium
    } else {
        ElementTheme.typography.fontBodySmRegular
    }
}
