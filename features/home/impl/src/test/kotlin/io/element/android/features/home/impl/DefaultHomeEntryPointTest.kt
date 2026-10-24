/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.google.common.truth.Truth.assertThat
import io.element.android.features.home.api.HomeEntryPoint
import io.element.android.features.home.api.HomeTab
import io.element.android.features.invite.test.declineandblock.FakeDeclineInviteAndBlockEntryPoint
import io.element.android.features.matcha.api.MatchaNotesTabRenderer
import io.element.android.features.matcha.api.MatchaTasksTabRenderer
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.core.ThreadId
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.services.analytics.test.FakeAnalyticsService
import io.element.android.tests.testutils.lambda.lambdaError
import io.element.android.tests.testutils.node.TestParentNode
import io.element.android.tests.testutils.robolectric.RobolectricTest
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultHomeEntryPointTest : RobolectricTest() {
    @Test
    fun `test node builder`() = runTest {
        val entryPoint = DefaultHomeEntryPoint()
        val parentNode = TestParentNode.create { buildContext, plugins ->
            HomeFlowNode(
                buildContext = buildContext,
                plugins = plugins,
                matrixClient = FakeMatrixClient(),
                presenter = createHomePresenter(),
                inviteFriendsUseCase = { lambdaError() },
                analyticsService = FakeAnalyticsService(),
                acceptDeclineInviteView = { _, _, _, _ -> lambdaError() },
                reportRoomEntryPoint = { _, _, _ -> lambdaError() },
                declineInviteAndBlockUserEntryPoint = FakeDeclineInviteAndBlockEntryPoint(),
                changeRoomMemberRolesEntryPoint = { _, _, _, _ -> lambdaError() },
                leaveRoomRenderer = { _, _, _ -> lambdaError() },
                sessionCoroutineScope = backgroundScope,
                matchaTasksTabRenderer = object : MatchaTasksTabRenderer {
                    @Composable
                    override fun Render(
                        contentPadding: PaddingValues,
                        onOpenRoom: (RoomId) -> Unit,
                        onOpenThread: (RoomId, ThreadId) -> Unit,
                        modifier: Modifier,
                    ) = lambdaError()
                },
                matchaNotesTabRenderer = object : MatchaNotesTabRenderer {
                    @Composable
                    override fun Render(
                        contentPadding: PaddingValues,
                        onOpenNote: (spaceId: RoomId, documentId: String?) -> Unit,
                        modifier: Modifier,
                    ) = lambdaError()
                },
            )
        }
        val callback = object : HomeEntryPoint.Callback {
            override fun navigateToRoom(roomId: RoomId, eventId: EventId?, joinedRoom: JoinedRoom?) = lambdaError()
            override fun navigateToCreateRoom() = lambdaError()
            override fun navigateToCreateSpace() = lambdaError()
            override fun navigateToSettings() = lambdaError()
            override fun navigateToSetUpRecovery() = lambdaError()
            override fun navigateToEnterRecoveryKey() = lambdaError()
            override fun navigateToRoomSettings(roomId: RoomId) = lambdaError()
            override fun navigateToBugReport() = lambdaError()
            override fun navigateToAddAccount() = lambdaError()
            override fun navigateToSession(sessionId: SessionId) = lambdaError()
            override fun onTabSelected(tab: HomeTab) = lambdaError()
            override fun navigateToThread(roomId: RoomId, threadId: ThreadId) = lambdaError()
            override fun navigateToSpaceNote(spaceId: RoomId, documentId: String?) = lambdaError()
        }
        val result = entryPoint.createNode(
            parentNode = parentNode,
            buildContext = BuildContext.root(null),
            callback = callback,
        )
        assertThat(result).isInstanceOf(HomeFlowNode::class.java)
        assertThat(result.plugins).contains(callback)
    }
}
