/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.spaces

import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.spaces.SpaceRoom
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet

data class HomeSpacesState(
    val space: CurrentSpace,
    val spaceRooms: ImmutableList<SpaceRoom>,
    val seenSpaceInvites: ImmutableSet<RoomId>,
    val hideInvitesAvatar: Boolean,
    val canExploreSpaces: Boolean,
    /** What the Channels tab lists, in place of this tab while the Matcha workspaces are on; null while it loads, and while they are off. */
    val channels: HomeChannels?,
    val eventSink: (HomeSpacesEvent) -> Unit,
)

/** The joined group chats ("channels"): under the top level space they belong to, then the ones in no space. */
data class HomeChannels(
    val spaces: ImmutableList<SpaceChannels>,
    val otherChannels: ImmutableList<RoomListRoomSummary>,
) {
    val isEmpty = spaces.isEmpty() && otherChannels.isEmpty()
}

/** A joined top level space and the joined channels of its whole tree, most recent first. */
data class SpaceChannels(
    val space: SpaceRoom,
    val channels: ImmutableList<RoomListRoomSummary>,
)

sealed interface CurrentSpace {
    object Root : CurrentSpace
    data class Space(val spaceRoom: SpaceRoom) : CurrentSpace
}
