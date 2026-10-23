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
import io.element.android.libraries.matrix.api.timeline.item.event.UnableToDecryptContent
import io.element.android.libraries.matrix.api.timeline.item.event.TimelineItemDebugInfoProvider
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
                Result.success(timeline)
            },
            sendRawEventResult = sendRawEventResult,
            getStateEventsJsonResult = { type ->
                if (type == StateEventType.Custom(MatchaConfig.STATE_KANBAN)) {
                    Result.success(mapOf("" to """{"version": 1, "event_id": "$boardRoot"}"""))
                } else {
                    Result.success(emptyMap())
                }
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
    fun `a failed send leaves nothing behind`() = runTest {
        val (store, _) = aStore(scope = backgroundScope, items = emptyList(), sendRawEventResult = { _, _ -> Result.failure(IllegalStateException("offline")) })
        settle()
        assertThat(store.send(MatchaConfig.EVENT_KANBAN_CARD, "{}").isFailure).isTrue()
        settle()
        assertThat(store.snapshot.value.events).isEmpty()
    }

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
