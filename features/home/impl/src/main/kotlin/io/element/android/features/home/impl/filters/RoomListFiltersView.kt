/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.filters

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.home.impl.R
import io.element.android.features.home.impl.spacefilters.SpaceFiltersEvent
import io.element.android.features.home.impl.spacefilters.SpaceFiltersState
import io.element.android.features.home.impl.spacefilters.aSelectedSpaceFiltersState
import io.element.android.features.home.impl.spacefilters.anUnselectedSpaceFiltersState
import io.element.android.libraries.designsystem.components.PillFilterChip
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.ui.strings.CommonStrings

/**
 * The WhatsApp-style chips of the chat list: "All", then one chip per [RoomListFilter], single-select,
 * then a trailing "Spaces" chip that opens the space picker when space filters are available.
 */
@Composable
fun RoomListFiltersView(
    state: RoomListFiltersState,
    spaceFiltersState: SpaceFiltersState,
    modifier: Modifier = Modifier
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        // The chips are a single choice, announced as a group of radio buttons
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item("all") {
            // All means every chat: it is not selected inside a space, and tapping it also leaves the space.
            PillFilterChip(
                label = stringResource(CommonStrings.common_matcha_all),
                selected = !state.hasAnyFilterSelected && spaceFiltersState !is SpaceFiltersState.Selected,
                onClick = {
                    state.eventSink(RoomListFiltersEvent.ClearSelectedFilters)
                    if (spaceFiltersState is SpaceFiltersState.Selected) {
                        spaceFiltersState.eventSink(SpaceFiltersEvent.Selected.ClearSelection)
                    }
                },
            )
        }
        items(
            items = state.filterSelectionStates,
            key = { it.filter },
        ) { filterWithSelection ->
            PillFilterChip(
                label = stringResource(id = filterWithSelection.filter.stringResource),
                selected = filterWithSelection.isSelected,
                onClick = { state.eventSink(RoomListFiltersEvent.ToggleFilter(filterWithSelection.filter)) },
            )
        }
        if (spaceFiltersState != SpaceFiltersState.Disabled) {
            item("spaces") {
                SpacesChip(spaceFiltersState)
            }
        }
    }
}

@Composable
private fun SpacesChip(spaceFiltersState: SpaceFiltersState) {
    if (spaceFiltersState is SpaceFiltersState.Selected) {
        PillFilterChip(
            label = spaceFiltersState.selectedFilter.spaceRoom.displayName,
            selected = true,
            role = Role.Button,
            trailingIcon = CompoundIcons.ChevronDown(),
            maxLabelWidth = 160.dp,
            // Tapping the selected chip goes back to all chats, like the other chips.
            contentDescription = stringResource(R.string.screen_roomlist_clear_filters),
            onClick = { spaceFiltersState.eventSink(SpaceFiltersEvent.Selected.ClearSelection) },
        )
    } else {
        PillFilterChip(
            label = stringResource(R.string.screen_home_tab_spaces),
            selected = false,
            role = Role.Button,
            trailingIcon = CompoundIcons.ChevronDown(),
            onClick = {
                if (spaceFiltersState is SpaceFiltersState.Unselected) {
                    spaceFiltersState.eventSink(SpaceFiltersEvent.Unselected.ShowFilters)
                }
            },
        )
    }
}

@PreviewsDayNight
@Composable
internal fun RoomListFiltersViewPreview(@PreviewParameter(RoomListFiltersStatePreviewParam::class) state: RoomListFiltersState) = ElementPreview {
    RoomListFiltersView(
        modifier = Modifier.padding(vertical = 4.dp),
        state = state,
        spaceFiltersState = anUnselectedSpaceFiltersState(),
    )
}

@PreviewsDayNight
@Composable
internal fun RoomListFiltersViewSpaceSelectedPreview() = ElementPreview {
    RoomListFiltersView(
        modifier = Modifier.padding(vertical = 4.dp),
        state = aRoomListFiltersState(),
        spaceFiltersState = aSelectedSpaceFiltersState(),
    )
}
