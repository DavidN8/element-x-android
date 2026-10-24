/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.di

import com.google.common.truth.Truth.assertThat
import io.element.android.appconfig.MatchaConfig
import io.element.android.features.matcha.impl.store.A_BOARD_ROOT
import io.element.android.features.matcha.impl.store.PointerRoom
import io.element.android.features.matcha.impl.store.aMatchaJoinedRoom
import io.element.android.features.matcha.impl.taskstab.TasksTabRooms
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.roomlist.RoomList
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.room.aRoomSummary
import io.element.android.libraries.matrix.test.roomlist.FakeDynamicRoomList
import io.element.android.libraries.matrix.test.roomlist.FakeRoomListService
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class MatchaRoomBindingContainerTest {
    private val pointer = """{"version": 1, "event_id": "${'$'}board-root"}"""

    /** Room ids are unique per test: the "no board" answers are remembered process-wide. */
    private fun aRoomId(name: String) = RoomId("!$name-${System.nanoTime()}:x")

    @Test
    fun `a chat the Tasks tab has open reads and writes through the tab's store, and lets it go once its screens are gone`() = runTest {
        val roomId = aRoomId("shared")
        val tabRoom = aMatchaJoinedRoom(roomId = roomId, roomCoroutineScope = backgroundScope) { Result.success(pointer) }
        val client = FakeMatrixClient(
            sessionCoroutineScope = backgroundScope,
            roomListService = FakeRoomListService(
                allRooms = FakeDynamicRoomList(
                    summaries = MutableStateFlow(listOf(aRoomSummary(roomId = roomId))),
                    loadingState = MutableStateFlow(RoomList.LoadingState.Loaded(1)),
                ),
            ),
        ).apply {
            givenGetRoomResult(roomId, tabRoom)
            givenGetBaseRoomResult(roomId, PointerRoom(tabRoom))
        }
        val rooms = TasksTabRooms(client)
        val shown = backgroundScope.launch { rooms.run() }
        val board = rooms.chats.first { it.boards.isNotEmpty() }.boards.single()

        // The chat's own room is never read nor written: everything goes through the tab's.
        var chatTimelines = 0
        val chatScope = CoroutineScope(Job())
        val chatRoom = aMatchaJoinedRoom(
            roomId = roomId,
            roomCoroutineScope = chatScope,
            onCreateTimeline = { chatTimelines++ },
            sendRawEventResult = { _, _ -> error("Sent through the chat's own room") },
        )
        val store = MatchaRoomBindingContainer.providesMatchaRoomStore(chatRoom, rooms, testCoroutineDispatchers())
        assertThat(store).isSameInstanceAs(board.room.store)
        store.send(MatchaConfig.EVENT_THREAD_METADATA, "{}")
        rooms.chats.first { chats -> chats.boards.single().snapshot.events.any { it.eventId == "${'$'}sent" } }
        assertThat(chatTimelines).isEqualTo(0)

        shown.cancel()
        testScheduler.advanceTimeBy(TasksTabRooms.KEEP_ALIVE + 1.seconds)
        testScheduler.runCurrent()
        assertThat(tabRoom.baseRoom.isDestroyed).isFalse()
        chatScope.cancel()
        testScheduler.runCurrent()
        tabRoom.baseRoom.assertDestroyed()
    }

    @Test
    fun `a chat the Tasks tab doesn't have open gets a store of its own`() = runTest {
        val rooms = TasksTabRooms(FakeMatrixClient(sessionCoroutineScope = backgroundScope))
        var timelines = 0
        val room = aMatchaJoinedRoom(
            roomId = aRoomId("own"),
            roomCoroutineScope = backgroundScope,
            boardRoot = A_BOARD_ROOT,
            onCreateTimeline = { timelines++ },
        )
        val store = MatchaRoomBindingContainer.providesMatchaRoomStore(room, rooms, testCoroutineDispatchers())
        assertThat(store.snapshot.first { it.isLoaded }.board?.rootId).isEqualTo(A_BOARD_ROOT)
        assertThat(timelines).isEqualTo(1)
    }
}
