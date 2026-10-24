/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.store

import io.element.android.appconfig.MatchaConfig
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UniqueId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.MessageEventType
import io.element.android.libraries.matrix.api.room.RawEventsPage
import io.element.android.libraries.matrix.api.room.RawRelationType
import io.element.android.libraries.matrix.api.room.RoomInfo
import io.element.android.libraries.matrix.api.room.StateEventType
import io.element.android.libraries.matrix.api.room.threads.ThreadListItem
import io.element.android.libraries.matrix.api.room.threads.ThreadListItemEvent
import io.element.android.libraries.matrix.api.room.threads.ThreadListPaginationStatus
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.api.timeline.item.event.EventContent
import io.element.android.libraries.matrix.api.timeline.item.event.ProfileDetails
import io.element.android.libraries.matrix.api.timeline.item.event.TimelineItemDebugInfoProvider
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.room.FakeBaseRoom
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.room.aRoomInfo
import io.element.android.libraries.matrix.test.room.powerlevels.FakeRoomPermissions
import io.element.android.libraries.matrix.test.room.threads.FakeThreadsListService
import io.element.android.libraries.matrix.test.timeline.FakeTimeline
import io.element.android.libraries.matrix.test.timeline.aMessageContent
import io.element.android.libraries.matrix.test.timeline.aTimelineItemDebugInfo
import io.element.android.libraries.matrix.test.timeline.anEventTimelineItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope

/** Builds a [MatchaRoomStore] over fakes for presenter tests; everything the store reads is already loaded. */
internal fun aMatchaRoomStore(
    scope: CoroutineScope,
    items: List<MatrixTimelineItem> = emptyList(),
    threads: List<ThreadListItem> = emptyList(),
    threadsPaginationStatus: ThreadListPaginationStatus = ThreadListPaginationStatus.Idle(hasMoreToLoad = false),
    canSendMessage: (MessageEventType) -> Boolean = { true },
    boardRoot: String? = null,
    canSendState: Boolean = true,
    sendRawStateEventResult: (String, String, String) -> Result<EventId> = { _, _, _ -> Result.success(EventId("\$state")) },
    /** Overrides how the board pointer is read; by default it points at [boardRoot]. */
    boardPointerResult: (() -> Result<Map<String, String>>)? = null,
    olderPages: List<List<MatrixTimelineItem>> = emptyList(),
    paginateResult: () -> Result<Unit> = { Result.success(Unit) },
    roomInfo: RoomInfo = aRoomInfo(),
    /** The replies of each thread, as raw event JSON, by thread root id. */
    threadReplies: Map<String, List<String>> = emptyMap(),
    sendRawEventResult: (String, String) -> Result<EventId> = { _, _ -> Result.success(EventId("\$sent")) },
): MatchaRoomStore = MatchaRoomStore(
    aMatchaJoinedRoom(
        items = items,
        olderPages = olderPages,
        paginateResult = paginateResult,
        threads = threads,
        threadsPaginationStatus = threadsPaginationStatus,
        canSendMessage = canSendMessage,
        boardRoot = boardRoot,
        canSendState = canSendState,
        sendRawStateEventResult = sendRawStateEventResult,
        boardPointerResult = boardPointerResult,
        sendRawEventResult = sendRawEventResult,
        roomInfo = roomInfo,
        threadReplies = threadReplies,
    ),
    scope,
)

