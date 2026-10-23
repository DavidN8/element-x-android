/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.store

import io.element.android.appconfig.MatchaConfig
import io.element.android.features.matcha.impl.protocol.CardRevision
import io.element.android.features.matcha.impl.protocol.KanbanProtocol
import io.element.android.features.matcha.impl.protocol.ProjectedCard
import io.element.android.features.matcha.impl.protocol.RawEvent
import io.element.android.features.matcha.impl.protocol.ThreadMetadata
import io.element.android.features.matcha.impl.protocol.ThreadMetadataPatch
import io.element.android.features.matcha.impl.protocol.ThreadMetadataProtocol
import io.element.android.features.matcha.impl.protocol.booleanOrNull
import io.element.android.features.matcha.impl.protocol.referenceTarget
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.ThreadId
import io.element.android.libraries.matrix.api.core.UniqueId
import io.element.android.libraries.matrix.api.room.CreateTimelineParams
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.room.MessageEventType
import io.element.android.libraries.matrix.api.room.StateEventType
import io.element.android.libraries.matrix.api.room.powerlevels.permissionsFlow
import io.element.android.libraries.matrix.api.room.threads.ThreadListItem
import io.element.android.libraries.matrix.api.room.threads.ThreadListPaginationStatus
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.api.timeline.item.event.EventTimelineItem
import io.element.android.libraries.matrix.api.timeline.item.event.MessageContent
import io.element.android.libraries.matrix.api.timeline.item.event.UnableToDecryptContent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

/** A thread of a room with its shared Matcha metadata. */
data class MatchaThread(
    val id: ThreadId,
    val metadata: ThreadMetadata,
    val rootBody: String?,
    val numberOfReplies: Long,
    /** Timestamp of the latest event in the thread, for ordering. */
    val lastActivityTimestamp: Long,
    /** Timestamp of the thread root, 0 when unknown; the chip bar orders threads by it. */
    val rootTimestamp: Long = 0,
    /** True when the root message could not be decrypted on this device, so a name taken from it is missing. */
    val isRootUnreadable: Boolean = false,
)

/** A room's board, if it has one. */
data class MatchaBoard(
    val rootId: String,
    val cards: List<ProjectedCard>,
)

/** What the room-level Matcha features may do, from the room's power levels. */
data class MatchaPermissions(
    val canSendMessages: Boolean = false,
    val canManageThreads: Boolean = false,
    val canWriteCards: Boolean = false,
    val canCreateBoard: Boolean = false,
    val canInvite: Boolean = false,
)

/** Everything the Matcha features know about one room, rebuilt from its timeline, thread list and state. */
data class MatchaRoomSnapshot(
    /** False until the thread list and the first history pages have been read. */
    val isLoaded: Boolean = false,
    /** All threads, active and archived, most recently active first. */
    val threads: List<MatchaThread> = emptyList(),
    val board: MatchaBoard? = null,
    val permissions: MatchaPermissions = MatchaPermissions(),
    val hasMoreHistory: Boolean = true,
    val hasMoreThreads: Boolean = true,
    val isLoadingMore: Boolean = false,
    /** Matcha events in server order, newest first, for features that read them directly (documents). */
    val events: List<RawEvent> = emptyList(),
)

/**
 * Reads the Matcha workspace data of one joined room: a live timeline filtered to the Matcha event types (whose raw JSON
 * is parsed), the SDK's thread list, and the room's stored state. Writes go through [JoinedRoom.sendRawEvent] and are
 * shown immediately, before their remote echo arrives.
 *
 * ponytail: history is read through a filtered live timeline, so the cost grows with the room's history rather than
 * with the number of cards; an SDK method returning the JSON of related events would replace the scan (see the port plan, Path B).
 */
