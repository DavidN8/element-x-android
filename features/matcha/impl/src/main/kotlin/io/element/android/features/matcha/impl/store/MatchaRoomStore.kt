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
import io.element.android.features.matcha.impl.protocol.ThreadNameSource
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
import io.element.android.libraries.matrix.api.timeline.item.event.UtdCause
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

/** Why some of a room's Matcha data may be missing or out of date: one line for the user, the plan's merged warning. */
enum class MatchaWarning {
    /** Some Matcha events or thread roots can't be decrypted on this device yet. */
    Undecryptable,

    /** Older Matcha events can't be decrypted on this device: the keys of that history aren't available. */
    HistoryUnavailable,

    /** Matcha events from before the user joined can't be read. */
    NoAccess,

    /** Reading history or the thread list failed; the data may be out of date. */
    Network,
}

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
    /**
     * Matcha events in server order, newest first, for features that read them directly (documents). Events that can't be
     * decrypted are included with the type `m.room.encrypted`; only their relation is readable.
     */
    val events: List<RawEvent> = emptyList(),
    /** The room holds a board card or thread patch that can't be decrypted, so the board or names may be out of date. */
    val unreadable: Boolean = false,
    val warning: MatchaWarning? = null,
    /** True once the board pointer has been read from the room state, so a missing board is really missing. */
    val boardPointerRead: Boolean = false,
    /** The Matcha timeline could not be opened; [MatchaRoomStore.retryLoad] tries again. */
    val loadFailed: Boolean = false,
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
    private val storedState = MutableStateFlow(StoredState(boardPointer = null, legacyArchive = emptyMap(), boardPointerRead = false))
    private val utdCauses = MutableStateFlow<Map<String, UtdCause>>(emptyMap())
    private val loadFailed = MutableStateFlow(false)
    private val readFailed = MutableStateFlow(false)
    private val pagination = MutableStateFlow(Timeline.PaginationStatus(isPaginating = false, hasMoreToLoad = true))
    private val historyPagesRead = MutableStateFlow(0)
    private val loadingMore = MutableStateFlow(false)
    private val writeMutex = Mutex()
    private var timeline: Timeline? = null

    private val _snapshot = MutableStateFlow(MatchaRoomSnapshot())
    val snapshot: StateFlow<MatchaRoomSnapshot> = _snapshot.asStateFlow()

    private data class StoredState(
        val boardPointer: String?,
        val legacyArchive: Map<String, Boolean>,
        val boardPointerRead: Boolean,
    )

    init {
        start()
    }

    private fun openTimeline() {
        coroutineScope.launch {
            // Undecryptable events are read too, so the board can say when some of it can't be decrypted.
            val types = MatchaConfig.MESSAGE_LIKE_EVENT_TYPES + MatchaConfig.STATE_EVENT_TYPES + EVENT_ROOM_ENCRYPTED
            val created = room.createTimeline(CreateTimelineParams.OnlyEventTypes(types))
                .onFailure { Timber.w(it, "Could not open the Matcha timeline") }
                .getOrNull()
            loadFailed.value = created == null
            created ?: return@launch
            timeline = created
            created.backwardPaginationStatus.onEach { pagination.value = it }.launchIn(this)
            created.timelineItems
                .onEach { items ->
                    val events = ArrayList<RawEvent>(items.size)
                    val causes = HashMap<String, UtdCause>()
                    for (item in items.asReversed()) {
                        val event = parse(item) ?: continue
                        events += event
                        item.utdCause()?.let { cause -> event.eventId?.let { causes[it] = cause } }
                    }
                    utdCauses.value = causes
                    scanEvents.value = events
                }
                .launchIn(this)
            // Read a bounded amount of history up front; older items are loaded on demand.
            readInitialHistory(created)
        }
    }

    /** Opens the Matcha timeline again after it [MatchaRoomSnapshot.loadFailed]. */
    fun retryLoad() {
        if (loadFailed.value && timeline == null) openTimeline()
    }

    @OptIn(FlowPreview::class)
    private fun start() {
        openTimeline()
        room.threadsListService.subscribeToItemUpdates()
            .onEach { threadItems.value = it }
            .launchIn(coroutineScope)
        room.threadsListService.subscribeToPaginationUpdates()
            .onEach { threadsPaginationStatus.value = it }
            .launchIn(coroutineScope)
        coroutineScope.launch { paginateThreads() }
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
            combine(
                pagination,
                threadsPaginationStatus,
                historyPagesRead,
                loadingMore,
                combine(utdCauses, loadFailed, readFailed, ::Health),
                ::Status,
            ),
        ) { events, threads, state, perms, status ->
            buildSnapshot(events, threads, state, perms, status)
        }
            .distinctUntilChanged()
            .onEach { _snapshot.value = it }
            .launchIn(coroutineScope)
    }

    private data class Status(
        val pagination: Timeline.PaginationStatus,
        val threads: ThreadListPaginationStatus,
        val pagesRead: Int,
        val loadingMore: Boolean,
        val health: Health,
    )

    private data class Health(
        /** Why each undecryptable event of the Matcha timeline couldn't be decrypted, by event id. */
        val utdCauses: Map<String, UtdCause>,
        val loadFailed: Boolean,
        /** The last read of history or of the thread list failed. */
        val readFailed: Boolean,
    )

    private suspend fun readInitialHistory(timeline: Timeline) {
        repeat(INITIAL_HISTORY_PAGES) {
            if (!timeline.backwardPaginationStatus.value.hasMoreToLoad) return
            val result = runCatchingExceptions { timeline.paginate(Timeline.PaginationDirection.BACKWARDS).getOrThrow() }
            readFailed.value = result.isFailure
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
        val pointerRead = room.getStateEventsJson(StateEventType.Custom(MatchaConfig.STATE_KANBAN))
            .onFailure { Timber.w(it, "Could not read the board pointer") }
        val pointer = pointerRead.getOrNull()
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
        storedState.update { previous ->
            StoredState(
                // A failed read keeps what was known; only a successful read proves the pointer absent.
                boardPointer = if (pointerRead.isSuccess) pointer else previous.boardPointer,
                legacyArchive = legacy,
                boardPointerRead = previous.boardPointerRead || pointerRead.isSuccess,
            )
        }
    }

    /**
     * Reads the board pointer from the room state again, right before creating a board, so a board created elsewhere
     * is never replaced. Returns the root id, or null when the room has no board.
     */
    suspend fun readBoardPointer(): Result<String?> {
        readStoredState()
        val state = storedState.value
        return if (state.boardPointerRead) Result.success(state.boardPointer) else Result.failure(IllegalStateException("Board pointer not read"))
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
        status: Status,
    ): MatchaRoomSnapshot {
        val health = status.health
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
        // An undecryptable event's relation stays readable: one referring to the board or a thread is Matcha data.
        val matchaTargets = threadIds + listOfNotNull(boardRootId)
        val unreadableCauses = events
            .filter { it.type == EVENT_ROOM_ENCRYPTED && it.content.referenceTarget() in matchaTargets }
            .map { event -> event.eventId?.let(health.utdCauses::get) }
        val unreadableRootCauses = threadList
            .filter { item -> (item.rootEvent.content as? UnableToDecryptContent) != null }
            .filter { item -> threads.firstOrNull { it.id == item.threadId }?.metadata?.nameSource != ThreadNameSource.Explicit }
            .map { item -> ((item.rootEvent.content as UnableToDecryptContent).data as? UnableToDecryptContent.Data.MegolmV1AesSha2)?.utdCause }
        val warning = (unreadableCauses + unreadableRootCauses).map(::warningFor).minOrNull()
            ?: MatchaWarning.Network.takeIf { health.readFailed }
        return MatchaRoomSnapshot(
            isLoaded = historyRead && threadsRead,
            threads = threads,
            board = board,
            permissions = permissions,
            hasMoreHistory = status.pagination.hasMoreToLoad,
            hasMoreThreads = (status.threads as? ThreadListPaginationStatus.Idle)?.hasMoreToLoad ?: true,
            isLoadingMore = status.loadingMore || status.pagination.isPaginating,
            events = events,
            unreadable = unreadableCauses.isNotEmpty(),
            warning = warning,
            boardPointerRead = state.boardPointerRead,
            loadFailed = health.loadFailed,
        )
    }

    private suspend fun paginateThreads() {
        val result = room.threadsListService.paginate().onFailure { Timber.w(it, "Could not read threads") }
        readFailed.value = result.isFailure
    }

    /** Loads one more page of history and of the thread list. */
    suspend fun loadMore() {
        if (loadingMore.value) return
        loadingMore.value = true
        try {
            timeline?.takeIf { it.backwardPaginationStatus.value.hasMoreToLoad }?.paginate(Timeline.PaginationDirection.BACKWARDS)
                ?.also { readFailed.value = it.isFailure }
                ?.onFailure { Timber.w(it, "Could not read older Matcha history") }
            if ((threadsPaginationStatus.value as? ThreadListPaginationStatus.Idle)?.hasMoreToLoad == true) paginateThreads()
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
        private const val EVENT_ROOM_ENCRYPTED = "m.room.encrypted"
    }
}

/** Matrix only tells why an event can't be decrypted for Megolm events; anything else is plainly undecryptable. */
private fun MatrixTimelineItem.utdCause(): UtdCause? {
    val content = (this as? MatrixTimelineItem.Event)?.event?.content as? UnableToDecryptContent ?: return null
    return (content.data as? UnableToDecryptContent.Data.MegolmV1AesSha2)?.utdCause ?: UtdCause.Unknown
}

/** The warning for one undecryptable event; the lowest one wins when several apply. */
private fun warningFor(cause: UtdCause?): MatchaWarning = when (cause) {
    UtdCause.SentBeforeWeJoined -> MatchaWarning.NoAccess
    UtdCause.HistoricalMessageAndBackupIsDisabled,
    UtdCause.HistoricalMessageAndDeviceIsUnverified -> MatchaWarning.HistoryUnavailable
    else -> MatchaWarning.Undecryptable
}