/** A joined room whose Matcha timeline, thread list and state are already loaded. */
internal fun aMatchaJoinedRoom(
    roomId: RoomId = A_ROOM_ID,
    roomCoroutineScope: CoroutineScope = TestScope(),
    items: List<MatrixTimelineItem> = emptyList(),
    threads: List<ThreadListItem> = emptyList(),
    threadsPaginationStatus: ThreadListPaginationStatus = ThreadListPaginationStatus.Idle(hasMoreToLoad = false),
    canSendMessage: (MessageEventType) -> Boolean = { true },
    boardRoot: String? = null,
    canSendState: Boolean = true,
    sendRawStateEventResult: (String, String, String) -> Result<EventId> = { _, _, _ -> Result.success(EventId("\$state")) },
    /** Overrides how the board pointer is read; by default it points at [boardRoot]. */
    boardPointerResult: (() -> Result<Map<String, String>>)? = null,
    sendRawEventResult: (String, String) -> Result<EventId> = { _, _ -> Result.success(EventId("\$sent")) },
    onCreateTimeline: () -> Unit = {},
    /** History older than [items], one page per backward pagination, newest page first. */
    olderPages: List<List<MatrixTimelineItem>> = emptyList(),
    paginateResult: () -> Result<Unit> = { Result.success(Unit) },
    /** Overrides opening the Matcha timeline; by default it opens on [items]. */
    createTimelineResult: (() -> Result<Timeline>)? = null,
    roomInfo: RoomInfo = aRoomInfo(id = roomId),
    /** The replies of each thread, as raw event JSON, by thread root id. */
    threadReplies: Map<String, List<String>> = emptyMap(),
    userDisplayNameResult: (UserId) -> Result<String?> = { Result.success(null) },
    /** How the homeserver answers a board pointer read; by default the SDK can't ask, like the published SDK. */
    serverPointerResult: () -> Result<String?> = { Result.failure(UnsupportedOperationException("No Matcha SDK patch")) },
): FakeJoinedRoom {
    val timeline = aMatchaTimeline(items, olderPages, paginateResult)
    return FakeJoinedRoom(
        baseRoom = FakeBaseRoom(
            roomId = roomId,
            initialRoomInfo = roomInfo,
            roomPermissions = FakeRoomPermissions(canSendMessage = canSendMessage, canSendState = { canSendState }, canInvite = true),
            userDisplayNameResult = userDisplayNameResult,
        ),
        roomCoroutineScope = roomCoroutineScope,
        threadsListService = FakeThreadsListService(
            items = MutableStateFlow(threads),
            paginationStatus = MutableStateFlow(threadsPaginationStatus),
        ),
        createTimelineResult = {
            onCreateTimeline()
            createTimelineResult?.invoke() ?: Result.success(timeline)
        },
        sendRawEventResult = sendRawEventResult,
        sendRawStateEventResult = sendRawStateEventResult,
        fetchRelatedEventsJsonResult = { eventId, relation, _, _ ->
            Result.success(RawEventsPage(if (relation == RawRelationType.Thread) threadReplies[eventId.value].orEmpty() else emptyList(), null))
        },
        getStateEventsJsonResult = { type ->
            when {
                type != StateEventType.Custom(MatchaConfig.STATE_KANBAN) -> Result.success(emptyMap())
                boardPointerResult != null -> boardPointerResult()
                boardRoot != null -> Result.success(mapOf("" to """{"version": 1, "event_id": "$boardRoot"}"""))
                else -> Result.success(emptyMap())
            }
        },
        fetchStateEventContentJsonResult = { type, key ->
            if (type == MatchaConfig.STATE_KANBAN && key.isEmpty()) serverPointerResult() else Result.success(null)
        },
    )
}

/** A Matcha timeline holding [items] (oldest first), which reads [olderPages] one backward page at a time. */
internal fun aMatchaTimeline(
    items: List<MatrixTimelineItem> = emptyList(),
    olderPages: List<List<MatrixTimelineItem>> = emptyList(),
    paginateResult: () -> Result<Unit> = { Result.success(Unit) },
): FakeTimeline {
    val timelineItems = MutableStateFlow(items)
    val remaining = ArrayDeque(olderPages)
    val status = MutableStateFlow(Timeline.PaginationStatus(isPaginating = false, hasMoreToLoad = remaining.isNotEmpty()))
    return FakeTimeline(timelineItems = timelineItems, backwardPaginationStatus = status).apply {
        paginateLambda = {
            paginateResult().map {
                remaining.removeFirstOrNull()?.let { page -> timelineItems.value = page + timelineItems.value }
                status.value = status.value.copy(hasMoreToLoad = remaining.isNotEmpty())
                remaining.isEmpty()
            }
        }
    }
}

/** A timeline item carrying a Matcha event with the given raw [content], sent at [index] ms; a state event with a [stateKey]. */
internal fun aMatchaEventItem(index: Int, type: String, content: String, stateKey: String? = null, sender: String = "@bob:x") = MatrixTimelineItem.Event(
    uniqueId = UniqueId("item-$index"),
    event = anEventTimelineItem(
        eventId = EventId("\$e$index"),
        debugInfoProvider = TimelineItemDebugInfoProvider {
            val stateKeyField = stateKey?.let { """"state_key": "$it", """ }.orEmpty()
            aTimelineItemDebugInfo(
                originalJson = """{"type": "$type", $stateKeyField"event_id": "${'$'}e$index", "sender": "$sender", "origin_server_ts": $index, """ +
                    """"content": $content}"""
            )
        },
    ),
)

/** A timeline item carrying the board pointer state event, pointing at [rootId]. */
internal fun aBoardPointerItem(index: Int, rootId: String) =
    aMatchaEventItem(index, MatchaConfig.STATE_KANBAN, """{"version": 1, "event_id": "$rootId"}""", stateKey = "")

internal fun aThreadListItem(
    id: String,
    body: String = "Root of $id",
    timestamp: Long = 10,
    content: EventContent? = aMessageContent(body = body),
    senderId: UserId = UserId("@bob:x"),
) = ThreadListItem(
    rootEvent = ThreadListItemEvent(
        eventId = EventId(id),
        senderId = senderId,
        senderProfile = ProfileDetails.Unavailable,
        isOwn = false,
        content = content,
        timestamp = timestamp,
    ),
    latestEvent = null,
    numberOfReplies = 1,
)
