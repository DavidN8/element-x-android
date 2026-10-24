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
import io.element.android.features.matcha.impl.protocol.REL_TYPE_THREAD
import io.element.android.features.matcha.impl.protocol.RawEvent
import io.element.android.features.matcha.impl.protocol.ThreadMetadata
import io.element.android.features.matcha.impl.protocol.ThreadMetadataPatch
import io.element.android.features.matcha.impl.protocol.ThreadMetadataProtocol
import io.element.android.features.matcha.impl.protocol.ThreadNameSource
import io.element.android.features.matcha.impl.protocol.booleanOrNull
import io.element.android.features.matcha.impl.protocol.objectOrNull
import io.element.android.features.matcha.impl.protocol.referenceTarget
import io.element.android.features.matcha.impl.protocol.stringOrNull
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.ThreadId
import io.element.android.libraries.matrix.api.core.UniqueId
import io.element.android.libraries.matrix.api.room.CreateTimelineParams
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.room.MessageEventType
import io.element.android.libraries.matrix.api.room.RawRelationType
import io.element.android.libraries.matrix.api.room.StateEventType
import io.element.android.libraries.matrix.api.room.powerlevels.permissionsFlow
import io.element.android.libraries.matrix.api.room.threads.ThreadListItem
import io.element.android.libraries.matrix.api.room.threads.ThreadListPaginationStatus
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.api.timeline.TimelineException
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

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
    /** True when the thread was created with a name, so its first reply is the hidden bootstrap reply that [numberOfReplies] counts. */
    val hasHiddenFirstReply: Boolean = false,
) {
    /** The replies people can see. */
    fun visibleReplies(): Int = (numberOfReplies - if (hasHiddenFirstReply) 1 else 0).coerceAtLeast(0).toInt()
}

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
    /** False until the thread list, the first history pages and the stored room state have been read. */
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
    /**
     * True once a missing [board] is really missing: the board is known, or the Matcha history was read back to the start of
     * the room. The stored room state alone proves nothing, since the sync only stores the custom state it asked for.
     */
    val isBoardResolved: Boolean = false,
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
    /** More event types to read into [MatchaRoomSnapshot.events], such as `m.room.encrypted` for documents. */
    private val extraEventTypes: Set<String> = emptySet(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val parsed = HashMap<UniqueId, Pair<EventTimelineItem, RawEvent?>>()
    private val scanEvents = MutableStateFlow<List<RawEvent>>(emptyList())

    /** Matcha events read from the homeserver as relations of each anchor (board root, thread roots, document anchor), newest first. */
    private val relationEvents = MutableStateFlow<Map<String, List<RawEvent>>>(emptyMap())

    /** New Matcha events from sync, newest first; timelines of this SDK drop custom message-like events, so they come from here. */
    private val liveEvents = MutableStateFlow<List<RawEvent>>(emptyList())
    private val anchorsLoading = MutableStateFlow<Set<String>>(emptySet())
    private val relationsUnavailable = MutableStateFlow(false)
    private val requestedAnchors = HashSet<String>()
    private val anchorPermits = Semaphore(MAX_CONCURRENT_ANCHORS)
    private val optimisticEvents = MutableStateFlow<List<RawEvent>>(emptyList())
    private val threadItems = MutableStateFlow<List<ThreadListItem>>(emptyList())
    private val threadsPaginationStatus = MutableStateFlow<ThreadListPaginationStatus>(ThreadListPaginationStatus.Idle(hasMoreToLoad = true))
    private val storedState = MutableStateFlow(
        StoredState(boardPointer = null, legacyArchive = emptyMap(), createdBoardRoot = null, isRead = false),
    )
    private val utdCauses = MutableStateFlow<Map<String, UtdCause>>(emptyMap())
    private val loadFailed = MutableStateFlow(false)
    private val readFailed = MutableStateFlow(false)
    private val pagination = MutableStateFlow(Timeline.PaginationStatus(isPaginating = false, hasMoreToLoad = true))
    private val historyPagesRead = MutableStateFlow(0)
    private val loadingMore = MutableStateFlow(false)
    private val writeMutex = Mutex()
    private val historyMutex = Mutex()

    /** The scan timeline once opened; null before that and after a failed open. */
    private val timelineFlow = MutableStateFlow<Timeline?>(null)
    private val timeline: Timeline? get() = timelineFlow.value

    /** A board root this client sent without managing to point the room at it; creating the board again points at this one. */
    internal var unpointedBoardRoot: String? = null

    private val _snapshot = MutableStateFlow(MatchaRoomSnapshot())
    val snapshot: StateFlow<MatchaRoomSnapshot> = _snapshot.asStateFlow()

    private data class StoredState(
        /** The board pointer of the stored room state; only a hint, see [MatchaRoomSnapshot.isBoardResolved]. */
        val boardPointer: String?,
        val legacyArchive: Map<String, Boolean>,
        /** The board this client created, until its pointer is read back. */
        val createdBoardRoot: String?,
        /** The stored state was read at least once, successfully or not. */
        val isRead: Boolean,
        /** The board pointer the homeserver returned, which the sync doesn't store; null when it has none or wasn't asked. */
        val serverPointer: String? = null,
        /** The homeserver answered whether the room has a board pointer, so a missing board is really missing. */
        val isServerRead: Boolean = false,
    )

    init {
        start()
    }

    private fun openTimeline() {
        coroutineScope.launch {
            // Undecryptable events are read too, so the board can say when some of it can't be decrypted.
            val types = MatchaConfig.MESSAGE_LIKE_EVENT_TYPES + MatchaConfig.STATE_EVENT_TYPES + EVENT_ROOM_ENCRYPTED + extraEventTypes
            val created = room.createTimeline(CreateTimelineParams.OnlyEventTypes(types))
                .onFailure { Timber.w(it, "Could not open the Matcha timeline") }
                .getOrNull()
            loadFailed.value = created == null
            created ?: return@launch
            // The SDK timeline lives until it is closed, so it goes with the store's scope.
            coroutineContext.job.invokeOnCompletion { created.close() }
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
            timelineFlow.value = created
            // Read a bounded amount of history up front; older items are loaded on demand.
            readInitialHistory(created)
        }
    }

    /** Opens the Matcha timeline again after it [MatchaRoomSnapshot.loadFailed]. */
    fun retryLoad() {
        if (loadFailed.value && timeline == null) openTimeline()
        if (!storedState.value.isServerRead) coroutineScope.launch { readServerPointer() }
    }

    @OptIn(FlowPreview::class)
    private fun start() {
        openTimeline()
        coroutineScope.launch { readServerPointer() }
        room.subscribeToRawMessageLikeEvents(MatchaConfig.MESSAGE_LIKE_EVENT_TYPES)
            .onEach { json ->
                val event = RawEvent.parse(json) ?: return@onEach
                liveEvents.update { list -> if (list.any { it.eventId == event.eventId }) list else listOf(event) + list }
            }
            .launchIn(coroutineScope)
        if (extraEventTypes.isNotEmpty()) coroutineScope.launch { requestDocumentAnchor() }
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
            combine(scanEvents, liveEvents, relationEvents, optimisticEvents) { scan, live, related, optimistic ->
                merge((live + related.values.flatten() + scan).distinctBy { it.eventId ?: it }, optimistic)
            },
            threadItems,
            storedState,
            permissions,
            combine(
                pagination,
                threadsPaginationStatus,
                historyPagesRead,
                loadingMore,
                combine(utdCauses, loadFailed, readFailed, anchorsLoading, relationsUnavailable, ::Health),
                ::Status,
            ),
        ) { events, threads, state, perms, status ->
            buildSnapshot(events, threads, state, perms, status)
        }
            .distinctUntilChanged()
            .onEach { snapshot ->
                _snapshot.value = snapshot
                requestAnchors(listOfNotNull(snapshot.board?.rootId) + snapshot.threads.map { it.id.value })
                requestAnchors(snapshot.bootstrapCandidates(), RawRelationType.Thread)
            }
            .launchIn(coroutineScope)
    }

    /** The room's `m.room.create` event, which document frames relate to. */
    private suspend fun requestDocumentAnchor() {
        room.getStateEventId(StateEventType.RoomCreate, "").getOrNull()?.let { requestAnchors(listOf(it.value)) }
    }

    /**
     * Threads whose reply count may include the hidden bootstrap reply, checked through their `m.thread` relations. A thread named
     * when it was created usually takes its name from the root message, so any thread may have one.
     *
     * ponytail: one request per thread, and only threads that fit in one page of replies are checked; a longer thread may count one
     * reply too many.
     */
    private fun MatchaRoomSnapshot.bootstrapCandidates(): List<String> =
        threads.filter { !it.hasHiddenFirstReply && it.numberOfReplies in 1L..RELATIONS_PAGE_SIZE }.map { it.id.value }

    /**
     * Fetches, once each, the events related to new anchors by [relation], a few anchors at a time.
     * For thread relations only the hidden bootstrap replies are kept, from one page.
     */
    private fun requestAnchors(anchors: Collection<String>, relation: RawRelationType = RawRelationType.Reference) {
        val byKey = anchors.filter { it.startsWith("$") }.associateBy { if (relation == RawRelationType.Thread) "$it$THREAD_KEY_SUFFIX" else it }
        val fresh = synchronized(requestedAnchors) { byKey.keys.filter { requestedAnchors.add(it) } }
        if (fresh.isEmpty()) return
        anchorsLoading.update { it + fresh }
        for (key in fresh) {
            coroutineScope.launch {
                try {
                    anchorPermits.withPermit { fetchAllRelated(byKey.getValue(key), relation) }
                        .onSuccess { events -> relationEvents.update { it + (key to events) } }
                        .onFailure { error ->
                            if (error is UnsupportedOperationException) relationsUnavailable.value = true else readFailed.value = true
                            Timber.w(error, "Could not read the events related to a Matcha anchor")
                        }
                } finally {
                    anchorsLoading.update { it - key }
                }
            }
        }
    }

    /**
     * Reads every event related to [anchor] by [relation], newest first, page by page; for threads, the bootstrap replies of one page.
     *
     * ponytail: a fixed page cap bounds very long histories (MAX_RELATION_PAGES × 100 events); add paging on demand if one anchor
     * ever needs more.
     */
    private suspend fun fetchAllRelated(anchor: String, relation: RawRelationType): Result<List<RawEvent>> {
        if (relation == RawRelationType.Thread) {
            return room.fetchRelatedEventsJson(EventId(anchor), relation, null, RELATIONS_PAGE_SIZE)
                .map { page -> page.events.mapNotNull { RawEvent.parse(it) }.filter { it.type == MatchaConfig.EVENT_THREAD_METADATA } }
        }
        val events = ArrayList<RawEvent>()
        var from: String? = null
        repeat(MAX_RELATION_PAGES) {
            val page = room.fetchRelatedEventsJson(EventId(anchor), relation, from, RELATIONS_PAGE_SIZE).getOrElse { return Result.failure(it) }
            page.events.mapNotNullTo(events) { RawEvent.parse(it) }
            from = page.nextBatch ?: return Result.success(events)
        }
        Timber.w("Stopped reading the events related to a Matcha anchor after %d pages", MAX_RELATION_PAGES)
        return Result.success(events)
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
        /** Anchors whose related events are still being fetched. */
        val anchorsLoading: Set<String>,
        /** The SDK can't fetch related events as JSON, so cards, thread names and documents can't be read. */
        val relationsUnavailable: Boolean,
    )

    private suspend fun readInitialHistory(timeline: Timeline) {
        repeat(INITIAL_HISTORY_PAGES) {
            if (!timeline.backwardPaginationStatus.value.hasMoreToLoad) return
            // Another read may have reached the start while this one waited for the lock; paginateBackwards allows for that.
            val result = historyMutex.withLock { paginateBackwards(timeline) }
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

    /** Reads the stored room state; returns the board pointer read, or the failure to read it. */
    private suspend fun readStoredState(): Result<String?> {
        val pointerRead = room.getStateEventsJson(StateEventType.Custom(MatchaConfig.STATE_KANBAN))
            .onFailure { Timber.w(it, "Could not read the board pointer") }
            .map { states ->
                states[""]
                    ?.let { runCatchingExceptions { json.parseToJsonElement(it).jsonObject }.getOrNull() }
                    ?.let(KanbanProtocol::parseBoardPointer)
            }
        val legacy = room.getStateEventsJson(StateEventType.Custom(MatchaConfig.STATE_THREAD_ARCHIVE)).getOrNull()
            .orEmpty()
            .mapNotNull { (key, content) ->
                val archived = runCatchingExceptions { json.parseToJsonElement(content).jsonObject }.getOrNull()?.booleanOrNull("archived")
                archived?.let { key to it }
            }
            .toMap()
        storedState.update { previous ->
            // A failed read keeps what was known.
            previous.copy(boardPointer = pointerRead.getOrElse { previous.boardPointer }, legacyArchive = legacy, isRead = true)
        }
        return pointerRead
    }

    /**
     * Reads the board pointer again, right before creating a board: from the stored room state, then from the homeserver when the
     * stored state holds none, so a board made elsewhere is never replaced. Returns the root id, null when there is none (when the
     * SDK can't ask the homeserver, null doesn't prove the room has no board, see [MatchaRoomSnapshot.isBoardResolved]), or the
     * failure of the read.
     */
    suspend fun readBoardPointer(): Result<String?> {
        val stored = readStoredState()
        if (stored.getOrNull() != null) return stored
        return fetchServerPointer()?.onSuccess(::recordServerPointer) ?: stored
    }

    /** Asks the homeserver for the board pointer and records the answer; does nothing when this SDK can't ask. */
    private suspend fun readServerPointer() {
        fetchServerPointer()?.onSuccess(::recordServerPointer)
    }

    private fun recordServerPointer(pointer: String?) = storedState.update { it.copy(serverPointer = pointer, isServerRead = true) }

    /**
     * The board pointer from the homeserver: the root id, or null when the room has none. Returns null itself when the Rust SDK
     * in use can't fetch state from the homeserver (it needs the Matcha SDK patch).
     */
    private suspend fun fetchServerPointer(): Result<String?>? {
        val read = room.fetchStateEventContentJson(MatchaConfig.STATE_KANBAN, "")
        if (read.exceptionOrNull() is UnsupportedOperationException) return null
        return read
            .onFailure { Timber.w(it, "Could not fetch the board pointer from the homeserver") }
            .map { content ->
                content
                    ?.let { runCatchingExceptions { json.parseToJsonElement(it).jsonObject }.getOrNull() }
                    ?.let(KanbanProtocol::parseBoardPointer)
            }
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
        // Only a pointer makes a board, as on the web client. A root without one is never a board: it is left behind by a create
        // whose pointer failed, and the pointer of a real board is newer than its root, so the scan holds it whenever it holds the
        // root. Cards are newer than the pointer, so the board they refer to stands in for a pointer older than the scan.
        // The homeserver's answer, read when the store opened, comes after the stored state and the scan, which can be newer.
        val boardRootId = state.boardPointer
            ?: events.firstOrNull { it.type == MatchaConfig.STATE_KANBAN && it.stateKey == "" }
                ?.let { KanbanProtocol.parseBoardPointer(it.content) }
            ?: state.serverPointer
            ?: state.createdBoardRoot
            ?: events.firstOrNull { it.type == MatchaConfig.EVENT_KANBAN_CARD }?.content?.referenceTarget()
        val revisions = boardRootId?.let { root -> events.mapNotNull { KanbanProtocol.parseCard(it, root) } }.orEmpty()
        val latestCards = KanbanProtocol.latestRevisions(revisions)

        val patchesByRoot = HashMap<String, MutableList<ThreadMetadataPatch>>()
        for (event in events.filter { it.type == MatchaConfig.EVENT_THREAD_METADATA }) {
            val root = event.content.referenceTarget() ?: continue
            ThreadMetadataProtocol.parsePatch(event, root)?.let { patchesByRoot.getOrPut(root) { mutableListOf() } += it }
        }
        val listed = threadList.associateBy { it.threadId.value }
        val bootstrapped = events.mapNotNullTo(HashSet()) { it.bootstrapRootId() }
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
                hasHiddenFirstReply = id in bootstrapped,
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
            .filter { item -> item.rootEvent.content is UnableToDecryptContent }
            .filter { item -> threads.firstOrNull { it.id == item.threadId }?.metadata?.nameSource != ThreadNameSource.Explicit }
            .map { item -> ((item.rootEvent.content as UnableToDecryptContent).data as? UnableToDecryptContent.Data.MegolmV1AesSha2)?.utdCause }
        val warning = (unreadableCauses + unreadableRootCauses).map(::warningFor).minOrNull()
            ?: MatchaWarning.HistoryUnavailable.takeIf { health.relationsUnavailable }
            ?: MatchaWarning.Network.takeIf { health.readFailed }
        return MatchaRoomSnapshot(
            isLoaded = historyRead && threadsRead && state.isRead && health.anchorsLoading.isEmpty(),
            threads = threads,
            board = board,
            permissions = permissions,
            hasMoreHistory = status.pagination.hasMoreToLoad,
            hasMoreThreads = (status.threads as? ThreadListPaginationStatus.Idle)?.hasMoreToLoad ?: true,
            isLoadingMore = status.loadingMore || status.pagination.isPaginating,
            events = events,
            unreadable = unreadableCauses.isNotEmpty(),
            warning = warning,
            // ponytail: history hidden from a member who joined later may hide an older pointer too; Path B reads it from the server.
            isBoardResolved = boardRootId != null || state.isServerRead || !status.pagination.hasMoreToLoad,
            loadFailed = health.loadFailed,
        )
    }

    private suspend fun paginateThreads() {
        val result = room.threadsListService.paginate().onFailure { Timber.w(it, "Could not read threads") }
        readFailed.value = result.isFailure
    }

    /**
     * Loads one more page of history and of the thread list. Calls are serialized, and a call made before the timeline has
     * opened waits for it.
     *
     * @return whether older history remains, or the failure to read the history page; a failure to read more threads is only logged.
     */
    suspend fun loadMore(): Result<Boolean> = historyMutex.withLock {
        val timeline = awaitTimeline() ?: return@withLock Result.failure(IllegalStateException("The Matcha timeline is not open"))
        loadingMore.value = true
        try {
            val history = paginateBackwards(timeline)
                .also { readFailed.value = it.isFailure }
                .onFailure { Timber.w(it, "Could not read older Matcha history") }
            if ((threadsPaginationStatus.value as? ThreadListPaginationStatus.Idle)?.hasMoreToLoad == true) paginateThreads()
            if (extraEventTypes.isNotEmpty()) requestDocumentAnchor()
            // Related events are read in full per anchor, so "no more history" also means every anchor has been read.
            if (history.isSuccess) withTimeoutOrNull(ANCHORS_TIMEOUT) { anchorsLoading.first { it.isEmpty() } }
            history.map { timeline.backwardPaginationStatus.value.hasMoreToLoad }
        } finally {
            loadingMore.value = false
        }
    }

    /**
     * Loads older history until [isFound] holds for the snapshot or the start of the room is reached, then returns once the
     * snapshot shows what was read. Returns the failure to read a page.
     *
     * ponytail: reads page by page, so reaching the start of a long room takes many requests; Path B replaces the scan.
     */
    suspend fun loadHistoryUntil(isFound: (MatchaRoomSnapshot) -> Boolean): Result<Unit> {
        while (!isFound(_snapshot.value)) {
            val hasMore = loadMore().getOrElse { return Result.failure(it) }
            if (!hasMore) {
                _snapshot.first { !it.hasMoreHistory }
                break
            }
        }
        return Result.success(Unit)
    }

    /** The open timeline, waiting for it to open; null when opening failed. */
    private suspend fun awaitTimeline(): Timeline? =
        combine(timelineFlow, loadFailed) { opened, failed -> opened to failed }.first { (opened, failed) -> opened != null || failed }.first

    /** Reads one older page. The initial history read may be running, so a refused page waits for it and tries again. */
    private suspend fun paginateBackwards(timeline: Timeline): Result<Unit> {
        var result: Result<Unit> = Result.failure(TimelineException.CannotPaginate)
        var attempts = 0
        while (result.exceptionOrNull() == TimelineException.CannotPaginate && attempts++ < PAGINATION_ATTEMPTS) {
            val hasMore = timeline.backwardPaginationStatus.first { !it.isPaginating }.hasMoreToLoad
            result = if (hasMore) timeline.paginate(Timeline.PaginationDirection.BACKWARDS).map { } else Result.success(Unit)
        }
        return result
    }

    /** Sends a Matcha timeline event and shows it straight away, before its remote echo. */
    suspend fun send(eventType: String, contentJson: String): Result<EventId> = writeMutex.withLock {
        room.sendRawEvent(eventType, contentJson).onSuccess { eventId ->
            // Only the Matcha types come back through the filtered timeline, so an echo of any other type would never be replaced.
            if (eventType !in MatchaConfig.MESSAGE_LIKE_EVENT_TYPES) return@onSuccess
            val content = runCatchingExceptions { json.parseToJsonElement(contentJson).jsonObject }.getOrNull() ?: return@onSuccess
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

    /** Shows the board this client created until its pointer is read back from the room. */
    fun rememberCreatedBoard(rootId: String) {
        storedState.update { it.copy(createdBoardRoot = rootId) }
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
        private const val PAGINATION_ATTEMPTS = 5
        private const val MAX_CONCURRENT_ANCHORS = 4
        private const val RELATIONS_PAGE_SIZE = 100
        private const val MAX_RELATION_PAGES = 50
        private const val THREAD_KEY_SUFFIX = "#thread"
        private val ANCHORS_TIMEOUT = 30.seconds
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

/** The thread root of a hidden first reply sent when a named thread is created, or null for any other event. */
private fun RawEvent.bootstrapRootId(): String? {
    if (type != MatchaConfig.EVENT_THREAD_METADATA) return null
    val relation = content.objectOrNull("m.relates_to") ?: return null
    return relation.stringOrNull("event_id")?.takeIf { relation.stringOrNull("rel_type") == REL_TYPE_THREAD }
}
