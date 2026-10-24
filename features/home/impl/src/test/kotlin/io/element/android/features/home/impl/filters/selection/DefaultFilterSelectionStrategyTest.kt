/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.filters.selection

import com.google.common.truth.Truth.assertThat
import io.element.android.features.home.impl.filters.RoomListFilter
import org.junit.Test

class DefaultFilterSelectionStrategyTest {
    private val strategy = DefaultFilterSelectionStrategy()

    private fun selectedFilters() = strategy.filterSelectionStates.value.filter { it.isSelected }.map { it.filter }

    @Test
    fun `selecting a filter deselects the previous one`() {
        strategy.select(RoomListFilter.Unread)
        strategy.select(RoomListFilter.People)
        assertThat(selectedFilters()).containsExactly(RoomListFilter.People)
        assertThat(strategy.isSelected(RoomListFilter.Unread)).isFalse()
    }

    @Test
    fun `filters keep the enum order whatever the selection`() {
        strategy.select(RoomListFilter.Invites)
        assertThat(strategy.filterSelectionStates.value.map { it.filter }).containsExactlyElementsIn(RoomListFilter.entries).inOrder()
    }

    @Test
    fun `deselecting another filter keeps the selection`() {
        strategy.select(RoomListFilter.Rooms)
        strategy.deselect(RoomListFilter.People)
        assertThat(selectedFilters()).containsExactly(RoomListFilter.Rooms)
    }

    @Test
    fun `toggle and clear`() {
        strategy.toggle(RoomListFilter.Favourites)
        assertThat(selectedFilters()).containsExactly(RoomListFilter.Favourites)
        strategy.toggle(RoomListFilter.Favourites)
        assertThat(selectedFilters()).isEmpty()
        strategy.select(RoomListFilter.Unread)
        strategy.clear()
        assertThat(selectedFilters()).isEmpty()
    }
}
