/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.chatbar

import com.google.common.truth.Truth.assertThat
import io.element.android.appconfig.MatchaConfig
import io.element.android.features.matcha.impl.protocol.CardRevision
import io.element.android.features.matcha.impl.protocol.KanbanProtocol
import io.element.android.features.matcha.impl.protocol.TaskStatus
import io.element.android.features.matcha.impl.store.aMatchaEventItem
import io.element.android.features.matcha.impl.store.aMatchaRoomStore
import io.element.android.features.matcha.impl.store.aThreadListItem
import io.element.android.libraries.matrix.api.room.threads.ThreadListPaginationStatus
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultMatchaRoomControllerTest {
    private val boardRoot = "\$board"

    @Test
    fun `the chat has a chip bar once an active thread is named`() = runTest {
        assertThat(hasChatBar(threads = listOf("\$t1"), items = listOf(namePatch(1, "\$t1", "Launch plan")))).isTrue()
    }

    @Test
    fun `a board alone, unnamed threads or an archived named thread give no chip bar`() = runTest {
        assertThat(hasChatBar(boardRoot = boardRoot)).isFalse()
        assertThat(hasChatBar(threads = listOf("\$t1"))).isFalse()
        assertThat(
            hasChatBar(
                threads = listOf("\$t1"),
                items = listOf(namePatch(1, "\$t1", "Launch plan"), metadataPatch(2, "\$t1", """"archived": true""")),
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
            items = listOf(namePatch(1, "\$t1", "Launch plan")),
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

    /** Runs the work launched in the room scope, which is the test's background scope. */
    private fun TestScope.settle() {
        testScheduler.advanceTimeBy(1_000)
        testScheduler.runCurrent()
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

    private fun namePatch(index: Int, threadId: String, name: String) = metadataPatch(index, threadId, """"name": "$name"""")

    private fun metadataPatch(index: Int, threadId: String, field: String) = aMatchaEventItem(
        index,
        MatchaConfig.EVENT_THREAD_METADATA,
        """{"version": 1, $field, "m.relates_to": {"rel_type": "m.reference", "event_id": "$threadId"}}""",
    )

    private fun card(index: Int, id: String, status: TaskStatus, archived: Boolean = false, showToRoom: Boolean = true) = aMatchaEventItem(
        index,
        MatchaConfig.EVENT_KANBAN_CARD,
        KanbanProtocol.cardContent(
            CardRevision(
                cardId = id,
                title = "Task $id",
                description = "",
                status = status,
                archived = archived,
                threadId = null,
                dueAt = null,
                reminderRequested = null,
                showToRoom = showToRoom,
            ),
            boardRoot,
        ),
    )
}
