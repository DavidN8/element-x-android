/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import io.element.android.libraries.designsystem.icons.CompoundDrawables

/**
 * The tabs of the bottom navigation bar, in display order. A tab's label is also its top bar title (except on Chats, which shows
 * the app name); its content and floating action button are set in `HomeView`.
 */
enum class HomeNavigationBarItem(
    @StringRes
    val labelRes: Int,
    @DrawableRes
    private val iconRes: Int,
    @DrawableRes
    private val selectedIconRes: Int,
) {
    Chats(
        labelRes = R.string.screen_home_tab_chats,
        iconRes = CompoundDrawables.ic_compound_chat,
        selectedIconRes = CompoundDrawables.ic_compound_chat_solid,
    ),
    Spaces(
        labelRes = R.string.screen_home_tab_spaces,
        iconRes = CompoundDrawables.ic_compound_space,
        selectedIconRes = CompoundDrawables.ic_compound_space_solid,
    );

    /** The outlined icon, or the solid one when the tab is selected. */
    @Composable
    fun icon(isSelected: Boolean): ImageVector = ImageVector.vectorResource(if (isSelected) selectedIconRes else iconRes)

    companion object {
        fun from(index: Int): HomeNavigationBarItem {
            return entries.getOrElse(index) { Chats }
        }
    }
}
