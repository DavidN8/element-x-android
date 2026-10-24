/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.store

import com.google.common.truth.Truth.assertThat
import io.element.android.appconfig.MatchaConfig
import io.element.android.features.matcha.impl.protocol.CardRevision
import io.element.android.features.matcha.impl.protocol.KanbanProtocol
import io.element.android.features.matcha.impl.protocol.TaskStatus
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.RawEventsPage
import io.element.android.libraries.matrix.api.room.RawRelationType
import io.element.android.libraries.matrix.api.room.threads.ThreadListItem
import io.element.android.libraries.matrix.api.room.threads.ThreadListPaginationStatus
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.room.FakeBaseRoom
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.room.aRoomInfo
import io.element.android.libraries.matrix.test.room.threads.FakeThreadsListService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MatchaRoomStoreParticipantsTest {
    private val boardRoot = "\$board"
    private val thread = "\$thread"
    private val me = A_SESSION_ID.value

    private fun TestScope.settle() {
        testScheduler.advanceTimeBy(1_000)
        testScheduler.runCurrent()
    }

    private fun reply(index: Int, sender: String, type: String = "m.room.message", relation: String = "m.thread", unsigned: String = "") =
        """{"type": "$type", "event_id": "${'$'}r$index", "sender": "$sender", "origin_server_ts": $index$unsigned, """ +
            """"content": {"m.relates_to": {"rel_type": "$relation", "event_id": "$thread"}}}"""

    private fun card(index: Int, cardId: String, threadId: String?, sender: String) = aMatchaEventItem(
        index,
        MatchaConfig.EVENT_KANBAN_CARD,
        KanbanProtocol.cardContent(CardRevision(cardId, "Card $index", "", TaskStatus.Todo, false, threadId, null, null, false), boardRoot),
        sender = sender,
    )

    @Test
    fun `a thread involves its root sender and its repliers, and an older card the authors of its revisions`() = runTest {
        val room = aMatchaJoinedRoom(
            roomCoroutineScope = backgroundScope,
            boardRoot = boardRoot,
            threads = listOf(aThreadListItem(thread, senderId = UserId("@bob:x"))),
            items = listOf(
                card(1, KanbanProtocol.threadCardId(thread), thread, sender = "@writer:x"),
                card(2, "legacy", threadId = null, sender = "@carol:x"),
                card(3, "legacy", threadId = null, sender = "@dan:x"),
            ),
            threadReplies = mapOf(
                thread to listOf(
                    reply(1, "@erin:x"),
                    // An encrypted reply's relation and sender stay readable.
                    reply(2, "@frank:x", type = "m.room.encrypted"),
                    reply(3, "@redacted:x", unsigned = """, "unsigned": {"redacted_because": {}}"""),
                    reply(4, "@reactor:x", relation = "m.annotation"),
                ),
            ),
        )
        val store = MatchaRoomStore(room, backgroundScope)
        settle()
        val snapshot = store.snapshot.value
        assertThat(snapshot.isLoaded).isTrue()
        assertThat(snapshot.threads.single().rootSenderId).isEqualTo("@bob:x")
        assertThat(snapshot.threads.single().participants).containsExactly("@bob:x", "@erin:x", "@frank:x").inOrder()
        val cards = snapshot.board!!.cards.associateBy { it.id }
        // A thread's task is the thread's people, not whoever wrote its card.
        assertThat(cards.getValue(KanbanProtocol.threadCardId(thread)).participants).containsExactly("@bob:x", "@erin:x", "@frank:x")
        assertThat(cards.getValue("legacy").participants).containsExactly("@carol:x", "@dan:x")
        assertThat(snapshot.isDirect).isFalse()
    }

    @Test
    fun `in a direct chat the user and the other members are in every task`() = runTest {
        val store = aMatchaRoomStore(
            backgroundScope,
            boardRoot = boardRoot,
            items = listOf(card(1, "legacy", threadId = null, sender = "@carol:x")),
            roomInfo = aRoomInfo(isDirect = true, heroes = listOf(MatrixUser(UserId("@bob:x")))),
        )
        settle()
        val snapshot = store.snapshot.value
        assertThat(snapshot.isDirect).isTrue()
        assertThat(snapshot.board!!.cards.single().participants).containsExactly("@carol:x", me, "@bob:x")
    }

    @Test
    fun `a thread's replies are read again when it gets more, only the new ones, without holding up the board`() = runTest {
        val threads = MutableStateFlow(listOf(aThreadListItem(thread)))
        var replies = listOf(reply(1, "@erin:x"))
        var reads = 0
        val limits = mutableListOf<Int>()
        val store = MatchaRoomStore(
            FakeJoinedRoom(
                baseRoom = FakeBaseRoom(),
                threadsListService = FakeThreadsListService(items = threads, paginationStatus = MutableStateFlow(ThreadListPaginationStatus.Idle(false))),
                createTimelineResult = { Result.success(aMatchaTimeline()) },
                fetchRelatedEventsJsonResult = { _, relation, _, limit ->
                    if (relation == RawRelationType.Thread) {
                        reads++
                        limits += limit
                    }
                    Result.success(RawEventsPage(if (relation == RawRelationType.Thread) replies else emptyList(), null))
                },
            ),
            backgroundScope,
        )
        settle()
        assertThat(store.snapshot.value.threads.single().participants).containsExactly("@bob:x", "@erin:x")
        assertThat(reads).isEqualTo(1)

        replies = replies + reply(2, me)
        // The latest reply is known from the thread list straight away; the others once the replies are read again.
        threads.value = listOf(aThreadListItem(thread).withLatestReply(sender = "@gina:x", count = 3))
        settle()
        assertThat(reads).isEqualTo(2)
        assertThat(store.snapshot.value.isLoaded).isTrue()
        assertThat(store.snapshot.value.threads.single().participants).containsExactly("@bob:x", "@gina:x", "@erin:x", me)
        // The first read takes a full page; the next only the two replies counted since, added to what was read.
        assertThat(limits).containsExactly(100, 2).inOrder()

        // The same count isn't read again.
        threads.value = listOf(aThreadListItem(thread, timestamp = 11).withLatestReply(sender = "@gina:x", count = 3))
        settle()
        assertThat(reads).isEqualTo(2)

        // A redaction lowers the count; the newest reply is still read.
        threads.value = listOf(aThreadListItem(thread, timestamp = 12).withLatestReply(sender = "@gina:x", count = 2))
        settle()
        assertThat(limits).containsExactly(100, 2, 1).inOrder()
        assertThat(store.snapshot.value.threads.single().participants).containsExactly("@bob:x", "@gina:x", "@erin:x", me)
    }

    @Test
    fun `the hidden reply this client sent makes the user one of a new thread's participants`() = runTest {
        val store = aMatchaRoomStore(backgroundScope, boardRoot = boardRoot)
        settle()
        store.createNamedThread("Plan")
        settle()
        assertThat(store.snapshot.value.events.single().senderId).isEqualTo(me)
        val card = CardRevision(KanbanProtocol.threadCardId("\$sent"), "Plan", "", TaskStatus.Todo, false, "\$sent", null, null, false)
        store.send(MatchaConfig.EVENT_KANBAN_CARD, KanbanProtocol.cardContent(card, boardRoot))
        settle()
        assertThat(store.snapshot.value.board!!.cards.single().participants).containsExactly(me)
    }

    private fun ThreadListItem.withLatestReply(sender: String, count: Long) =
        copy(latestEvent = rootEvent.copy(eventId = EventId("\$latest"), senderId = UserId(sender)), numberOfReplies = count)
}
