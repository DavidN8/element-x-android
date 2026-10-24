/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.filters

import io.element.android.features.home.impl.R
import io.element.android.libraries.matrix.api.roomlist.RoomListFilter as MatrixRoomListFilter

/**
 * Enum class representing the different filters that can be applied to the room list.
 * Only one filter is selected at a time, and the chips keep this order (after the "All" chip).
 * Favourites comes first, for parity with the Matcha web client.
 */
enum class RoomListFilter(val stringResource: Int) {
    Favourites(R.string.screen_roomlist_filter_favourites),
    Unread(R.string.screen_roomlist_filter_unreads),
    Rooms(R.string.screen_roomlist_filter_rooms),
    People(R.string.screen_roomlist_filter_people),
    Invites(R.string.screen_roomlist_filter_invites),
}

fun RoomListFilter.into(): MatrixRoomListFilter {
    return when (this) {
        RoomListFilter.Rooms -> MatrixRoomListFilter.Category.Group
        RoomListFilter.People -> MatrixRoomListFilter.Category.People
        RoomListFilter.Unread -> MatrixRoomListFilter.Unread
        RoomListFilter.Favourites -> MatrixRoomListFilter.Favorite
        RoomListFilter.Invites -> MatrixRoomListFilter.Invite
    }
}
