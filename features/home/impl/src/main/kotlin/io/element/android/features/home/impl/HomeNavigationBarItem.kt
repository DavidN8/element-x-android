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
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import io.element.android.libraries.designsystem.R as DesignSystemR

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
        iconRes = DesignSystemR.drawable.ic_matcha_board,
        selectedIconRes = DesignSystemR.drawable.ic_matcha_board_solid,
        homeTab = HomeTab.Tasks,
    ),

    /** The notes (space documents) of all the user's spaces, shown while the Matcha workspaces are on. */
    Notes(
        labelRes = R.string.screen_home_tab_notes,
        iconRes = DesignSystemR.drawable.ic_matcha_notes,
        selectedIconRes = DesignSystemR.drawable.ic_matcha_notes_solid,
        homeTab = HomeTab.Notes,
    ),

    /**
     * The group chats ("channels"), under the space they belong to, shown in place of [Spaces] while the Matcha workspaces are on.
     * The room icon is a hash, the usual channel sign; Compound has no solid variant of it.
     */
    Channels(
        labelRes = R.string.screen_home_tab_channels,
        iconRes = CompoundDrawables.ic_compound_room,
        selectedIconRes = CompoundDrawables.ic_compound_room,
        homeTab = HomeTab.Channels,
    );

    /** The outlined icon, or the solid one when the tab is selected. */
    @Composable
    fun icon(isSelected: Boolean): ImageVector = ImageVector.vectorResource(if (isSelected) selectedIconRes else iconRes)

    companion object {
        /** The tabs in the order of the bar, which is not the order of the entries: Chats · Tasks · Notes · Channels with the Matcha workspaces. */
        fun barOrder(isMatchaWorkspacesEnabled: Boolean): ImmutableList<HomeNavigationBarItem> = if (isMatchaWorkspacesEnabled) {
            persistentListOf(Chats, Tasks, Notes, Channels)
        } else {
            persistentListOf(Chats, Spaces)
        }

        /**
         * The tab saved with the given [ordinal]. Spaces and Channels are the same tab under two names, so a saved one maps to the
         * other when the Matcha workspaces are toggled; any other tab missing from the bar falls back to Chats.
         */
        fun from(ordinal: Int, isMatchaWorkspacesEnabled: Boolean): HomeNavigationBarItem {
            val item = when (val saved = entries.getOrElse(ordinal) { Chats }) {
                Spaces -> if (isMatchaWorkspacesEnabled) Channels else Spaces
                Channels -> if (isMatchaWorkspacesEnabled) Channels else Spaces
                else -> saved
            }
            return item.takeIf { it in barOrder(isMatchaWorkspacesEnabled) } ?: Chats
        }
    }
}
