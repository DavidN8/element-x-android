/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.spaces

import com.google.common.truth.Truth.assertThat
import io.element.android.features.home.impl.datasource.aRoomListRoomSummaryFactory
import io.element.android.features.home.impl.spacefilters.aSpaceServiceFilter
import io.element.android.features.invite.api.SeenInvitesStore
import io.element.android.features.invite.test.InMemorySeenInvitesStore
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.featureflag.test.FakeFeatureFlagService
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.CurrentUserMembership
import io.element.android.libraries.matrix.api.roomlist.RoomList
import io.element.android.libraries.matrix.api.roomlist.RoomSummary
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.room.aRoomInfo
import io.element.android.libraries.matrix.test.room.aRoomSummary
import io.element.android.libraries.matrix.test.roomlist.FakeDynamicRoomList
import io.element.android.libraries.matrix.test.roomlist.FakeRoomListService
import io.element.android.libraries.matrix.test.spaces.FakeSpaceService
import io.element.android.libraries.previewutils.room.aSpaceRoom
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.lambda.lambdaRecorder
import io.element.android.tests.testutils.test
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class HomeSpacesPresenterTest {
    @Test
    fun `present - initial state`() = runTest {
        val presenter = createHomeSpacesPresenter()
        presenter.test {
            val state = awaitItem()
            assertThat(state.space).isEqualTo(CurrentSpace.Root)
            assertThat(state.spaceRooms).isEmpty()
            assertThat(state.hideInvitesAvatar).isFalse()
            assertThat(state.seenSpaceInvites).isEmpty()
            assertThat(state.channels).isNull()
        }
    }

    @Test
    fun `present - without the Matcha workspaces, the channels are not built`() = runTest {
        val rebuildSummaries = lambdaRecorder<Unit> { }
        val roomList = FakeDynamicRoomList(
            summaries = MutableStateFlow(listOf(aChannel(OTHER))),
            loadingState = MutableStateFlow(RoomList.LoadingState.Loaded(1)),
            rebuildSummariesLambda = rebuildSummaries,
        )
        val presenter = createHomeSpacesPresenter(
            client = FakeMatrixClient(roomListService = FakeRoomListService(allRooms = roomList)),
            featureFlagService = FakeFeatureFlagService(initialState = mapOf(FeatureFlags.MatchaWorkspaces.key to false)),
        )
        presenter.test {
            advanceTimeBy(1.seconds)
            runCurrent()
            assertThat(expectMostRecentItem().channels).isNull()
            rebuildSummaries.assertions().isNeverCalled()
        }
    }

    @Test
    fun `present - the room list is rebuilt when the notification settings change, so the mute icons are right`() = runTest {
        val rebuildSummaries = lambdaRecorder<Unit> { }
        val roomList = FakeDynamicRoomList(rebuildSummariesLambda = rebuildSummaries)
        val presenter = createHomeSpacesPresenter(
            client = FakeMatrixClient(roomListService = FakeRoomListService(allRooms = roomList)),
        )
        presenter.test {
            // The fake service signals a change as soon as it is observed.
            advanceTimeBy(1.seconds)
            runCurrent()
            rebuildSummaries.assertions().isCalledOnce()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - the channels are grouped under their top level space, then the ones in no space`() = runTest {
        val spaceService = FakeSpaceService()
        val roomList = FakeDynamicRoomList(
            // In recency order, as the SDK sorts them
            summaries = MutableStateFlow(
                listOf(
                    aChannel(SUB_SPACE_CHANNEL),
                    aChannel(B_CHANNEL),
                    aRoomSummary(aRoomInfo(id = DM, isDirect = true, isDm = true, activeMembersCount = 2)),
                    aChannel(OTHER),
                    aChannel(A_CHANNEL),
                    aRoomSummary(aRoomInfo(id = SUB_SPACE, isSpace = true)),
                    aRoomSummary(aRoomInfo(id = INVITED_ROOM, currentUserMembership = CurrentUserMembership.INVITED)),
                    aChannel(SHARED),
                    aRoomSummary(aRoomInfo(id = LEFT_CHILD, currentUserMembership = CurrentUserMembership.LEFT)),
                )
            ),
            loadingState = MutableStateFlow(RoomList.LoadingState.NotLoaded),
        )
        val presenter = createHomeSpacesPresenter(
            client = FakeMatrixClient(spaceService = spaceService, roomListService = FakeRoomListService(allRooms = roomList)),
        )
        presenter.test {
            runCurrent()
            // Loading until the room list is loaded
            assertThat(expectMostRecentItem().channels).isNull()
            spaceService.emitTopLevelSpaces(
                listOf(
                    aSpaceRoom(roomId = SPACE_A, state = CurrentUserMembership.JOINED),
                    aSpaceRoom(roomId = SPACE_B, state = CurrentUserMembership.JOINED),
                    aSpaceRoom(roomId = SPACE_INVITE, state = CurrentUserMembership.INVITED),
                )
            )
            spaceService.emitSpaceFilters(
                listOf(
                    aSpaceServiceFilter(roomId = SPACE_A, level = 0, descendants = listOf(A_CHANNEL, SUB_SPACE, SHARED, LEFT_CHILD)),
                    // The rest of the tree of A
                    aSpaceServiceFilter(roomId = SUB_SPACE, level = 1, descendants = listOf(SUB_SPACE_CHANNEL)),
                    aSpaceServiceFilter(roomId = SPACE_B, level = 0, descendants = listOf(B_CHANNEL, SHARED)),
                )
            )
            roomList.loadingState.value = RoomList.LoadingState.Loaded(9)
            val channels = consumeItemsUntilPredicate { it.channels?.spaces?.size == 2 }.last().channels!!

            // The invite is not a block: it keeps its Spaces tab row
            assertThat(channels.spaces.map { it.space.roomId }).containsExactly(SPACE_A, SPACE_B).inOrder()
            assertThat(channels.spaces[0].channels.map { it.roomId }).containsExactly(SUB_SPACE_CHANNEL, A_CHANNEL, SHARED).inOrder()
            assertThat(channels.spaces[1].channels.map { it.roomId }).containsExactly(B_CHANNEL, SHARED).inOrder()
            // Not the DM, the sub space, the invite, nor the channels of a space
            assertThat(channels.otherChannels.map { it.roomId }).containsExactly(OTHER)
        }
    }

    @Test
    fun `present - without any space, every channel is an other channel`() = runTest {
        val roomList = FakeDynamicRoomList(
            summaries = MutableStateFlow(listOf(aChannel(A_CHANNEL), aChannel(OTHER))),
            loadingState = MutableStateFlow(RoomList.LoadingState.Loaded(2)),
        )
        val presenter = createHomeSpacesPresenter(
            client = FakeMatrixClient(roomListService = FakeRoomListService(allRooms = roomList)),
        )
        presenter.test {
            val channels = consumeItemsUntilPredicate { it.channels != null }.last().channels!!
            assertThat(channels.spaces).isEmpty()
            assertThat(channels.otherChannels.map { it.roomId }).containsExactly(A_CHANNEL, OTHER).inOrder()
        }
    }

    private fun aChannel(roomId: RoomId): RoomSummary = aRoomSummary(aRoomInfo(id = roomId, isDirect = false, activeMembersCount = 5))

    private fun TestScope.createHomeSpacesPresenter(
        client: MatrixClient = FakeMatrixClient(),
        seenInvitesStore: SeenInvitesStore = InMemorySeenInvitesStore(),
        featureFlagService: FeatureFlagService = FakeFeatureFlagService(initialState = mapOf(FeatureFlags.MatchaWorkspaces.key to true)),
    ) = HomeSpacesPresenter(
        client = client,
        seenInvitesStore = seenInvitesStore,
        featureFlagService = featureFlagService,
        roomListRoomSummaryFactory = aRoomListRoomSummaryFactory(),
        coroutineDispatchers = testCoroutineDispatchers(useUnconfinedTestDispatcher = true),
    )

    private companion object {
        val SPACE_A = RoomId("!spaceA:example.com")
        val SPACE_B = RoomId("!spaceB:example.com")
        val SPACE_INVITE = RoomId("!spaceInvite:example.com")
        val SUB_SPACE = RoomId("!subSpace:example.com")
        val A_CHANNEL = RoomId("!aChannel:example.com")
        val SUB_SPACE_CHANNEL = RoomId("!subSpaceChannel:example.com")
        val B_CHANNEL = RoomId("!bChannel:example.com")
        val SHARED = RoomId("!shared:example.com")
        val LEFT_CHILD = RoomId("!leftChild:example.com")
        val DM = RoomId("!dm:example.com")
        val INVITED_ROOM = RoomId("!invited:example.com")
        val OTHER = RoomId("!other:example.com")
    }
}
