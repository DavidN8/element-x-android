/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.taskstab

import com.google.common.truth.Truth.assertThat
import io.element.android.features.matcha.impl.store.aMatchaJoinedRoom
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.roomlist.RoomList
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.room.aRoomSummary
import io.element.android.libraries.matrix.test.roomlist.FakeDynamicRoomList
import io.element.android.libraries.matrix.test.roomlist.FakeRoomListService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TasksTabRoomsTest {
    private val pointer = """{"version": 1, "event_id": "${'$'}board-root"}"""

    /** Room ids are unique per test: the "no board" answers are remembered process-wide. */
    private fun aRoomId(name: String) = RoomId("!$name-${System.nanoTime()}:x")

    private fun TestScope.aClient(rooms: List<Pair<RoomId, FakeJoinedRoom>>) = FakeMatrixClient(
        sessionCoroutineScope = backgroundScope,
        roomListService = FakeRoomListService(
            allRooms = FakeDynamicRoomList(
                summaries = MutableStateFlow(rooms.map { aRoomSummary(roomId = it.first) }),
                loadingState = MutableStateFlow(RoomList.LoadingState.Loaded(rooms.size)),
            ),
        ),
    ).apply { rooms.forEach { (roomId, room) -> givenGetRoomResult(roomId, room) } }

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
}
