/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.features.home.impl.roomlist.RoomListState
import io.element.android.features.home.impl.roomlist.RoomListStatePreviewParam
import io.element.android.features.home.impl.roomlist.aRoomListState
import io.element.android.features.home.impl.roomlist.aRoomsContentState
import io.element.android.features.home.impl.roomlist.generateRoomListRoomSummaryList
import io.element.android.features.home.impl.spaces.ChannelsHomeSpacesStatePreviewParam
import io.element.android.features.home.impl.spaces.HomeSpacesState
import io.element.android.features.home.impl.spaces.aHomeSpacesState
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarMessage
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.toImmutableList

open class HomeStatePreviewParam : PreviewParameterProvider<HomeState> {
    override val values: Sequence<HomeState>
        get() = sequenceOf(
            aHomeState(),
            aHomeState(hasNetworkConnection = false),
            aHomeState(snackbarMessage = SnackbarMessage(CommonStrings.common_verification_complete)),
            aHomeState(
                roomListState = aRoomListState(
                    // Add more rooms to see the blur effect under the NavigationBar
                    contentState = aRoomsContentState(
                        summaries = generateRoomListRoomSummaryList(),
                    )
                ),
                // For the bottom nav bar to be visible in the preview, the user must be member of at least one space
                homeSpacesState = aHomeSpacesState(),
            ),
            aHomeState(
                currentHomeNavigationBarItem = HomeNavigationBarItem.Spaces,
            ),
        ) + RoomListStatePreviewParam().values.map {
            aHomeState(roomListState = it)
        } + aHomeState(
            currentHomeNavigationBarItem = HomeNavigationBarItem.Spaces,
            homeSpacesState = aHomeSpacesState(
                spaceRooms = emptyList(),
            ),
        ) + aHomeState(
            showMultiAccountAnnouncement = true,
        ) + aHomeState(
            // The unread count badge on the Chats tab
            totalUnreadCount = 5,
        ) + aHomeState(
            // The Matcha bar: Chats · Tasks · Notes · Channels
            isMatchaWorkspacesEnabled = true,
        ) + aHomeState(
            isMatchaWorkspacesEnabled = true,
            currentHomeNavigationBarItem = HomeNavigationBarItem.Tasks,
        ) + aHomeState(
            isMatchaWorkspacesEnabled = true,
            currentHomeNavigationBarItem = HomeNavigationBarItem.Notes,
        ) + aHomeState(
            isMatchaWorkspacesEnabled = true,
            currentHomeNavigationBarItem = HomeNavigationBarItem.Channels,
            homeSpacesState = ChannelsHomeSpacesStatePreviewParam().values.first(),
        )
}

internal fun aHomeState(
    matrixUser: MatrixUser = MatrixUser(userId = UserId("@id:domain"), displayName = "User#1"),
    currentUserAndNeighbors: List<MatrixUser> = listOf(matrixUser),
    showAvatarIndicator: Boolean = false,
    hasNetworkConnection: Boolean = true,
    snackbarMessage: SnackbarMessage? = null,
    currentHomeNavigationBarItem: HomeNavigationBarItem = HomeNavigationBarItem.Chats,
    roomListState: RoomListState = aRoomListState(),
    homeSpacesState: HomeSpacesState = aHomeSpacesState(),
    canReportBug: Boolean = true,
    showMultiAccountAnnouncement: Boolean = false,
    totalUnreadCount: Int = 0,
    isMatchaWorkspacesEnabled: Boolean = false,
    eventSink: (HomeEvent) -> Unit = {}
) = HomeState(
    currentUserAndNeighbors = currentUserAndNeighbors.toImmutableList(),
    showAvatarIndicator = showAvatarIndicator,
    hasNetworkConnection = hasNetworkConnection,
    snackbarMessage = snackbarMessage,
    canReportBug = canReportBug,
    showMultiAccountAnnouncement = showMultiAccountAnnouncement,
    currentHomeNavigationBarItem = currentHomeNavigationBarItem,
    roomListState = roomListState,
    homeSpacesState = homeSpacesState,
    totalUnreadCount = totalUnreadCount,
    isMatchaWorkspacesEnabled = isMatchaWorkspacesEnabled,
    eventSink = eventSink,
)
