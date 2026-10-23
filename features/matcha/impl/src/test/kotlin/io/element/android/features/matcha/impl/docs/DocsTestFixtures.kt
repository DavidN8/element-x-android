/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs

import io.element.android.appconfig.MatchaConfig
import io.element.android.features.matcha.impl.docs.transport.RoomDocumentSession
import io.element.android.features.matcha.impl.store.MatchaRoomStore
import io.element.android.features.networkmonitor.test.FakeNetworkMonitor
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.UniqueId
import io.element.android.libraries.matrix.api.room.RoomInfo
import io.element.android.libraries.matrix.api.room.StateEventType
import io.element.android.libraries.matrix.api.room.threads.ThreadListPaginationStatus
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.api.timeline.item.event.TimelineItemDebugInfoProvider
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.room.FakeBaseRoom
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.room.aRoomInfo
import io.element.android.libraries.matrix.test.room.powerlevels.FakeRoomPermissions
import io.element.android.libraries.matrix.test.room.threads.FakeThreadsListService
import io.element.android.libraries.matrix.test.timeline.FakeTimeline
import io.element.android.libraries.matrix.test.timeline.aTimelineItemDebugInfo
import io.element.android.libraries.matrix.test.timeline.anEventTimelineItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope

const val ANCHOR = "\$create"
const val BOB = "@bob:x"

/** The store and sessions run in the background scope, which advanceUntilIdle() does not drive. */
fun TestScope.settle(millis: Long = 1_000) {
    testScheduler.advanceTimeBy(millis)
    testScheduler.runCurrent()
}

private var itemCounter = 0

fun anItem(json: String, eventId: String): MatrixTimelineItem.Event = MatrixTimelineItem.Event(
    uniqueId = UniqueId("item-${itemCounter++}"),
    event = anEventTimelineItem(
        eventId = EventId(eventId),
        debugInfoProvider = TimelineItemDebugInfoProvider { aTimelineItemDebugInfo(originalJson = json) },
    ),
)

fun aCreateItem(eventId: String = ANCHOR) =
    anItem("""{"type": "m.room.create", "state_key": "", "event_id": "$eventId", "sender": "@admin:x", "origin_server_ts": 1, "content": {}}""", eventId)

fun aFrameItem(eventId: String, content: String, sender: String = BOB, type: String = MatchaConfig.EVENT_DOCS_UPDATE) =
    anItem("""{"type": "$type", "event_id": "$eventId", "sender": "$sender", "origin_server_ts": 2, "content": $content}""", eventId)

/** The frames of one update, as timeline items with the ids "$prefix-0", "$prefix-1" and so on. */
fun frameItems(
    prefix: String,
    update: ByteArray,
    updateId: String,
    documentId: String = DocsProtocol.MAIN_DOCUMENT_ID,
    sender: String = BOB,
) = DocsProtocol.frames(update, ANCHOR, updateId, documentId).mapIndexed { index, frame -> aFrameItem("$prefix-$index", frame, sender) }

/** A space room with a filtered timeline holding [items], whose sent events are recorded in [sent]. */
class DocsRoomFixture(
    items: List<MatrixTimelineItem>,
    roomInfo: RoomInfo = aRoomInfo(isEncrypted = true, isSpace = true),
    canSend: Boolean = true,
    canSendState: Boolean = false,
    roomId: RoomId = A_ROOM_ID,
    hasMoreHistory: Boolean = false,
    paginateResult: () -> Result<Boolean> = { Result.success(true) },
    var sendResult: (String, String) -> Result<EventId> = { _, _ -> Result.success(EventId("\$sent")) },
    stateEvents: Map<StateEventType, String> = emptyMap(),
    sendStateResult: (String, String, String) -> Result<EventId> = { _, _, _ -> Result.success(EventId("\$state")) },
    enableEncryptionResult: () -> Result<Unit> = { Result.success(Unit) },
) {
    val timelineItems = MutableStateFlow(items)
    val sent = mutableListOf<Pair<String, String>>()
    val sentState = mutableListOf<Triple<String, String, String>>()
    val stateEvents = stateEvents.toMutableMap()
    val engine = FakeYjsEngine()
    val networkMonitor = FakeNetworkMonitor()
    private var sentCount = 0
    val timeline = FakeTimeline(
        timelineItems = timelineItems,
        backwardPaginationStatus = MutableStateFlow(Timeline.PaginationStatus(isPaginating = false, hasMoreToLoad = hasMoreHistory)),
    ).apply { paginateLambda = { paginateResult() } }
    val baseRoom = FakeBaseRoom(
        roomId = roomId,
        initialRoomInfo = roomInfo,
        roomPermissions = FakeRoomPermissions(canSendMessage = { canSend }, canSendState = { canSendState }),
    )
    val room = FakeJoinedRoom(
        baseRoom = baseRoom,
        threadsListService = FakeThreadsListService(paginationStatus = MutableStateFlow(ThreadListPaginationStatus.Idle(hasMoreToLoad = false))),
        createTimelineResult = { Result.success(timeline) },
        sendRawEventResult = { type, content ->
            sendResult(type, content).onSuccess { sent += type to content }.map { EventId("\$sent-${sentCount++}") }
        },
        sendRawStateEventResult = { type, key, content ->
            sendStateResult(type, key, content).onSuccess { sentState += Triple(type, key, content) }
        },
        getStateEventsJsonResult = { type -> Result.success(this.stateEvents[type]?.let { mapOf("" to it) }.orEmpty()) },
        enableEncryptionResult = enableEncryptionResult,
    )

    fun store(scope: CoroutineScope) = MatchaRoomStore(room, scope, setOf(DocsProtocol.EVENT_ROOM_CREATE, DocsProtocol.EVENT_ROOM_ENCRYPTED))

    fun session(
        scope: CoroutineScope,
        store: MatchaRoomStore = store(scope),
        documentId: String = DocsProtocol.MAIN_DOCUMENT_ID,
        isReadOnly: Boolean = false,
    ): RoomDocumentSession {
        var updateCount = 0
        return RoomDocumentSession(
            documentId = documentId,
            room = room,
            store = store,
            engine = engine,
            networkMonitor = networkMonitor,
            parentScope = scope,
            isReadOnly = isReadOnly,
            newUpdateId = { "$documentId-update-${updateCount++}" },
        )
    }

    companion object {
        val OWN_SENDER = A_SESSION_ID.value
    }
}
