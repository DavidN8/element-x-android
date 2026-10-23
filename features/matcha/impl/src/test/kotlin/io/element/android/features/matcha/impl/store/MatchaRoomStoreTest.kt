/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.store

import com.google.common.truth.Truth.assertThat
import io.element.android.appconfig.MatchaConfig
import io.element.android.features.matcha.impl.protocol.KanbanProtocol
import io.element.android.features.matcha.impl.protocol.TaskStatus
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.UniqueId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.CreateTimelineParams
import io.element.android.libraries.matrix.api.room.StateEventType
import io.element.android.libraries.matrix.api.room.threads.ThreadListItem
import io.element.android.libraries.matrix.api.room.threads.ThreadListItemEvent
import io.element.android.libraries.matrix.api.room.threads.ThreadListPaginationStatus
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.api.timeline.item.event.EventContent
import io.element.android.libraries.matrix.api.timeline.item.event.ProfileDetails
import io.element.android.libraries.matrix.api.timeline.item.event.TimelineItemDebugInfoProvider
import io.element.android.libraries.matrix.api.timeline.item.event.UnableToDecryptContent
import io.element.android.libraries.matrix.api.timeline.item.event.UtdCause
import io.element.android.libraries.matrix.test.room.FakeBaseRoom
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.room.powerlevels.FakeRoomPermissions
import io.element.android.libraries.matrix.test.room.threads.FakeThreadsListService
import io.element.android.libraries.matrix.test.timeline.FakeTimeline
import io.element.android.libraries.matrix.test.timeline.aMessageContent
import io.element.android.libraries.matrix.test.timeline.aTimelineItemDebugInfo
import io.element.android.libraries.matrix.test.timeline.anEventTimelineItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MatchaRoomStoreTest {
    /** The store runs in the background scope, which advanceUntilIdle() does not drive. */
    private fun TestScope.settle() {
        testScheduler.advanceTimeBy(1_000)
        testScheduler.runCurrent()
    }

    private val boardRoot = "\$board"
    private val thread = "\$thread-1"

    private fun item(index: Int, json: String) = MatrixTimelineItem.Event(
        uniqueId = UniqueId("item-$index"),
        event = anEventTimelineItem(
            eventId = EventId("\$e$index"),
            debugInfoProvider = TimelineItemDebugInfoProvider { aTimelineItemDebugInfo(originalJson = json) },
        ),
    )

    private fun event(index: Int, type: String, content: String) =
        item(index, """{"type": "$type", "event_id": "${'$'}e$index", "sender": "@bob:x", "origin_server_ts": $index, "content": $content}""")

    private fun threadItem(id: String, body: String, content: EventContent = aMessageContent(body = body)) = ThreadListItem(
        rootEvent = ThreadListItemEvent(
            eventId = EventId(id),
            senderId = UserId("@bob:x"),
            senderProfile = ProfileDetails.Unavailable,
            isOwn = false,
            content = content,
            timestamp = 10,
        ),
        latestEvent = null,
        numberOfReplies = 3,
    )

    private fun aStore(
        items: List<MatrixTimelineItem>,
        threads: List<ThreadListItem> = emptyList(),
        sendRawEventResult: (String, String) -> Result<EventId> = { _, _ -> Result.success(EventId("\$sent")) },
        createTimelineResult: (() -> Result<Timeline>)? = null,
        boardPointerResult: () -> Result<Map<String, String>> = { Result.success(mapOf("" to """{"version": 1, "event_id": "$boardRoot"}""")) },
        scope: kotlinx.coroutines.CoroutineScope,
    ): Pair<MatchaRoomStore, MutableStateFlow<List<MatrixTimelineItem>>> {
        val timelineItems = MutableStateFlow(items)
        val timeline = FakeTimeline(
            timelineItems = timelineItems,
            backwardPaginationStatus = MutableStateFlow(Timeline.PaginationStatus(isPaginating = false, hasMoreToLoad = false)),
        )
        var requestedTypes: Set<String>? = null
        val room = FakeJoinedRoom(
            baseRoom = FakeBaseRoom(roomPermissions = FakeRoomPermissions(canSendMessage = { true }, canSendState = { true }, canInvite = true)),
            threadsListService = FakeThreadsListService(
                items = MutableStateFlow(threads),
                paginationStatus = MutableStateFlow(ThreadListPaginationStatus.Idle(hasMoreToLoad = false)),
            ),
            createTimelineResult = { params ->
                requestedTypes = (params as CreateTimelineParams.OnlyEventTypes).types
                createTimelineResult?.invoke() ?: Result.success(timeline)
            },
            sendRawEventResult = sendRawEventResult,
            getStateEventsJsonResult = { type ->
                if (type == StateEventType.Custom(MatchaConfig.STATE_KANBAN)) boardPointerResult() else Result.success(emptyMap())
            },
        )
        val store = MatchaRoomStore(room, scope)
        return store to timelineItems
    }

    @Test
    fun `threads, their shared names and the board are read from the room`() = runTest {
        val (store, _) = aStore(
            scope = backgroundScope,
            items = listOf(
                event(1, MatchaConfig.EVENT_KANBAN_CARD, KanbanProtocol.cardContent(aCard("c-standalone", "Standalone", null), boardRoot)),
                event(
                    2,
                    MatchaConfig.EVENT_KANBAN_CARD,
                    KanbanProtocol.cardContent(aCard(KanbanProtocol.threadCardId(thread), "Old title", thread), boardRoot)
                ),
                event(
                    3,
                    MatchaConfig.EVENT_THREAD_METADATA,
                    """{"version": 1, "name": "Renamed", "m.relates_to": {"rel_type": "m.reference", "event_id": "$thread"}}"""
                ),
            ),
            threads = listOf(threadItem(thread, "Original body")),
        )
        settle()
        val snapshot = store.snapshot.value
        assertThat(snapshot.isLoaded).isTrue()
        assertThat(snapshot.threads.map { it.metadata.name }).containsExactly("Renamed")
        assertThat(snapshot.board?.rootId).isEqualTo(boardRoot)
        assertThat(snapshot.board?.cards?.map { it.title }).containsExactly("Standalone", "Renamed").inOrder()
        assertThat(snapshot.board?.cards?.last()?.status).isEqualTo(TaskStatus.Doing)
        assertThat(snapshot.permissions.canCreateBoard).isTrue()
    }

    @Test
    fun `a thread whose root cannot be decrypted is flagged and keeps its root timestamp`() = runTest {
        val (store, _) = aStore(
            scope = backgroundScope,
            items = emptyList(),
            threads = listOf(threadItem(thread, "", content = UnableToDecryptContent(UnableToDecryptContent.Data.Unknown, threadInfo = null))),
        )
        settle()
        val matchaThread = store.snapshot.value.threads.single()
        assertThat(matchaThread.isRootUnreadable).isTrue()
        assertThat(matchaThread.rootTimestamp).isEqualTo(10)
        assertThat(matchaThread.metadata.name).isEqualTo(thread)
    }

    @Test
    fun `a sent event is shown before its remote echo and not duplicated after`() = runTest {
        val (store, timelineItems) = aStore(scope = backgroundScope, items = emptyList())
        settle()
        val content = """{"version": 1, "archived": true, "m.relates_to": {"rel_type": "m.reference", "event_id": "$thread"}}"""
        assertThat(store.send(MatchaConfig.EVENT_THREAD_METADATA, content).getOrNull()).isEqualTo(EventId("\$sent"))
        settle()
        assertThat(store.snapshot.value.events.map { it.eventId }).containsExactly("\$sent")
        timelineItems.value = listOf(
            item(
                9,
                """{"type": "${MatchaConfig.EVENT_THREAD_METADATA}", "event_id": "${'$'}sent", "sender": "@me:x", "origin_server_ts": 9, "content": $content}"""
            ),
        )
        settle()
        assertThat(store.snapshot.value.events.map { it.eventId }).containsExactly("\$sent")
    }

    @Test
    fun `a sent message of a type the store does not read is not echoed`() = runTest {
        val (store, _) = aStore(scope = backgroundScope, items = emptyList())
        settle()
        assertThat(store.send("m.room.message", """{"msgtype": "m.text", "body": "!remind 7200s Call"}""").isSuccess).isTrue()
        settle()
        assertThat(store.snapshot.value.events).isEmpty()
    }

    @Test
    fun `a failed send leaves nothing behind`() = runTest {
        val (store, _) = aStore(scope = backgroundScope, items = emptyList(), sendRawEventResult = { _, _ -> Result.failure(IllegalStateException("offline")) })
        settle()
        assertThat(store.send(MatchaConfig.EVENT_KANBAN_CARD, "{}").isFailure).isTrue()
        settle()
        assertThat(store.snapshot.value.events).isEmpty()
    }

    @Test
    fun `undecryptable board and thread events make the board unreadable, with the warning from their cause`() = runTest {
        val unrelated = utd(1, target = "\$poll", cause = UtdCause.Unknown)
        val historical = utd(2, target = boardRoot, cause = UtdCause.HistoricalMessageAndBackupIsDisabled)
        val (store, timelineItems) = aStore(scope = backgroundScope, items = listOf(unrelated), threads = listOf(threadItem(thread, "Body")))
        settle()
        assertThat(store.snapshot.value.unreadable).isFalse()
        assertThat(store.snapshot.value.warning).isNull()
        assertThat(store.snapshot.value.events.map { it.type }).containsExactly("m.room.encrypted")

        timelineItems.value = listOf(unrelated, historical)
        settle()
        assertThat(store.snapshot.value.unreadable).isTrue()
        assertThat(store.snapshot.value.warning).isEqualTo(MatchaWarning.HistoryUnavailable)

        timelineItems.value = listOf(unrelated, historical, utd(3, target = thread, cause = UtdCause.UnknownDevice))
        settle()
        assertThat(store.snapshot.value.warning).isEqualTo(MatchaWarning.Undecryptable)
    }

    @Test
    fun `a thread root from before joining warns that it can't be read`() = runTest {
        val content = UnableToDecryptContent(UnableToDecryptContent.Data.MegolmV1AesSha2("session", UtdCause.SentBeforeWeJoined), threadInfo = null)
        val (store, _) = aStore(scope = backgroundScope, items = emptyList(), threads = listOf(threadItem(thread, "", content = content)))
        settle()
        assertThat(store.snapshot.value.unreadable).isFalse()
        assertThat(store.snapshot.value.warning).isEqualTo(MatchaWarning.NoAccess)
    }

    @Test
    fun `the stored pointer is re-read on demand, and a failed read is never taken for an empty one`() = runTest {
        var pointer: Result<Map<String, String>> = Result.failure(IllegalStateException("offline"))
        val (store, _) = aStore(scope = backgroundScope, items = emptyList(), boardPointerResult = { pointer })
        settle()
        assertThat(store.readBoardPointer().isFailure).isTrue()

        pointer = Result.success(emptyMap())
        assertThat(store.readBoardPointer().getOrThrow()).isNull()

        pointer = Result.failure(IllegalStateException("offline"))
        assertThat(store.readBoardPointer().isFailure).isTrue()

        pointer = Result.success(mapOf("" to """{"version": 1, "event_id": "$boardRoot"}"""))
        assertThat(store.readBoardPointer().getOrThrow()).isEqualTo(boardRoot)
        settle()
        assertThat(store.snapshot.value.board?.rootId).isEqualTo(boardRoot)
    }

    @Test
    fun `a missing board is only resolved once the history is read back to the start`() = runTest {
        // Ten pages are read up front; the pointer is in the twelfth.
        val olderPages = List(11) { emptyList<MatrixTimelineItem>() } + listOf(listOf(aBoardPointerItem(1, boardRoot)))
        val store = aMatchaRoomStore(backgroundScope, olderPages = olderPages)
        settle()
        assertThat(store.snapshot.value.isLoaded).isTrue()
        assertThat(store.snapshot.value.board).isNull()
        assertThat(store.snapshot.value.isBoardResolved).isFalse()

        assertThat(store.loadHistoryUntil { it.board != null }.isSuccess).isTrue()
        settle()
        assertThat(store.snapshot.value.board?.rootId).isEqualTo(boardRoot)
        assertThat(store.snapshot.value.isBoardResolved).isTrue()

        val empty = aMatchaRoomStore(backgroundScope, olderPages = List(12) { emptyList() })
        settle()
        assertThat(empty.loadHistoryUntil { it.board != null }.isSuccess).isTrue()
        assertThat(empty.snapshot.value.board).isNull()
        assertThat(empty.snapshot.value.isBoardResolved).isTrue()
        assertThat(empty.snapshot.value.hasMoreHistory).isFalse()
    }

    @Test
    fun `reading the history stops at a page that can't be read`() = runTest {
        var fail = false
        val store = aMatchaRoomStore(backgroundScope, olderPages = List(12) { emptyList() }, paginateResult = {
            if (fail) Result.failure(IllegalStateException("offline")) else Result.success(Unit)
        })
        settle()
        fail = true
        assertThat(store.loadHistoryUntil { false }.isFailure).isTrue()
        settle()
        assertThat(store.snapshot.value.isBoardResolved).isFalse()
        assertThat(store.snapshot.value.warning).isEqualTo(MatchaWarning.Network)
    }

    @Test
    fun `a board root that no pointer refers to is not a board, but the board cards refer to is`() = runTest {
        val strayRoot = aMatchaEventItem(1, MatchaConfig.EVENT_KANBAN_BOARD, """{"version": 1}""")
        val stray = aMatchaRoomStore(backgroundScope, items = listOf(strayRoot))
        settle()
        assertThat(stray.snapshot.value.board).isNull()

        val card = aMatchaEventItem(2, MatchaConfig.EVENT_KANBAN_CARD, KanbanProtocol.cardContent(aCard("c-1", "Card", null), boardRoot))
        val withCards = aMatchaRoomStore(backgroundScope, items = listOf(strayRoot, card))
        settle()
        assertThat(withCards.snapshot.value.board?.rootId).isEqualTo(boardRoot)
    }

    @Test
    fun `every task has its own id, even a card named like a list row or unlinked from its thread`() = runTest {
        val (store, _) = aStore(
            scope = backgroundScope,
            items = listOf(
                event(1, MatchaConfig.EVENT_KANBAN_CARD, KanbanProtocol.cardContent(aCard("archived", "Named like a row", null), boardRoot)),
                // The newest revision of the thread's card no longer links it.
                event(2, MatchaConfig.EVENT_KANBAN_CARD, KanbanProtocol.cardContent(aCard(KanbanProtocol.threadCardId(thread), "Unlinked", null), boardRoot)),
            ),
            threads = listOf(threadItem(thread, "Thread")),
        )
        settle()
        val ids = store.snapshot.value.board!!.cards.map { it.id }
        assertThat(ids).containsExactly(KanbanProtocol.threadCardId(thread), "archived")
    }

    @Test
    fun `a timeline that could not be opened is reported and can be retried`() = runTest {
        var result: Result<Timeline> = Result.failure(IllegalStateException("offline"))
        val timeline = FakeTimeline(
            timelineItems = MutableStateFlow(emptyList()),
            backwardPaginationStatus = MutableStateFlow(Timeline.PaginationStatus(isPaginating = false, hasMoreToLoad = false)),
        )
        val (store, _) = aStore(scope = backgroundScope, items = emptyList(), createTimelineResult = { result })
        settle()
        assertThat(store.snapshot.value.loadFailed).isTrue()

        result = Result.success(timeline)
        store.retryLoad()
        settle()
        assertThat(store.snapshot.value.loadFailed).isFalse()
        assertThat(store.snapshot.value.isLoaded).isTrue()
    }

    @Test
    fun `a failed history read warns about the network`() = runTest {
        val timeline = FakeTimeline(
            timelineItems = MutableStateFlow(emptyList()),
            backwardPaginationStatus = MutableStateFlow(Timeline.PaginationStatus(isPaginating = false, hasMoreToLoad = true)),
        ).apply { paginateLambda = { Result.failure(IllegalStateException("offline")) } }
        val (store, _) = aStore(scope = backgroundScope, items = emptyList(), createTimelineResult = { Result.success(timeline) })
        settle()
        assertThat(store.snapshot.value.warning).isEqualTo(MatchaWarning.Network)
    }

    /** An undecryptable event whose clear relation refers to [target]. */
    private fun utd(index: Int, target: String, cause: UtdCause) = MatrixTimelineItem.Event(
        uniqueId = UniqueId("utd-$index"),
        event = anEventTimelineItem(
            eventId = EventId("\$u$index"),
            content = UnableToDecryptContent(UnableToDecryptContent.Data.MegolmV1AesSha2("session", cause), threadInfo = null),
            debugInfoProvider = TimelineItemDebugInfoProvider {
                aTimelineItemDebugInfo(
                    originalJson = """{"type": "m.room.encrypted", "event_id": "${'$'}u$index", "sender": "@bob:x", "origin_server_ts": $index,
                        "content": {"algorithm": "m.megolm.v1.aes-sha2", "ciphertext": "x",
                        "m.relates_to": {"rel_type": "m.reference", "event_id": "$target"}}}""".trimIndent()
                )
            },
        ),
    )

    private fun aCard(id: String, title: String, threadId: String?) = io.element.android.features.matcha.impl.protocol.CardRevision(
        cardId = id,
        title = title,
        description = "",
        status = if (threadId != null) TaskStatus.Doing else TaskStatus.Todo,
        archived = false,
        threadId = threadId,
        dueAt = null,
        reminderRequested = null,
        showToRoom = true,
    )
}
