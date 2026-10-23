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
import io.element.android.features.home.api.HomeTab
import io.element.android.libraries.designsystem.icons.CompoundDrawables
import io.element.android.libraries.ui.strings.CommonStrings

/**
 * The tabs of the bottom navigation bar. A tab's label is also its top bar title (except on Chats, which shows the product
 * name); its content and floating action button are set in `HomeView`.
 *
 * The selected tab is saved by its ordinal, so new tabs are appended at the end; the bar follows [barOrder].
 */
enum class HomeNavigationBarItem(
    @StringRes
    val labelRes: Int,
    @DrawableRes
    private val iconRes: Int,
    @DrawableRes
    private val selectedIconRes: Int,
    /** The tab as the rest of the app sees it. */
    val homeTab: HomeTab,
) {
    Chats(
        labelRes = R.string.screen_home_tab_chats,
        iconRes = CompoundDrawables.ic_compound_chat,
        selectedIconRes = CompoundDrawables.ic_compound_chat_solid,
        homeTab = HomeTab.Chats,
    ),
    Spaces(
        labelRes = R.string.screen_home_tab_spaces,
        iconRes = CompoundDrawables.ic_compound_space,
        selectedIconRes = CompoundDrawables.ic_compound_space_solid,
        homeTab = HomeTab.Spaces,
    ),

    /** The Matcha tasks of all the user's chats, shown while the Matcha workspaces are on. */
    Tasks(
        labelRes = CommonStrings.common_matcha_tasks,
        iconRes = CompoundDrawables.ic_compound_check_circle,
        selectedIconRes = CompoundDrawables.ic_compound_check_circle_solid,
        homeTab = HomeTab.Tasks,
    );

    /** The outlined icon, or the solid one when the tab is selected. */
    @Composable
    fun icon(isSelected: Boolean): ImageVector = ImageVector.vectorResource(if (isSelected) selectedIconRes else iconRes)

    companion object {
        /** The tabs in the order of the bar, which is not the order of the entries; Tasks only exists with the Matcha workspaces. */
        fun barOrder(isTasksTabEnabled: Boolean): List<HomeNavigationBarItem> = listOfNotNull(Chats, Tasks.takeIf { isTasksTabEnabled }, Spaces)

        fun from(index: Int): HomeNavigationBarItem {
            return entries.getOrElse(index) { Chats }
        }
    }
}