class MatchaRoomStore(
    private val room: JoinedRoom,
    private val coroutineScope: CoroutineScope,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val parsed = HashMap<UniqueId, Pair<EventTimelineItem, RawEvent?>>()
    private val scanEvents = MutableStateFlow<List<RawEvent>>(emptyList())
    private val optimisticEvents = MutableStateFlow<List<RawEvent>>(emptyList())
    private val threadItems = MutableStateFlow<List<ThreadListItem>>(emptyList())
    private val threadsPaginationStatus = MutableStateFlow<ThreadListPaginationStatus>(ThreadListPaginationStatus.Idle(hasMoreToLoad = true))
    private val storedState = MutableStateFlow(StoredState())
    private val pagination = MutableStateFlow(Timeline.PaginationStatus(isPaginating = false, hasMoreToLoad = true))
    private val historyPagesRead = MutableStateFlow(0)
    private val loadingMore = MutableStateFlow(false)
    private val writeMutex = Mutex()
    private var timeline: Timeline? = null

    private val _snapshot = MutableStateFlow(MatchaRoomSnapshot())
    val snapshot: StateFlow<MatchaRoomSnapshot> = _snapshot.asStateFlow()

    private data class StoredState(
        val boardPointer: String? = null,
        val legacyArchive: Map<String, Boolean> = emptyMap(),
    )

    init {
        start()
    }

    @OptIn(FlowPreview::class)
    private fun start() {
        coroutineScope.launch {
            val created = room.createTimeline(CreateTimelineParams.OnlyEventTypes(MatchaConfig.MESSAGE_LIKE_EVENT_TYPES + MatchaConfig.STATE_EVENT_TYPES))
                .onFailure { Timber.w(it, "Could not open the Matcha timeline") }
                .getOrNull() ?: return@launch
            timeline = created
            created.backwardPaginationStatus.onEach { pagination.value = it }.launchIn(this)
            created.timelineItems
                .map { items -> items.mapNotNull(::parse).asReversed() }
                .onEach { scanEvents.value = it }
                .launchIn(this)
            // Read a bounded amount of history up front; older items are loaded on demand.
            readInitialHistory(created)
        }
        room.threadsListService.subscribeToItemUpdates()
            .onEach { threadItems.value = it }
            .launchIn(coroutineScope)
        room.threadsListService.subscribeToPaginationUpdates()
            .onEach { threadsPaginationStatus.value = it }
            .launchIn(coroutineScope)
        coroutineScope.launch { room.threadsListService.paginate().onFailure { Timber.w(it, "Could not read threads") } }
        // Stored state changes arrive with syncs; re-read it whenever something new arrives.
        combine(room.roomInfoFlow, scanEvents) { _, _ -> }
            .debounce(200.milliseconds)
            .onEach { readStoredState() }
            .launchIn(coroutineScope)
        val permissions = room.permissionsFlow(MatchaPermissions()) { perms ->
            val encrypted = room.info().isEncrypted == true
            fun canSend(type: String) = perms.canOwnUserSendMessage(MessageEventType.Other(type)) &&
                (!encrypted || perms.canOwnUserSendMessage(MessageEventType.RoomEncrypted))
            val canWriteCards = canSend(MatchaConfig.EVENT_KANBAN_CARD)
            MatchaPermissions(
                canSendMessages = perms.canOwnUserSendMessage(MessageEventType.RoomMessage),
                canManageThreads = canSend(MatchaConfig.EVENT_THREAD_METADATA),
                canWriteCards = canWriteCards,
                canCreateBoard = canWriteCards && canSend(MatchaConfig.EVENT_KANBAN_BOARD) &&
                    perms.canOwnUserSendState(StateEventType.Custom(MatchaConfig.STATE_KANBAN)),
                canInvite = perms.canOwnUserInvite(),
            )
        }
        combine(
            combine(scanEvents, optimisticEvents) { scan, optimistic -> merge(scan, optimistic) },
            threadItems,
            storedState,
            permissions,
            combine(pagination, threadsPaginationStatus, historyPagesRead, loadingMore) { p, t, pages, loading -> Quad(p, t, pages, loading) },
        ) { events, threads, state, perms, status ->
            buildSnapshot(events, threads, state, perms, status)
        }
            .distinctUntilChanged()
            .onEach { _snapshot.value = it }
            .launchIn(coroutineScope)
    }

    private data class Quad(
        val pagination: Timeline.PaginationStatus,
        val threads: ThreadListPaginationStatus,
        val pagesRead: Int,
        val loadingMore: Boolean,
    )

    private suspend fun readInitialHistory(timeline: Timeline) {
        repeat(INITIAL_HISTORY_PAGES) {
            if (!timeline.backwardPaginationStatus.value.hasMoreToLoad) return
            val result = runCatchingExceptions { timeline.paginate(Timeline.PaginationDirection.BACKWARDS).getOrThrow() }
            if (result.isFailure) {
                Timber.w(result.exceptionOrNull(), "Could not read Matcha history")
                return
            }
            historyPagesRead.update { it + 1 }
        }
    }

    private fun parse(item: MatrixTimelineItem): RawEvent? {
        val eventItem = (item as? MatrixTimelineItem.Event)?.event ?: return null
        if (eventItem.eventId == null) return null
        val cached = parsed[item.uniqueId]
        if (cached != null && cached.first === eventItem) return cached.second
        val event = RawEvent.parse(eventItem.timelineItemDebugInfoProvider().originalJson)
        parsed[item.uniqueId] = eventItem to event
        return event
    }

    private suspend fun readStoredState() {
        val pointer = room.getStateEventsJson(StateEventType.Custom(MatchaConfig.STATE_KANBAN)).getOrNull()
            ?.get("")
            ?.let { runCatching { json.parseToJsonElement(it).jsonObject }.getOrNull() }
            ?.let(KanbanProtocol::parseBoardPointer)
        val legacy = room.getStateEventsJson(StateEventType.Custom(MatchaConfig.STATE_THREAD_ARCHIVE)).getOrNull()
            .orEmpty()
            .mapNotNull { (key, content) ->
                val archived = runCatching { json.parseToJsonElement(content).jsonObject }.getOrNull()?.booleanOrNull("archived")
                archived?.let { key to it }
            }
            .toMap()
        storedState.value = StoredState(boardPointer = pointer, legacyArchive = legacy)
    }

    private fun merge(scan: List<RawEvent>, optimistic: List<RawEvent>): List<RawEvent> {
        if (optimistic.isEmpty()) return scan
        val known = scan.mapNotNullTo(HashSet()) { it.eventId }
        val pending = optimistic.filter { it.eventId !in known }
        if (pending.size != optimistic.size) optimisticEvents.update { list -> list.filter { it.eventId !in known } }
        return pending + scan
    }

    private fun buildSnapshot(
        events: List<RawEvent>,
        threadList: List<ThreadListItem>,
        state: StoredState,
        permissions: MatchaPermissions,
        status: Quad,
    ): MatchaRoomSnapshot {
        val boardRootId = state.boardPointer
            ?: events.firstOrNull { it.type == MatchaConfig.STATE_KANBAN && it.stateKey == "" }
                ?.let { KanbanProtocol.parseBoardPointer(it.content) }
            ?: events.firstOrNull { it.type == MatchaConfig.EVENT_KANBAN_BOARD && !it.isRedacted }?.eventId
            ?: events.firstOrNull { it.type == MatchaConfig.EVENT_KANBAN_CARD }?.content?.referenceTarget()
        val revisions = boardRootId?.let { root -> events.mapNotNull { KanbanProtocol.parseCard(it, root) } }.orEmpty()
        val latestCards = KanbanProtocol.latestRevisions(revisions)

        val patchesByRoot = HashMap<String, MutableList<ThreadMetadataPatch>>()
        for (event in events) {
            if (event.type != MatchaConfig.EVENT_THREAD_METADATA) continue
            val root = event.content.referenceTarget() ?: continue
            ThreadMetadataProtocol.parsePatch(event, root)?.let { patchesByRoot.getOrPut(root) { mutableListOf() } += it }
        }
        val listed = threadList.associateBy { it.threadId.value }
        val threadIds = LinkedHashSet<String>().apply {
            addAll(listed.keys)
            latestCards.mapNotNullTo(this) { it.threadId }
            addAll(state.legacyArchive.filterValues { it }.keys)
        }
        val threads = threadIds.map { id ->
            val item = listed[id]
            val rootBody = (item?.rootEvent?.content as? MessageContent)?.body
                ?: latestCards.firstOrNull { it.threadId == id }?.title
            MatchaThread(
                id = ThreadId(id),
                metadata = ThreadMetadataProtocol.resolve(id, rootBody, patchesByRoot[id].orEmpty(), state.legacyArchive[id]),
                rootBody = rootBody,
                numberOfReplies = item?.numberOfReplies ?: 0,
                lastActivityTimestamp = item?.let { it.latestEvent?.timestamp ?: it.rootEvent.timestamp } ?: 0,
                rootTimestamp = item?.rootEvent?.timestamp ?: 0,
                isRootUnreadable = item?.rootEvent?.content is UnableToDecryptContent,
            )
        }.sortedByDescending { it.lastActivityTimestamp }

        val board = boardRootId?.let { root ->
            MatchaBoard(rootId = root, cards = KanbanProtocol.project(latestCards, threads.associate { it.id.value to it.metadata }))
        }
        val historyRead = !status.pagination.hasMoreToLoad || status.pagesRead >= INITIAL_HISTORY_PAGES
        val threadsRead = status.threads is ThreadListPaginationStatus.Idle
        return MatchaRoomSnapshot(
            isLoaded = historyRead && threadsRead,
            threads = threads,
            board = board,
            permissions = permissions,
            hasMoreHistory = status.pagination.hasMoreToLoad,
            hasMoreThreads = (status.threads as? ThreadListPaginationStatus.Idle)?.hasMoreToLoad ?: true,
            isLoadingMore = status.loadingMore || status.pagination.isPaginating,
            events = events,
        )
    }

    /** Loads one more page of history and of the thread list. */
    suspend fun loadMore() {
        if (loadingMore.value) return
        loadingMore.value = true
        try {
            timeline?.takeIf { it.backwardPaginationStatus.value.hasMoreToLoad }?.paginate(Timeline.PaginationDirection.BACKWARDS)
                ?.onFailure { Timber.w(it, "Could not read older Matcha history") }
            if ((threadsPaginationStatus.value as? ThreadListPaginationStatus.Idle)?.hasMoreToLoad == true) {
                room.threadsListService.paginate().onFailure { Timber.w(it, "Could not read older threads") }
            }
        } finally {
            loadingMore.value = false
        }
    }

    /** Sends a Matcha timeline event and shows it straight away, before its remote echo. */
    suspend fun send(eventType: String, contentJson: String): Result<EventId> = writeMutex.withLock {
        room.sendRawEvent(eventType, contentJson).onSuccess { eventId ->
            val content = runCatching { json.parseToJsonElement(contentJson).jsonObject }.getOrNull() ?: return@onSuccess
            val echo = RawEvent(
                type = eventType,
                content = content,
                eventId = eventId.value,
                senderId = room.sessionId.value,
                timestamp = System.currentTimeMillis(),
                stateKey = null,
                isRedacted = false,
            )
            optimisticEvents.update { listOf(echo) + it }
        }
    }

    /** Sends a state event; it is read back from the stored state once synced. */
    suspend fun sendState(eventType: String, stateKey: String, contentJson: String): Result<EventId> = writeMutex.withLock {
        room.sendRawStateEvent(eventType, stateKey, contentJson).onSuccess { readStoredState() }
    }

    /** Overrides the board root until the state pointer is synced, after this client created the board. */
    fun rememberCreatedBoard(rootId: String) {
        if (storedState.value.boardPointer == null) storedState.update { it.copy(boardPointer = rootId) }
    }

    /** The newest card revision currently known for [cardId], for read-modify-write edits. */
    fun latestRevision(cardId: String): CardRevision? {
        val root = _snapshot.value.board?.rootId ?: return null
        return _snapshot.value.events.firstNotNullOfOrNull { event -> KanbanProtocol.parseCard(event, root)?.takeIf { it.cardId == cardId } }
    }

    companion object {
        /** Pages of 50 events read when the store opens. */
        const val INITIAL_HISTORY_PAGES = 10
    }
}
