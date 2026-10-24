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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Runnable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.coroutines.CoroutineContext

class MatchaRoomStoreParticipantsTest {
    private val boardRoot = A_BOARD_ROOT
    private val thread = "\$thread"
    private val me = A_SESSION_ID.value

    private fun reply(index: Int, sender: String, type: String = "m.room.message", relation: String = "m.thread", unsigned: String = "") =
        """{"type": "$type", "event_id": "${'$'}r$index", "sender": "$sender", "origin_server_ts": $index$unsigned, """ +
            """"content": {"m.relates_to": {"rel_type": "$relation", "event_id": "$thread"}}}"""

    private fun card(index: Int, cardId: String, threadId: String?, sender: String) =
        aCardItem(index, cardId, "Card $index", threadId = threadId, showToRoom = false, sender = sender)

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

    @Test
    fun `a thread's first read pages back through all its replies, to its earliest replier and its hidden first reply`() = runTest {
        val requests = mutableListOf<Pair<String?, Int>>()
        // Newest first: the hidden bootstrap reply is the thread's first, and one person replied only on the oldest page.
        val replies = (250 downTo 1).map { index ->
            when (index) {
                1 -> reply(1, "@bob:x", type = MatchaConfig.EVENT_THREAD_METADATA)
                2 -> reply(2, "@early:x")
                else -> reply(index, "@erin:x")
            }
        }
        val store = aPagedStore(MutableStateFlow(listOf(aThreadListItem(thread, numberOfReplies = 250))), page = pagesOf(requests) { replies })
        settle()
        val snapshot = store.snapshot.value
        assertThat(snapshot.isLoaded).isTrue()
        assertThat(requests).containsExactly(null to 100, "100" to 100, "200" to 100).inOrder()
        assertThat(snapshot.threads.single().participants).containsExactly("@bob:x", "@erin:x", "@early:x")
        assertThat(snapshot.threads.single().hasHiddenFirstReply).isTrue()
        assertThat(snapshot.threads.single().visibleReplies()).isEqualTo(249)
    }

    @Test
    fun `a later read pages until it has the replies counted since, or reaches a reply read before`() = runTest {
        val requests = mutableListOf<Pair<String?, Int>>()
        var replies = (250 downTo 1).map { reply(it, "@erin:x") }
        val threads = MutableStateFlow(listOf(aThreadListItem(thread, numberOfReplies = 250)))
        val store = aPagedStore(threads, page = pagesOf(requests) { replies })
        settle()
        requests.clear()

        // 150 new replies, one person among them only on the second page back.
        replies = (400 downTo 251).map { reply(it, if (it == 260) "@late:x" else "@gina:x") } + replies
        threads.value = listOf(aThreadListItem(thread, numberOfReplies = 400))
        settle()
        assertThat(requests).containsExactly(null to 100, "100" to 50).inOrder()
        assertThat(store.snapshot.value.threads.single().participants).containsExactly("@bob:x", "@erin:x", "@gina:x", "@late:x")

        // A count ahead of the replies served stops at the first page holding a reply read before.
        requests.clear()
        replies = (410 downTo 401).map { reply(it, "@hal:x") } + replies
        threads.value = listOf(aThreadListItem(thread, numberOfReplies = 600))
        settle()
        assertThat(requests).containsExactly(null to 100)
        assertThat(store.snapshot.value.threads.single().participants).contains("@hal:x")
    }

    @Test
    fun `a read stops at the page cap when the homeserver keeps paging`() = runTest {
        var requests = 0
        val store = aPagedStore(MutableStateFlow(listOf(aThreadListItem(thread)))) { _, _ ->
            requests++
            Result.success(RawEventsPage(listOf(reply(1, "@erin:x")), "again"))
        }
        settle()
        assertThat(requests).isEqualTo(50)
        assertThat(store.snapshot.value.isLoaded).isTrue()
        assertThat(store.snapshot.value.threads.single().participants).containsExactly("@bob:x", "@erin:x")
    }

