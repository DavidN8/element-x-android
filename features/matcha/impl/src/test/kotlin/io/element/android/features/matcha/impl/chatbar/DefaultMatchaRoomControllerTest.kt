/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.chatbar

import com.google.common.truth.Truth.assertThat
import io.element.android.appconfig.MatchaConfig
import io.element.android.features.matcha.impl.protocol.TaskStatus
import io.element.android.features.matcha.impl.protocol.ThreadMetadataProtocol
import io.element.android.features.matcha.impl.store.A_BOARD_ROOT
import io.element.android.features.matcha.impl.store.aCardItem
import io.element.android.features.matcha.impl.store.aMatchaRoomStore
import io.element.android.features.matcha.impl.store.aThreadListItem
import io.element.android.features.matcha.impl.store.aThreadPatchItem
import io.element.android.features.matcha.impl.store.settle
import io.element.android.libraries.matrix.api.room.threads.ThreadListPaginationStatus
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultMatchaRoomControllerTest {
    private val boardRoot = A_BOARD_ROOT

    @Test
    fun `the chat has a chip bar once an active thread is named`() = runTest {
        assertThat(hasChatBar(threads = listOf("\$t1"), items = listOf(aThreadPatchItem(1, "\$t1", name = "Launch plan")))).isTrue()
    }

    @Test
    fun `a thread named when it was created gives a chip bar, through its hidden first reply`() = runTest {
        val bootstrap = ThreadMetadataProtocol.bootstrapReplyContent("\$t1", "Launch plan")
        val reply = """{"type": "${MatchaConfig.EVENT_THREAD_METADATA}", "event_id": "${'$'}b1", "sender": "@bob:x", """ +
            """"origin_server_ts": 2, "content": $bootstrap}"""
        val store = aMatchaRoomStore(
            scope = backgroundScope,
            threads = listOf(aThreadListItem("\$t1", body = "Launch plan")),
            threadReplies = mapOf("\$t1" to listOf(reply)),
        )
        val controller = DefaultMatchaRoomController(store, backgroundScope)
        store.snapshot.first { it.isLoaded && it.threads.singleOrNull()?.hasHiddenFirstReply == true }
        settle()
        assertThat(controller.hasChatBar.value).isTrue()
    }

    @Test
    fun `a board alone, unnamed threads or an archived named thread give no chip bar`() = runTest {
        assertThat(hasChatBar(boardRoot = boardRoot)).isFalse()
        assertThat(hasChatBar(threads = listOf("\$t1"))).isFalse()
        assertThat(
            hasChatBar(
                threads = listOf("\$t1"),
                items = listOf(aThreadPatchItem(1, "\$t1", name = "Launch plan"), aThreadPatchItem(2, "\$t1", archived = true)),
            )
        ).isFalse()
    }

    @Test
    fun `there is no chip bar and no task count until the store has loaded`() = runTest {
        val store = aMatchaRoomStore(
            scope = backgroundScope,
            boardRoot = boardRoot,
            threads = listOf(aThreadListItem("\$t1")),
            threadsPaginationStatus = ThreadListPaginationStatus.Loading,
            items = listOf(aThreadPatchItem(1, "\$t1", name = "Launch plan")),
        )
        val controller = DefaultMatchaRoomController(store, backgroundScope)
        settle()
        assertThat(store.snapshot.value.isLoaded).isFalse()
        assertThat(controller.hasChatBar.value).isFalse()
        assertThat(controller.openTaskCount.value).isEqualTo(0)
    }

    @Test
    fun `the open task count is the To do and In progress tasks of the board`() = runTest {
        val store = aMatchaRoomStore(
            scope = backgroundScope,
            boardRoot = boardRoot,
            threads = listOf(aThreadListItem("\$t1", timestamp = 1), aThreadListItem("\$t2", timestamp = 2)),
            items = listOf(
                card(1, "c-todo", TaskStatus.Todo),
                card(2, "c-doing", TaskStatus.Doing),
                card(3, "c-done", TaskStatus.Done),
                card(4, "c-archived", TaskStatus.Todo, archived = true),
            ),
        )
        val controller = DefaultMatchaRoomController(store, backgroundScope)
        // Every thread is a task on the board: the two threads (To do) plus the To do and In progress cards.
        assertThat(controller.openTaskCount.first { it > 0 }).isEqualTo(4)
    }

    @Test
    fun `a task hidden from the user is not counted`() = runTest {
        val store = aMatchaRoomStore(
            scope = backgroundScope,
            boardRoot = boardRoot,
            items = listOf(
                card(1, "c-shared", TaskStatus.Todo),
                // Written by someone else, not shared with the chat: only the people it involves see it.
                card(2, "c-private", TaskStatus.Todo, showToRoom = false),
            ),
        )
        val controller = DefaultMatchaRoomController(store, backgroundScope)
        store.snapshot.first { it.isLoaded && it.board != null }
        settle()
        assertThat(controller.openTaskCount.value).isEqualTo(1)
    }

    @Test
    fun `without a board there are no open tasks`() = runTest {
        val store = aMatchaRoomStore(scope = backgroundScope, threads = listOf(aThreadListItem("\$t1")))
        val controller = DefaultMatchaRoomController(store, backgroundScope)
        store.snapshot.first { it.isLoaded }
        settle()
        assertThat(controller.openTaskCount.value).isEqualTo(0)
    }

    private suspend fun TestScope.hasChatBar(
        threads: List<String> = emptyList(),
        items: List<MatrixTimelineItem> = emptyList(),
        boardRoot: String? = null,
    ): Boolean {
        val store = aMatchaRoomStore(scope = backgroundScope, threads = threads.map { aThreadListItem(it) }, items = items, boardRoot = boardRoot)
        val controller = DefaultMatchaRoomController(store, backgroundScope)
        store.snapshot.first { it.isLoaded && (boardRoot == null || it.board != null) }
        settle()
        return controller.hasChatBar.value
    }

    private fun card(index: Int, id: String, status: TaskStatus, archived: Boolean = false, showToRoom: Boolean = true) =
        aCardItem(index, id, "Task $id", status, archived = archived, showToRoom = showToRoom)
}
