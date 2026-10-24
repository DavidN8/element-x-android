/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HomeNavigationBarItemTest {
    @Test
    fun `the bar order depends on the Matcha workspaces, not on the order of the entries`() {
        assertThat(HomeNavigationBarItem.barOrder(isMatchaWorkspacesEnabled = false))
            .containsExactly(HomeNavigationBarItem.Chats, HomeNavigationBarItem.Spaces)
            .inOrder()
        assertThat(HomeNavigationBarItem.barOrder(isMatchaWorkspacesEnabled = true))
            .containsExactly(HomeNavigationBarItem.Chats, HomeNavigationBarItem.Tasks, HomeNavigationBarItem.Notes, HomeNavigationBarItem.Channels)
            .inOrder()
    }

    @Test
    fun `the saved ordinals keep their meaning, so new tabs are appended`() {
        assertThat(HomeNavigationBarItem.entries.map { it.ordinal to it }).containsExactly(
            0 to HomeNavigationBarItem.Chats,
            1 to HomeNavigationBarItem.Spaces,
            2 to HomeNavigationBarItem.Tasks,
            3 to HomeNavigationBarItem.Notes,
            4 to HomeNavigationBarItem.Channels,
        ).inOrder()
    }

    @Test
    fun `a saved tab maps to the tab of the current bar`() {
        fun from(item: HomeNavigationBarItem, isMatchaWorkspacesEnabled: Boolean) = HomeNavigationBarItem.from(item.ordinal, isMatchaWorkspacesEnabled)
        // Spaces and Channels are one tab under two names
        assertThat(from(HomeNavigationBarItem.Spaces, isMatchaWorkspacesEnabled = true)).isEqualTo(HomeNavigationBarItem.Channels)
        assertThat(from(HomeNavigationBarItem.Channels, isMatchaWorkspacesEnabled = false)).isEqualTo(HomeNavigationBarItem.Spaces)
        assertThat(from(HomeNavigationBarItem.Spaces, isMatchaWorkspacesEnabled = false)).isEqualTo(HomeNavigationBarItem.Spaces)
        assertThat(from(HomeNavigationBarItem.Channels, isMatchaWorkspacesEnabled = true)).isEqualTo(HomeNavigationBarItem.Channels)
        // The Matcha tabs fall back to Chats without the Matcha workspaces
        assertThat(from(HomeNavigationBarItem.Tasks, isMatchaWorkspacesEnabled = false)).isEqualTo(HomeNavigationBarItem.Chats)
        assertThat(from(HomeNavigationBarItem.Notes, isMatchaWorkspacesEnabled = false)).isEqualTo(HomeNavigationBarItem.Chats)
        assertThat(from(HomeNavigationBarItem.Notes, isMatchaWorkspacesEnabled = true)).isEqualTo(HomeNavigationBarItem.Notes)
        // An unknown ordinal is Chats
        assertThat(HomeNavigationBarItem.from(42, isMatchaWorkspacesEnabled = true)).isEqualTo(HomeNavigationBarItem.Chats)
    }

    @Test
    fun `each tab is the Home tab of the same name`() {
        HomeNavigationBarItem.entries.forEach { assertThat(it.homeTab.name).isEqualTo(it.name) }
    }
}
