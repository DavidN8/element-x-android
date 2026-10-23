/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class)

package io.element.android.features.home.impl.filters

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import io.element.android.features.home.impl.R
import io.element.android.features.home.impl.filters.selection.FilterSelectionState
import io.element.android.features.home.impl.spacefilters.SpaceFiltersEvent
import io.element.android.features.home.impl.spacefilters.SpaceFiltersState
import io.element.android.features.home.impl.spacefilters.aSelectedSpaceFiltersState
import io.element.android.features.home.impl.spacefilters.anUnselectedSpaceFiltersState
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.tests.testutils.EventsRecorder
import io.element.android.tests.testutils.clickOn
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test
import org.robolectric.annotation.Config

class RoomListFiltersViewTest : RobolectricTest() {
    @Test
    fun `clicking on filters generates expected Event`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<RoomListFiltersEvent>()
        setContent {
            RoomListFiltersView(
                state = aRoomListFiltersState(eventSink = eventsRecorder),
                spaceFiltersState = SpaceFiltersState.Disabled,
            )
        }
        clickOn(R.string.screen_roomlist_filter_rooms)
        eventsRecorder.assertList(
            listOf(
                RoomListFiltersEvent.ToggleFilter(RoomListFilter.Rooms),
            )
        )
    }

    @Test
    fun `clicking on All generates expected Event`() = runAndroidComposeUiTest<ComponentActivity> {
        val eventsRecorder = EventsRecorder<RoomListFiltersEvent>()
        setContent {
            RoomListFiltersView(
                state = aRoomListFiltersState(
                    filterSelectionStates = RoomListFilter.entries.map { FilterSelectionState(it, isSelected = it == RoomListFilter.Rooms) },
                    eventSink = eventsRecorder
                ),
                spaceFiltersState = SpaceFiltersState.Disabled,
            )
        }
        clickOn(CommonStrings.common_matcha_all)
        eventsRecorder.assertList(
            listOf(
                RoomListFiltersEvent.ClearSelectedFilters,
            )
        )
    }

    @Test
    fun `All is not selected inside a space, and clicking it also clears the space filter`() = runAndroidComposeUiTest<ComponentActivity> {
        val filtersEventsRecorder = EventsRecorder<RoomListFiltersEvent>()
        val spaceEventsRecorder = EventsRecorder<SpaceFiltersEvent.Selected>()
        setContent {
            RoomListFiltersView(
                state = aRoomListFiltersState(eventSink = filtersEventsRecorder),
                spaceFiltersState = aSelectedSpaceFiltersState(eventSink = spaceEventsRecorder),
            )
        }
        val all = onNodeWithText(activity!!.getString(CommonStrings.common_matcha_all))
        all.assertIsNotSelected()
        all.performClick()
        filtersEventsRecorder.assertSingle(RoomListFiltersEvent.ClearSelectedFilters)
        spaceEventsRecorder.assertSingle(SpaceFiltersEvent.Selected.ClearSelection)
    }

    @Test
    fun `All is selected when no filter and no space is selected`() = runAndroidComposeUiTest<ComponentActivity> {
        setContent {
            RoomListFiltersView(
                state = aRoomListFiltersState(),
                spaceFiltersState = anUnselectedSpaceFiltersState(),
            )
        }
        onNodeWithText(activity!!.getString(CommonStrings.common_matcha_all)).assertIsSelected()
    }

    // The Spaces chip is the last one: make the row wide enough for it to be composed.
    @Config(qualifiers = "w1024dp")
    @Test
    fun `clicking on the Spaces chip opens the space filters`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<SpaceFiltersEvent.Unselected>()
        setContent {
            RoomListFiltersView(
                state = aRoomListFiltersState(),
                spaceFiltersState = anUnselectedSpaceFiltersState(eventSink = eventsRecorder),
            )
        }
        clickOn(R.string.screen_home_tab_spaces)
        eventsRecorder.assertSingle(SpaceFiltersEvent.Unselected.ShowFilters)
    }

    // The Spaces chip is the last one: make the row wide enough for it to be composed.
    @Config(qualifiers = "w1024dp")
    @Test
    fun `clicking on the selected space chip clears the space filter`() = runAndroidComposeUiTest<ComponentActivity> {
        val eventsRecorder = EventsRecorder<SpaceFiltersEvent.Selected>()
        val state = aSelectedSpaceFiltersState(eventSink = eventsRecorder)
        setContent {
            RoomListFiltersView(
                state = aRoomListFiltersState(),
                spaceFiltersState = state,
            )
        }
        onNodeWithText(state.selectedFilter.spaceRoom.displayName).performClick()
        eventsRecorder.assertSingle(SpaceFiltersEvent.Selected.ClearSelection)
    }
}
