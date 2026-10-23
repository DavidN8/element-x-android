/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.theme.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRowScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.theme.chipOutline

@Composable
fun SingleChoiceSegmentedButtonRowScope.SegmentedButton(
    index: Int,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    text: String,
    modifier: Modifier = Modifier,
    interactionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    enabled: Boolean = true,
) {
    SegmentedButton(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        interactionSource = interactionSource,
        enabled = enabled,
        shape = SegmentedButtonDefaults.itemShape(index = index, count = count),
        label = {
            Text(
                text = text,
                style = ElementTheme.typography.fontBodyMdMedium,
            )
        },
        // Styled like the filter chips: the selected segment is tonal green, the outline a neutral hairline.
        colors = SegmentedButtonDefaults.colors(
            activeContainerColor = ElementTheme.colors.bgAccentSubtle,
            activeContentColor = ElementTheme.colors.textBadgeAccent,
            activeBorderColor = ElementTheme.colors.chipOutline,
            inactiveContainerColor = ElementTheme.materialColors.surface,
            inactiveContentColor = ElementTheme.colors.textSecondary,
            inactiveBorderColor = ElementTheme.colors.chipOutline,
            disabledActiveContainerColor = ElementTheme.colors.bgSubtlePrimary,
            disabledActiveContentColor = ElementTheme.colors.textDisabled,
            disabledActiveBorderColor = ElementTheme.colors.chipOutline,
            disabledInactiveContainerColor = ElementTheme.materialColors.surface,
            disabledInactiveContentColor = ElementTheme.colors.textDisabled,
            disabledInactiveBorderColor = Color.Transparent,
        )
    )
}
