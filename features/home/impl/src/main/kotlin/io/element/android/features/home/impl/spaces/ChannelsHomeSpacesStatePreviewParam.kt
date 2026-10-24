/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.spaces

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.features.home.impl.model.LatestEvent
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.features.home.impl.model.aRoomListRoomSummary
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.CurrentUserMembership
import io.element.android.libraries.matrix.api.spaces.SpaceRoom
import io.element.android.libraries.previewutils.room.aSpaceRoom
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** The states of the Channels tab, which reuses the state of the Spaces tab. */
open class ChannelsHomeSpacesStatePreviewParam : PreviewParameterProvider<HomeSpacesState> {
    override val values: Sequence<HomeSpacesState>
        get() = sequenceOf(
            // Spaces with their channels, then the other channels
            aChannelsHomeSpacesState(),
            // No space: the channels are the whole list
            aChannelsHomeSpacesState(spaces = emptyList()),
            // Nothing to list
            aChannelsHomeSpacesState(spaces = emptyList(), otherChannels = emptyList()),
            // Loading
            aHomeSpacesState(spaceRooms = emptyList(), channels = null),
            // Pending space invites stay at the top
            aChannelsHomeSpacesState(
                invites = listOf(
                    aSpaceRoom(displayName = "Design guild", roomId = RoomId("!invite1:example.com"), state = CurrentUserMembership.INVITED),
                    aSpaceRoom(displayName = "Book club", roomId = RoomId("!invite2:example.com"), state = CurrentUserMembership.INVITED),
                ),
                seenSpaceInvites = setOf(RoomId("!invite2:example.com")),
            ),
        )
}

private fun aChannelsHomeSpacesState(
    spaces: List<SpaceChannels> = aListOfSpaceChannels(),
    otherChannels: List<RoomListRoomSummary> = listOf(
        aRoomListRoomSummary(id = "!other1:example.com", name = "Weekend plans", timestamp = "Yesterday"),
    ),
    invites: List<SpaceRoom> = emptyList(),
    seenSpaceInvites: Set<RoomId> = emptySet(),
) = aHomeSpacesState(
    spaceRooms = invites + spaces.map { it.space },
    seenSpaceInvites = seenSpaceInvites,
    channels = HomeChannels(spaces = spaces.toImmutableList(), otherChannels = otherChannels.toImmutableList()),
)

private fun aListOfSpaceChannels() = listOf(
    SpaceChannels(
        space = aSpaceRoom(displayName = "Acme", roomId = RoomId("!acme:example.com"), state = CurrentUserMembership.JOINED),
        channels = persistentListOf(
            aRoomListRoomSummary(
                id = "!general:example.com",
                name = "general",
                numberOfUnreadMessages = 3,
                numberOfUnreadNotifications = 3,
                timestamp = "10:42",
            ),
            aRoomListRoomSummary(id = "!design:example.com", name = "design", latestEvent = LatestEvent.Synced("New mock-ups are up")),
        ),
    ),
    // A space the user is in none of the channels of
    SpaceChannels(
        space = aSpaceRoom(displayName = "Neighbours", roomId = RoomId("!neighbours:example.com"), state = CurrentUserMembership.JOINED),
        channels = persistentListOf(),
    ),
)