    @Test
    fun `a failing page keeps the repliers read before and warns, and the next read asks again for what it missed`() = runTest {
        val requests = mutableListOf<Pair<String?, Int>>()
        var replies = (250 downTo 1).map { reply(it, if (it == 1) "@early:x" else "@erin:x") }
        var isOffline = false
        val pages = pagesOf(requests) { replies }
        val threads = MutableStateFlow(listOf(aThreadListItem(thread, numberOfReplies = 250)))
        val store = aPagedStore(threads) { from, limit ->
            if (isOffline && from != null) {
                requests += from to limit
                Result.failure(IllegalStateException("offline"))
            } else {
                pages(from, limit)
            }
        }
        settle()
        requests.clear()

        isOffline = true
        replies = (400 downTo 251).map { reply(it, "@gina:x") } + replies
        threads.value = listOf(aThreadListItem(thread, numberOfReplies = 400))
        settle()
        assertThat(requests).containsExactly(null to 100, "100" to 50).inOrder()
        assertThat(store.snapshot.value.warning).isEqualTo(MatchaWarning.Network)
        assertThat(store.snapshot.value.threads.single().participants).containsExactly("@bob:x", "@erin:x", "@early:x")

        // The failed read doesn't count: the next one asks for every reply since the last successful read.
        isOffline = false
        requests.clear()
        replies = listOf(reply(401, "@hal:x")) + replies
        threads.value = listOf(aThreadListItem(thread, numberOfReplies = 401))
        settle()
        assertThat(requests).containsExactly(null to 100, "100" to 51).inOrder()
        assertThat(store.snapshot.value.threads.single().participants).containsExactly("@bob:x", "@erin:x", "@early:x", "@hal:x", "@gina:x")
    }

    @Test
    fun `a thread whose replies are being read is read again when that read ends, for the replies counted since`() = runTest {
        val requests = mutableListOf<Pair<String?, Int>>()
        var replies = (250 downTo 1).map { reply(it, "@erin:x") }
        val reads = HeldReads(StandardTestDispatcher(testScheduler))
        val threads = MutableStateFlow(listOf(aThreadListItem(thread, numberOfReplies = 250)))
        val store = aPagedStore(threads, computation = reads, page = pagesOf(requests) { replies })
        settle()
        replies = (260 downTo 251).map { reply(it, "@gina:x") } + replies
        threads.value = listOf(aThreadListItem(thread, numberOfReplies = 260))
        settle()
        assertThat(requests).isEmpty()

        reads.release()
        settle()
        // The first read finds all 260; the next asks only for the 10 counted since and stops at the newest reply it had.
        assertThat(requests).containsExactly(null to 100, "100" to 100, "200" to 100, null to 10).inOrder()
        assertThat(store.snapshot.value.isLoaded).isTrue()
        assertThat(store.snapshot.value.threads.single().participants).containsExactly("@bob:x", "@gina:x", "@erin:x")
    }

    /** A store over [threads] whose thread replies are read through [page], given the token to start from and the limit. */
    private fun TestScope.aPagedStore(
        threads: MutableStateFlow<List<ThreadListItem>>,
        computation: CoroutineDispatcher? = null,
        page: (from: String?, limit: Int) -> Result<RawEventsPage>,
    ) = MatchaRoomStore(
        FakeJoinedRoom(
            baseRoom = FakeBaseRoom(),
            threadsListService = FakeThreadsListService(items = threads, paginationStatus = MutableStateFlow(ThreadListPaginationStatus.Idle(false))),
            createTimelineResult = { Result.success(aMatchaTimeline()) },
            fetchRelatedEventsJsonResult = { _, relation, from, limit ->
                if (relation == RawRelationType.Thread) page(from, limit) else Result.success(RawEventsPage(emptyList(), null))
            },
        ),
        backgroundScope,
        computation = computation,
    )

    /** Pages [replies], newest first, as the homeserver does, each next token the offset of the next page; [requests] records each page asked for. */
    private fun pagesOf(requests: MutableList<Pair<String?, Int>>, replies: () -> List<String>): (String?, Int) -> Result<RawEventsPage> = { from, limit ->
        requests += from to limit
        val all = replies()
        val offset = from?.toInt() ?: 0
        val end = minOf(offset + limit, all.size)
        Result.success(RawEventsPage(all.subList(offset, end), end.toString().takeIf { end < all.size }))
    }

    private fun ThreadListItem.withLatestReply(sender: String, count: Long) =
        copy(latestEvent = rootEvent.copy(eventId = EventId("\$latest"), senderId = UserId(sender)), numberOfReplies = count)
}

/** A computation dispatcher that holds the store's related-event reads until [release]; it builds snapshots on [inner] straight away. */
private class HeldReads(private val inner: CoroutineDispatcher) : CoroutineDispatcher() {
    private val held = mutableListOf<Pair<CoroutineContext, Runnable>>()
    private var isHolding = true

    override fun dispatch(context: CoroutineContext, block: Runnable) {
        if (isHolding) held += context to block else inner.dispatch(context, block)
    }

    override fun limitedParallelism(parallelism: Int, name: String?): CoroutineDispatcher = inner

    fun release() {
        isHolding = false
        held.forEach { (context, block) -> inner.dispatch(context, block) }
        held.clear()
    }
}
