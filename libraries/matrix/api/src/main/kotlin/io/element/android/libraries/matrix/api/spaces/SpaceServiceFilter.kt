/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.spaces

import io.element.android.libraries.matrix.api.core.RoomId

/**
 * Represents a space filter for filtering rooms by space membership.
 *
 * @property spaceRoom The space room associated with this filter.
 * @property level The nesting level of the space (0 = top level, 1 = first level child, etc.).
 * @property descendants The list of room IDs that are descendants of this space.
 */
data class SpaceServiceFilter(
    val spaceRoom: SpaceRoom,
    val level: Int,
    val descendants: List<RoomId>,
)

/**
 * Each top level space's whole tree, by space id: a level 0 filter holds the direct children of a top level space, and the deeper
 * filters that follow it hold the rest of its tree.
 */
fun List<SpaceServiceFilter>.topLevelSpaceTrees(): Map<RoomId, Set<RoomId>> {
    val trees = mutableMapOf<RoomId, MutableSet<RoomId>>()
    var topLevelSpaceId: RoomId? = null
    for (filter in this) {
        if (filter.level == 0) topLevelSpaceId = filter.spaceRoom.roomId
        topLevelSpaceId?.let { trees.getOrPut(it) { mutableSetOf() } += filter.descendants }
    }
    return trees
}
