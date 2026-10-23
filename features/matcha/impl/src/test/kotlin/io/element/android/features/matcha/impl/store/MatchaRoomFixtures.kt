/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.store

import io.element.android.appconfig.MatchaConfig
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.UniqueId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.MessageEventType
import io.element.android.libraries.matrix.api.room.StateEventType
import io.element.android.libraries.matrix.api.room.threads.ThreadListItem
import io.element.android.libraries.matrix.api.room.threads.ThreadListItemEvent
import io.element.android.libraries.matrix.api.room.threads.ThreadListPaginationStatus
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.api.timeline.item.event.EventContent
import io.element.android.libraries.matrix.api.timeline.item.event.ProfileDetails
import io.element.android.libraries.matrix.api.timeline.item.event.TimelineItemDebugInfoProvider
import io.element.android.libraries.matrix.test.room.FakeBaseRoom
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.room.powerlevels.FakeRoomPermissions
import io.element.android.libraries.matrix.test.room.threads.FakeThreadsListService
import io.element.android.libraries.matrix.test.timeline.FakeTimeline
import io.element.android.libraries.matrix.test.timeline.aMessageContent
import io.element.android.libraries.matrix.test.timeline.aTimelineItemDebugInfo
import io.element.android.libraries.matrix.test.timeline.anEventTimelineItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

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
    sendRawEventResult: (String, String) -> Result<EventId> = { _, _ -> Result.success(EventId("\$sent")) },
): MatchaRoomStore {
    val timeline = FakeTimeline(
        timelineItems = MutableStateFlow(items),
        backwardPaginationStatus = MutableStateFlow(Timeline.PaginationStatus(isPaginating = false, hasMoreToLoad = false)),
    )
    val room = FakeJoinedRoom(
        baseRoom = FakeBaseRoom(roomPermissions = FakeRoomPermissions(canSendMessage = canSendMessage, canSendState = { canSendState }, canInvite = true)),
        threadsListService = FakeThreadsListService(
            items = MutableStateFlow(threads),
            paginationStatus = MutableStateFlow(threadsPaginationStatus),
        ),
        createTimelineResult = { Result.success(timeline) },
        sendRawEventResult = sendRawEventResult,
        sendRawStateEventResult = sendRawStateEventResult,
        getStateEventsJsonResult = { type ->
            when {
                type != StateEventType.Custom(MatchaConfig.STATE_KANBAN) -> Result.success(emptyMap())
                boardPointerResult != null -> boardPointerResult()
                boardRoot != null -> Result.success(mapOf("" to """{"version": 1, "event_id": "$boardRoot"}"""))
                else -> Result.success(emptyMap())
            }
        },
    )
    return MatchaRoomStore(room, scope)
}

/** A timeline item carrying a Matcha event with the given raw [content]. */
internal fun aMatchaEventItem(index: Int, type: String, content: String) = MatrixTimelineItem.Event(
    uniqueId = UniqueId("item-$index"),
    event = anEventTimelineItem(
        eventId = EventId("\$e$index"),
        debugInfoProvider = TimelineItemDebugInfoProvider {
            aTimelineItemDebugInfo(
                originalJson = """{"type": "$type", "event_id": "${'$'}e$index", "sender": "@bob:x", "origin_server_ts": $index, "content": $content}"""
            )
        },
    ),
)

internal fun aThreadListItem(
    id: String,
    body: String = "Root of $id",
    timestamp: Long = 10,
    content: EventContent? = aMessageContent(body = body),
) = ThreadListItem(
    rootEvent = ThreadListItemEvent(
        eventId = EventId(id),
        senderId = UserId("@bob:x"),
        senderProfile = ProfileDetails.Unavailable,
        isOwn = false,
        content = content,
        timestamp = timestamp,
    ),
    latestEvent = null,
    numberOfReplies = 1,
)
