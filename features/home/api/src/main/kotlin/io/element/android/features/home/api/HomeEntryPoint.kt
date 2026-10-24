/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.api

import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import io.element.android.libraries.architecture.FeatureEntryPoint
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.core.ThreadId
import io.element.android.libraries.matrix.api.room.JoinedRoom

interface HomeEntryPoint : FeatureEntryPoint {
    fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        callback: Callback,
    ): Node

    interface Callback : Plugin {
        fun navigateToRoom(roomId: RoomId, eventId: EventId?, joinedRoom: JoinedRoom?)
        fun navigateToCreateRoom()
        fun navigateToCreateSpace()
        fun navigateToSettings()
        fun navigateToSetUpRecovery()
        fun navigateToEnterRecoveryKey()
        fun navigateToRoomSettings(roomId: RoomId)
        fun navigateToBugReport()
        fun navigateToAddAccount()
        fun navigateToSession(sessionId: SessionId)

        /** Called with the selected tab, once Home is shown and each time the user switches tabs. */
        fun onTabSelected(tab: HomeTab)

        /** Opens a thread of a chat, such as a task's thread from the Tasks tab. */
        fun navigateToThread(roomId: RoomId, threadId: ThreadId)

        /** Opens a note of a space from the Notes tab, or the space's notes when [documentId] is null. */
        fun navigateToSpaceNote(spaceId: RoomId, documentId: String?)

        /** Called with whether the Tasks tab shows its table, which a two-pane layout gives the whole width while nothing else is open. */
        fun onTasksTableVisibilityChange(isVisible: Boolean) {
            // Only a two-pane host needs it.
        }
    }
}
