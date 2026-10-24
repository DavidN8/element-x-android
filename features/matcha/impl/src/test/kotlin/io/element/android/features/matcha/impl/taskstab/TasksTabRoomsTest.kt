/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.taskstab

import com.google.common.truth.Truth.assertThat
import io.element.android.appconfig.MatchaConfig
import io.element.android.features.matcha.impl.store.FakePointerRoom
import io.element.android.features.matcha.impl.store.MatchaRoomStore
import io.element.android.features.matcha.impl.store.aMatchaJoinedRoom
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.BaseRoom
import io.element.android.libraries.matrix.api.room.threads.ThreadListPaginationStatus
import io.element.android.libraries.matrix.api.roomlist.RoomList
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.room.FakeBaseRoom
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.room.aRoomSummary
import io.element.android.libraries.matrix.test.roomlist.FakeDynamicRoomList
import io.element.android.libraries.matrix.test.roomlist.FakeRoomListService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class TasksTabRoomsTest {
    private val pointer = """{"version": 1, "event_id": "${'$'}board-root"}"""

    /** Room ids are unique per test: the "no board" answers are remembered process-wide. */
    private fun aRoomId(name: String) = RoomId("!$name-${System.nanoTime()}:x")

    private fun TestScope.aClient(rooms: List<Pair<RoomId, BaseRoom>>) = FakeMatrixClient(
        sessionCoroutineScope = backgroundScope,
        roomListService = FakeRoomListService(
            allRooms = FakeDynamicRoomList(
                summaries = MutableStateFlow(rooms.map { aRoomSummary(roomId = it.first) }),
                loadingState = MutableStateFlow(RoomList.LoadingState.Loaded(rooms.size)),
            ),
        ),
    ).apply {
        rooms.forEach { (roomId, room) ->
            givenGetRoomResult(roomId, room)
            if (room is FakeJoinedRoom) givenGetBaseRoomResult(roomId, FakePointerRoom(room))
        }
    }

    /** Runs [rooms] until every chat is checked and returns what it found. */
    private suspend fun TestScope.check(rooms: TasksTabRooms): TabChats {
        backgroundScope.launch { rooms.run() }
        return rooms.chats.first { it.isRoomListLoaded && it.checkingCount == 0 }
    }

    @Test
    fun `a chat the homeserver says has no board is skipped without reading its history`() = runTest {
        val roomId = aRoomId("plain")
        var timelines = 0
        val room = aMatchaJoinedRoom(roomId = roomId, roomCoroutineScope = backgroundScope, onCreateTimeline = { timelines++ }) {
            Result.success(null)
        }
        val chats = check(TasksTabRooms(aClient(listOf(roomId to room))))
        assertThat(chats.boards).isEmpty()
        assertThat(chats.failedCount).isEqualTo(0)
        assertThat(timelines).isEqualTo(0)
    }

    @Test
    fun `a chat is checked through a room without a timeline, which is let go, and only a chat with a board is opened in full`() = runTest {
        // Only a room without a timeline is given for this chat, so opening it in full would fail its check.
        val plainId = aRoomId("light")
        val plain = FakeBaseRoom(roomId = plainId, fetchStateEventContentJsonResult = { _, _ -> Result.success(null) })
        val boardId = aRoomId("full")
        val board = aMatchaJoinedRoom(roomId = boardId, roomCoroutineScope = backgroundScope) { Result.success(pointer) }
        val client = aClient(listOf(plainId to plain, boardId to board))
        val chats = check(TasksTabRooms(client))
        assertThat(chats.boards.map { it.roomId }).containsExactly(boardId)
        assertThat(chats.failedCount).isEqualTo(0)
        plain.assertDestroyed()
        assertThat((client.getRoom(boardId) as FakePointerRoom).isDestroyed).isTrue()
    }

    @Test
    fun `a chat whose board pointer only the homeserver has is found`() = runTest {
        val roomId = aRoomId("board")
        val room = aMatchaJoinedRoom(roomId = roomId, roomCoroutineScope = backgroundScope) { Result.success(pointer) }
        val chats = check(TasksTabRooms(aClient(listOf(roomId to room))))
        assertThat(chats.boards.map { it.roomId }).containsExactly(roomId)
    }

    @Test
    fun `a chat the homeserver can't be asked about counts as failed`() = runTest {
        val roomId = aRoomId("offline")
        val room = aMatchaJoinedRoom(roomId = roomId, roomCoroutineScope = backgroundScope) { Result.failure(IllegalStateException("network")) }
        val chats = check(TasksTabRooms(aClient(listOf(roomId to room))))
        assertThat(chats.boards).isEmpty()
        assertThat(chats.failedCount).isEqualTo(1)
    }

    @Test
    fun `a board this device creates is found at once, despite a recent no-board answer`() = runTest {
        val roomId = aRoomId("created")
        var answer: Result<String?> = Result.success(null)
        val room = aMatchaJoinedRoom(roomId = roomId, roomCoroutineScope = backgroundScope) { answer }
        val client = aClient(listOf(roomId to room))
        assertThat(check(TasksTabRooms(client)).boards).isEmpty()
        MatchaRoomStore(room, backgroundScope).sendState(MatchaConfig.STATE_KANBAN, "", pointer)
        answer = Result.success(pointer)
        assertThat(check(TasksTabRooms(client)).boards.map { it.roomId }).containsExactly(roomId)
    }

    @Test
    fun `a clock moved back doesn't stretch a no-board answer`() = runTest {
        val roomId = aRoomId("clock")
        var asked = 0
        val room = aMatchaJoinedRoom(roomId = roomId, roomCoroutineScope = backgroundScope) {
            asked++
            Result.success(null)
        }
        var now = 1_000_000L
        val client = aClient(listOf(roomId to room))
        check(TasksTabRooms(client, clock = { now }))
        now = 0
        check(TasksTabRooms(client, clock = { now }))
        assertThat(asked).isEqualTo(2)
    }

    @Test
    fun `a recent no-board answer is reused, and asked again once it is old`() = runTest {
        val roomId = aRoomId("cached")
        var asked = 0
        val room = aMatchaJoinedRoom(roomId = roomId, roomCoroutineScope = backgroundScope) {
            asked++
            Result.success(null)
        }
        var now = 0L
        val client = aClient(listOf(roomId to room))
        check(TasksTabRooms(client, clock = { now }))
        check(TasksTabRooms(client, clock = { now }))
        assertThat(asked).isEqualTo(1)
        now += 11 * 60_000
        check(TasksTabRooms(client, clock = { now }))
        assertThat(asked).isEqualTo(2)
    }

    @Test
    fun `coming back within a while finds the boards read without checking again, and a longer absence lets them go`() = runTest {
        val roomId = aRoomId("kept")
        var asked = 0
        var timelines = 0
        val room = aMatchaJoinedRoom(roomId = roomId, roomCoroutineScope = backgroundScope, onCreateTimeline = { timelines++ }) {
            asked++
            Result.success(pointer)
        }
        val rooms = TasksTabRooms(aClient(listOf(roomId to room)))
        val shown = backgroundScope.launch { rooms.run() }
        rooms.chats.first { it.boards.isNotEmpty() && it.checkingCount == 0 }
        shown.cancel()
        testScheduler.advanceTimeBy(TasksTabRooms.KEEP_ALIVE - 1.seconds)
        testScheduler.runCurrent()
        assertThat(room.baseRoom.isDestroyed).isFalse()

        val shownAgain = backgroundScope.launch { rooms.run() }
        assertThat(rooms.chats.first().boards.map { it.roomId }).containsExactly(roomId)
        testScheduler.advanceTimeBy(1.seconds)
        testScheduler.runCurrent()
        assertThat(asked).isEqualTo(1)
        assertThat(timelines).isEqualTo(1)

        shownAgain.cancel()
        testScheduler.advanceTimeBy(TasksTabRooms.KEEP_ALIVE + 1.seconds)
        testScheduler.runCurrent()
        room.baseRoom.assertDestroyed()
        // Everything was let go, so the next time every chat is checked again.
        assertThat(check(rooms).boards.map { it.roomId }).containsExactly(roomId)
        assertThat(asked).isEqualTo(2)
    }

    @Test
    fun `a check cut short by leaving the tab is done again next time`() = runTest {
        val roomId = aRoomId("slow")
        var timelines = 0
        // The thread list never finishes loading, so the chat is still being checked when the tab goes.
        val room = aMatchaJoinedRoom(
            roomId = roomId,
            roomCoroutineScope = backgroundScope,
            threadsPaginationStatus = ThreadListPaginationStatus.Loading,
            onCreateTimeline = { timelines++ },
        )
        val rooms = TasksTabRooms(aClient(listOf(roomId to room)))
        val shown = backgroundScope.launch { rooms.run() }
        testScheduler.advanceTimeBy(1.seconds)
        testScheduler.runCurrent()
        assertThat(timelines).isEqualTo(1)
        shown.cancel()
        testScheduler.runCurrent()
        backgroundScope.launch { rooms.run() }
        testScheduler.advanceTimeBy(1.seconds)
        testScheduler.runCurrent()
        assertThat(timelines).isEqualTo(2)
    }

    @Test
    fun `a write started from the tab still finishes after the tab has been gone a while, and its chat goes once it is done`() = runTest {
        val roomId = aRoomId("writing")
        val room = aMatchaJoinedRoom(roomId = roomId, roomCoroutineScope = backgroundScope) { Result.success(pointer) }
        val rooms = TasksTabRooms(aClient(listOf(roomId to room)))
        val shown = backgroundScope.launch { rooms.run() }
        val board = rooms.chats.first { it.boards.isNotEmpty() }.boards.single()
        val write = CompletableDeferred<Unit>()
        board.room.writeScope.launch { write.await() }
        shown.cancel()
        testScheduler.advanceTimeBy(TasksTabRooms.KEEP_ALIVE + 1.seconds)
        testScheduler.runCurrent()
        assertThat(room.baseRoom.isDestroyed).isFalse()
        write.complete(Unit)
        testScheduler.runCurrent()
        room.baseRoom.assertDestroyed()
    }
}
