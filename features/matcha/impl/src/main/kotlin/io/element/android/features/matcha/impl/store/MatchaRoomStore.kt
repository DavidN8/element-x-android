/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.store

import io.element.android.appconfig.MatchaConfig
import io.element.android.features.matcha.impl.docs.DocsProtocol
import io.element.android.features.matcha.impl.protocol.KanbanProtocol
import io.element.android.features.matcha.impl.protocol.ProjectedCard
import io.element.android.features.matcha.impl.protocol.REL_TYPE_THREAD
import io.element.android.features.matcha.impl.protocol.RawEvent
import io.element.android.features.matcha.impl.protocol.TaskParticipants
import io.element.android.features.matcha.impl.protocol.ThreadMetadata
import io.element.android.features.matcha.impl.protocol.ThreadMetadataPatch
import io.element.android.features.matcha.impl.protocol.ThreadMetadataProtocol
import io.element.android.features.matcha.impl.protocol.ThreadNameSource
import io.element.android.features.matcha.impl.protocol.booleanOrNull
import io.element.android.features.matcha.impl.protocol.referenceTarget
import io.element.android.features.matcha.impl.protocol.relationTarget
import io.element.android.features.matcha.impl.protocol.toJsonObjectOrNull
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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.coroutines.EmptyCoroutineContext
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
    /** The sender of the thread root, or null while the thread isn't in the thread list read so far. */
    val rootSenderId: String? = null,
    /**
     * Who the thread involves: its root sender and everyone who replied in it, as far as read.
     *
     * ponytail: a thread's first read pages back through at most 50 pages of 100 replies, so in a thread longer than 5000 replies
     * someone who only replied further back is missed; paging on demand would lift that.
     */
    val participants: Set<String> = emptySet(),
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
) {
    /** A new thread is a message and its name. */
    val canCreateThreads: Boolean get() = canManageThreads && canSendMessages

    /** Adding a task also creates its thread. */
    val canAddTasks: Boolean get() = canWriteCards && canCreateThreads

    /** Archiving and restoring a thread's task also changes the thread. */
    fun canArchive(threadId: ThreadId?): Boolean = canWriteCards && (threadId == null || canManageThreads)
}

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
    /** The room is a direct chat, whose members are involved in every task. */
    val isDirect: Boolean = false,
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
    /** Also reads the events related to the room's `m.room.create` event, which document frames relate to. */
    private val readsDocumentAnchor: Boolean = false,
    /** The board pointer the homeserver already returned to the caller, so the store starts from it instead of asking again. */
    knownServerPointer: String? = null,
    /**
     * Where related events are parsed and snapshots are built, off the scope's (main) thread; null does it all on [coroutineScope]'s
     * dispatcher.
     */
    private val computation: CoroutineDispatcher? = null,
) {
    /**
     * Builds snapshots on [computation], one at a time: on a pool the combined flows' collectors could run in parallel and pair a
     * finished load with relation events one update old, so a board could show for a frame without its cards.
     */
    private val snapshotDispatcher = computation?.limitedParallelism(1)
    private val parsed = HashMap<UniqueId, Pair<EventTimelineItem, RawEvent?>>()
    private val scanEvents = MutableStateFlow<List<RawEvent>>(emptyList())

    /** Matcha events read from the homeserver as relations of each anchor (board root, thread roots, document anchor), newest first. */
    private val relationEvents = MutableStateFlow<Map<String, List<RawEvent>>>(emptyMap())

    /** New Matcha events from sync, newest first; timelines of this SDK drop custom message-like events, so they come from here. */
    private val liveEvents = MutableStateFlow<List<RawEvent>>(emptyList())
    private val anchorsLoading = MutableStateFlow<Set<String>>(emptySet())

    /** The senders of each thread's replies, as far as read, by thread root id. */
    private val threadRepliers = MutableStateFlow<Map<String, Set<String>>>(emptyMap())

    /** The reply count each thread had when its replies were last requested, by thread root id. */
    private val repliesRequestedAt = HashMap<String, Long>()

    /** The highest reply count each thread had when a read of its replies succeeded, by thread root id; guarded like [repliesRequestedAt]. */
    private val repliesReadAt = HashMap<String, Long>()

    /** The newest replies read of each thread, at most a page of them, newest first, by thread root id; guarded like [repliesRequestedAt]. */
    private val newestRepliesRead = HashMap<String, List<String>>()

    /** The threads whose replies are being read, one read per thread at a time; guarded like [repliesRequestedAt]. */
    private val repliesReading = HashSet<String>()
    private val _relationsUnavailable = MutableStateFlow(false)

    /** The SDK can't fetch related events as JSON, so cards, thread names and document frames can't be read from relations. */
    val relationsUnavailable: StateFlow<Boolean> = _relationsUnavailable.asStateFlow()

    private val requestedAnchors = HashSet<String>()
    private val anchorPermits = Semaphore(MAX_CONCURRENT_ANCHORS)
    private val optimisticEvents = MutableStateFlow<List<RawEvent>>(emptyList())
    private val threadItems = MutableStateFlow<List<ThreadListItem>>(emptyList())

    /**
     * Each thread's replies read through relations, by thread root id, once a read reached its first reply; they give the thread its
     * reply count and the time of its latest reply.
     *
     * ponytail: the SDK's thread list (matrix-sdk-ui `thread_list_service.rs`, `event_cache_listener_loop`) counts every event of a
     * listed thread that the room's event cache receives as one more reply, those read back from history or decrypted again
     * included, so the scan's history read counted replies again. It also caps the latest reply's time at the root's. The relations
     * read is the homeserver's own list; a thread longer than the page cap keeps the thread list's count, as does any thread until a
     * read of all its replies succeeds or when none can be read (history hidden from the user). Counting only newer events in the
     * SDK would let the list's count back.
     */
    private val readReplies = MutableStateFlow<Map<String, ReadReplies>>(emptyMap())
    private val threadsPaginationStatus = MutableStateFlow<ThreadListPaginationStatus>(ThreadListPaginationStatus.Idle(hasMoreToLoad = true))
    private val storedState = MutableStateFlow(
        StoredState(
            boardPointer = null,
            legacyArchive = emptyMap(),
            createdBoardRoot = null,
            isRead = false,
            serverPointer = knownServerPointer,
            isServerRead = knownServerPointer != null,
        ),
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

    /** The current user, whose view of the board the snapshot is: a task not shared with the chat shows only to the people it involves. */
    val sessionUserId: String get() = room.sessionId.value

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
        val serverPointer: String?,
        /** The homeserver answered whether the room has a board pointer, so a missing board is really missing. */
        val isServerRead: Boolean,
    )

    init {
        start()
    }

    private fun openTimeline() {
        coroutineScope.launch {
            // Undecryptable events are read too, so the board can say when some of it can't be decrypted.
            val types = MatchaConfig.MESSAGE_LIKE_EVENT_TYPES + MatchaConfig.STATE_EVENT_TYPES + DocsProtocol.EVENT_ROOM_ENCRYPTED
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
        if (!storedState.value.isServerRead) coroutineScope.launch { readServerPointer() }
        room.subscribeToRawMessageLikeEvents(MatchaConfig.MESSAGE_LIKE_EVENT_TYPES)
            .onEach { json ->
                val event = RawEvent.parse(json) ?: return@onEach
                liveEvents.update { list -> if (list.any { it.eventId == event.eventId }) list else listOf(event) + list }
            }
            .launchIn(coroutineScope)
        if (readsDocumentAnchor) coroutineScope.launch { requestDocumentAnchor() }
        room.threadsListService.subscribeToItemUpdates()
            .onEach {
                threadItems.value = it
                // The thread list's count, which says when a thread has new replies, changes without changing the snapshot.
                requestReplies(_snapshot.value.threads)
            }
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
                merge((live + related.values.flatten() + scan).distinctReadable(), optimistic)
            },
            combine(threadItems, threadRepliers, directMembersFlow(), readReplies, ::ThreadData),
            storedState,
            permissions,
            combine(
                pagination,
                threadsPaginationStatus,
                historyPagesRead,
                loadingMore,
                combine(utdCauses, loadFailed, readFailed, anchorsLoading, _relationsUnavailable, ::Health),
                ::Status,
            ),
        ) { events, threadData, state, perms, status ->
            buildSnapshot(events, threadData, state, perms, status)
        }
            // Only the latest snapshot matters, so a burst of changes is built once.
            .let { snapshots -> snapshotDispatcher?.let { snapshots.conflate().flowOn(it) } ?: snapshots }
            .distinctUntilChanged()
            .onEach { snapshot ->
                _snapshot.value = snapshot
                requestAnchors(listOfNotNull(snapshot.board?.rootId) + snapshot.threads.map { it.id.value })
                requestReplies(snapshot.threads)
            }
            .launchIn(coroutineScope)
    }

    /** The room's `m.room.create` event, which document frames relate to. */
    private suspend fun requestDocumentAnchor() {
        room.getStateEventId(StateEventType.RoomCreate, "").getOrNull()?.let { requestAnchors(listOf(it.value)) }
    }

    /**
     * Waits until the events related to the room's `m.room.create` event, which document frames relate to, have been read and the
     * snapshot holds them, then until the store has loaded or shows why it may be incomplete ([MatchaRoomSnapshot.loadFailed] or a
     * [MatchaRoomSnapshot.warning]). The anchor is requested here too, in case the room state didn't hold it when the store opened.
     * A read that never ends keeps the store from loading, so callers bound the wait.
     */
    suspend fun awaitRelations() {
        requestDocumentAnchor()
        withTimeoutOrNull(ANCHORS_TIMEOUT) { anchorsLoading.first { it.isEmpty() } }
        // Snapshots are built a moment after a read, so the latest one may be from before the anchor was requested.
        val read = relationEvents.value.values.flatten().mapNotNullTo(HashSet()) { it.eventId }
        _snapshot.first { snapshot ->
            (snapshot.isLoaded || snapshot.loadFailed || snapshot.warning != null) &&
                snapshot.events.mapNotNullTo(HashSet()) { it.eventId }.containsAll(read)
        }
    }

    /** The current user and the other members of a direct chat, or empty in any other chat. */
    private fun directMembersFlow() = room.roomInfoFlow
        .map { info -> if (info.isDirect) setOf(room.sessionId.value) + info.heroes.map { it.userId.value } else emptySet() }
        .distinctUntilChanged()

    /** Fetches, once each, the events related to new anchors by an `m.reference` relation, a few anchors at a time. */
    private fun requestAnchors(anchors: Collection<String>) {
        val fresh = synchronized(requestedAnchors) { anchors.filter { it.startsWith("$") && requestedAnchors.add(it) } }
        for (anchor in fresh) {
            launchRelationsRead(anchor, isFirstRead = true, read = { fetchRelated(anchor, RawRelationType.Reference) }) { events ->
                relationEvents.update { it + (anchor to events) }
            }
        }
    }

    /**
     * Reads each thread's replies: their senders are the thread's participants, and the hidden bootstrap reply among them (the
     * thread's first) makes [MatchaThread.hasHiddenFirstReply]. A thread's first read pages back through all its replies. A thread
     * is read again whenever the thread list's reply count changes, so new repliers and replies are seen, one read per thread at a
     * time; only its first read holds up [MatchaRoomSnapshot.isLoaded]. What is read is added to what was read before, so once a read
     * has succeeded, the next one pages only until it reaches one of the newest replies read before. The replies read give the
     * thread its reply count and the time of its latest reply (see [readReplies]).
     *
     * ponytail: a first read takes up to [MAX_RELATION_PAGES] requests of [RELATIONS_PAGE_SIZE] replies and holds up the load until
     * done; a longer thread may count its hidden reply as a visible one, and misses repliers further back. Paging older replies on
     * demand, as the web client's Load more does, would lift both. A later read that finds none of the newest replies read before
     * (all redacted since) pages back through the whole thread too.
     */
    private fun requestReplies(threads: List<MatchaThread>) {
        // Without the SDK's related events, every read would fail the same way.
        if (_relationsUnavailable.value) return
        // The thread list's count tells when there are new replies to read.
        val listedCounts = threadItems.value.associate { it.threadId.value to it.numberOfReplies }
        val fresh = synchronized(repliesRequestedAt) {
            threads
                // A listed thread without replies has none to read; a card's thread that isn't listed yet may have some. A thread
                // being read is requested again when its read ends.
                .filter { listedCounts[it.id.value] ?: it.numberOfReplies > 0 || it.rootSenderId == null }
                .filter { it.id.value !in repliesReading }
                .mapNotNull { thread ->
                    val threadId = thread.id.value
                    val count = listedCounts[threadId] ?: thread.numberOfReplies
                    val previous = repliesRequestedAt.put(threadId, count)
                    if (previous == count) return@mapNotNull null
                    repliesReading += threadId
                    // A count that went down (a redaction) still reads the newest reply.
                    val wanted = repliesReadAt[threadId]?.let { (count - it).coerceAtLeast(1).toInt() }
                    ThreadRepliesRead(threadId, count, wanted, newestRepliesRead[threadId].orEmpty(), isFirstRead = previous == null)
                }
        }
        for ((threadId, count, wanted, readBefore, isFirstRead) in fresh) {
            val key = "$threadId$THREAD_KEY_SUFFIX"
            val read = suspend { fetchRelated(threadId, RawRelationType.Thread, wanted, readBefore) }
            val onDone = {
                synchronized(repliesRequestedAt) { repliesReading -= threadId }
                requestReplies(_snapshot.value.threads)
            }
            launchRelationsRead(key, isFirstRead, read, onDone) { replies ->
                synchronized(repliesRequestedAt) {
                    repliesReadAt.merge(threadId, count, ::maxOf)
                    val ids = replies.take(RELATIONS_PAGE_SIZE).mapNotNull { it.eventId } + newestRepliesRead[threadId].orEmpty()
                    newestRepliesRead[threadId] = ids.distinct().take(RELATIONS_PAGE_SIZE)
                }
                val repliers = TaskParticipants.repliers(replies)[threadId].orEmpty()
                threadRepliers.update { it + (threadId to it[threadId].orEmpty() + repliers) }
                // A read of every reply (below the page cap) counts them; a later one adds the newer ones.
                val readsAll = wanted == null && replies.size < MAX_RELATION_PAGES * RELATIONS_PAGE_SIZE
                readReplies.update { read ->
                    val known = read[threadId]
                    if (readsAll || known != null) read + (threadId to (known ?: ReadReplies()).plus(replies, threadId)) else read
                }
                // Only the hidden bootstrap replies are kept with the Matcha events. A later read adds to the earlier ones: it no longer
                // reaches the thread's first reply.
                val bootstrap = replies.filter { event -> event.type == MatchaConfig.EVENT_THREAD_METADATA }
                relationEvents.update { it + (key to (it[key].orEmpty() + bootstrap).distinctBy { event -> event.eventId ?: event }) }
            }
        }
    }

    /**
     * Runs [read] a few at a time, then [onRead] with what it read, then [onDone] whether it succeeded or not; a first read is waited
     * for by [MatchaRoomSnapshot.isLoaded].
     */
    private fun launchRelationsRead(
        key: String,
        isFirstRead: Boolean,
        read: suspend () -> Result<List<RawEvent>>,
        onDone: () -> Unit = {},
        onRead: (List<RawEvent>) -> Unit,
    ) {
        if (isFirstRead) anchorsLoading.update { it + key }
        coroutineScope.launch {
            try {
                anchorPermits.withPermit { withContext(computation ?: EmptyCoroutineContext) { read() } }
                    .onSuccess(onRead)
                    .onFailure { error ->
                        if (error is UnsupportedOperationException) _relationsUnavailable.value = true else readFailed.value = true
                        Timber.w(error, "Could not read the events related to a Matcha anchor")
                    }
            } finally {
                if (isFirstRead) anchorsLoading.update { it - key }
                onDone()
            }
        }
    }

    /**
     * Reads the events related to [eventId] by [relation], newest first, page by page: all of them, or only the newer ones. A read
     * that knows some of the newest events [readBefore] pages until it reaches one of them, as events sent after the [wanted] ones
     * were counted push those further back; its first page asks for one more than [wanted], so it usually reaches one there.
     * Without [readBefore], it stops once [wanted] events are read.
     *
     * ponytail: a fixed page cap bounds very long histories (MAX_RELATION_PAGES × 100 events); add paging on demand if one anchor
     * ever needs more.
     */
    private suspend fun fetchRelated(
        eventId: String,
        relation: RawRelationType,
        wanted: Int? = null,
        readBefore: List<String> = emptyList(),
    ): Result<List<RawEvent>> {
        val events = ArrayList<RawEvent>()
        var from: String? = null
        var fetched = 0
        val toFetch = wanted?.plus(if (readBefore.isEmpty()) 0 else 1)
        repeat(MAX_RELATION_PAGES) {
            val limit = toFetch?.minus(fetched)?.takeIf { it > 0 }?.coerceAtMost(RELATIONS_PAGE_SIZE) ?: RELATIONS_PAGE_SIZE
            val page = room.fetchRelatedEventsJson(EventId(eventId), relation, from, limit)
                .getOrElse { return Result.failure(it) }
            fetched += page.events.size
            val read = page.events.mapNotNull { RawEvent.parse(it) }
            events += read
            val isDone = if (readBefore.isEmpty()) toFetch != null && fetched >= toFetch else read.any { it.eventId in readBefore }
            from = page.nextBatch?.takeUnless { isDone } ?: return Result.success(events)
        }
        Timber.w("Stopped reading the events related to a Matcha anchor after %d pages", MAX_RELATION_PAGES)
        return Result.success(events)
    }

    private data class ThreadRepliesRead(val threadId: String, val count: Long, val wanted: Int?, val readBefore: List<String>, val isFirstRead: Boolean)

    /** The thread list, the repliers read from each thread, and the members of a direct chat. */
    private data class ThreadData(
        val items: List<ThreadListItem>,
        val repliers: Map<String, Set<String>>,
        val directMembers: Set<String>,
        val readReplies: Map<String, ReadReplies>,
    )

    /** What was read of a thread's replies: their ids and the time of the latest. */
    private data class ReadReplies(val ids: Set<String> = emptySet(), val latestTimestamp: Long? = null) {
        /** With the replies of the thread [threadId] among [events]. */
        fun plus(events: List<RawEvent>, threadId: String): ReadReplies {
            val replies = events.filter { !it.isRedacted && it.eventId != null && it.content.relationTarget(REL_TYPE_THREAD) == threadId }
            val latest = (replies.mapNotNull { it.timestamp } + listOfNotNull(latestTimestamp)).maxOrNull()
            return ReadReplies(ids + replies.mapNotNull { it.eventId }, latest)
        }
    }

    /**
     * This thread with the count and latest time of the replies read, unchanged when none could be read. The thread list's latest
     * replier stays: whichever reply it took for the latest, its sender replied.
     */
    private fun ThreadListItem.withReplies(read: ReadReplies?): ThreadListItem {
        if (read == null || read.ids.isEmpty()) return this
        val latest = read.latestTimestamp?.let { timestamp -> latestEvent?.copy(timestamp = timestamp) } ?: latestEvent
        return copy(numberOfReplies = read.ids.size.toLong(), latestEvent = latest)
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
            .map { states -> KanbanProtocol.parseBoardPointerJson(states[""]) }
        val legacy = room.getStateEventsJson(StateEventType.Custom(MatchaConfig.STATE_THREAD_ARCHIVE)).getOrNull()
            .orEmpty()
            .mapNotNull { (key, content) ->
                val archived = content.toJsonObjectOrNull()?.booleanOrNull("archived")
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

    /** The homeserver has answered whether the room has a board pointer, so [readBoardPointer]'s null proves there is none. */
    val isServerPointerRead: Boolean get() = storedState.value.isServerRead

    /**
     * The board pointer from the homeserver: the root id, or null when the room has none. Returns null itself when the Rust SDK
     * in use can't fetch state from the homeserver (it needs the Matcha SDK patch).
     */
    private suspend fun fetchServerPointer(): Result<String?>? {
        val read = room.fetchStateEventContentJson(MatchaConfig.STATE_KANBAN, "")
        if (read.exceptionOrNull() is UnsupportedOperationException) return null
        return read
            .onFailure { Timber.w(it, "Could not fetch the board pointer from the homeserver") }
            .map(KanbanProtocol::parseBoardPointerJson)
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
        threadData: ThreadData,
        state: StoredState,
        permissions: MatchaPermissions,
        status: Status,
    ): MatchaRoomSnapshot {
        val health = status.health
        val threadList = threadData.items.map { it.withReplies(threadData.readReplies[it.threadId.value]) }
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
        val (threads, board) = projectThreadsAndBoard(events, boardRootId, threadList, threadData.repliers, state.legacyArchive, threadData.directMembers)
        val historyRead = !status.pagination.hasMoreToLoad || status.pagesRead >= INITIAL_HISTORY_PAGES
        val threadsRead = status.threads is ThreadListPaginationStatus.Idle
        // An undecryptable event's relation stays readable: one referring to the board or a thread is Matcha data.
        val matchaTargets = threads.mapTo(HashSet()) { it.id.value } + listOfNotNull(boardRootId)
        val unreadableCauses = events
            .filter { it.type == DocsProtocol.EVENT_ROOM_ENCRYPTED && it.content.referenceTarget() in matchaTargets }
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
            isDirect = threadData.directMembers.isNotEmpty(),
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
            if (readsDocumentAnchor) requestDocumentAnchor()
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
            val content = contentJson.toJsonObjectOrNull() ?: return@onSuccess
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
        room.sendRawStateEvent(eventType, stateKey, contentJson).onSuccess {
            // The Tasks tab may remember that this chat had no board; it has one now.
            if (eventType == MatchaConfig.STATE_KANBAN) NoBoardAnswers.pointed(room.sessionId, room.roomId)
            readStoredState()
        }
    }

    /** Shows the board this client created until its pointer is read back from the room. */
    fun rememberCreatedBoard(rootId: String) {
        storedState.update { it.copy(createdBoardRoot = rootId) }
    }

    companion object {
        /** Pages of 50 events read when the store opens. */
        const val INITIAL_HISTORY_PAGES = 10
        private const val PAGINATION_ATTEMPTS = 5
        private const val MAX_CONCURRENT_ANCHORS = 4
        private const val RELATIONS_PAGE_SIZE = 100
        private const val MAX_RELATION_PAGES = 50
        private const val THREAD_KEY_SUFFIX = "#thread"
        private val ANCHORS_TIMEOUT = 30.seconds
    }
}

/** A room's threads, most recently active first, and its board, if it has one. */
internal data class ThreadsAndBoard(val threads: List<MatchaThread>, val board: MatchaBoard?)

/**
 * The threads and board of a room's snapshot, from its Matcha events (newest first), the board it has, the thread list, the repliers
 * read from each thread and the legacy archive state. The protocol conformance fixtures (`docs/matcha/fixtures/kanban/board.json`)
 * run this function, so the other clients can project boards exactly like this one.
 */
internal fun projectThreadsAndBoard(
    events: List<RawEvent>,
    boardRootId: String?,
    threadList: List<ThreadListItem>,
    threadRepliers: Map<String, Set<String>>,
    legacyArchive: Map<String, Boolean>,
    directMembers: Set<String>,
): ThreadsAndBoard {
    val revisions = boardRootId?.let { root -> events.mapNotNull { KanbanProtocol.parseCard(it, root) } }.orEmpty()
    val latestCards = KanbanProtocol.latestRevisions(revisions)

    val patchesByRoot = HashMap<String, MutableList<ThreadMetadataPatch>>()
    for (event in events.filter { it.type == MatchaConfig.EVENT_THREAD_METADATA }) {
        val root = event.content.referenceTarget() ?: continue
        ThreadMetadataProtocol.parsePatch(event, root)?.let { patchesByRoot.getOrPut(root) { mutableListOf() } += it }
    }
    val listed = threadList.associateBy { it.threadId.value }
    val bootstrapped = events.mapNotNullTo(HashSet(), ThreadMetadataProtocol::bootstrapRootId)
    // The hidden replies this client sent are among the Matcha events before the thread's replies are read again.
    val eventRepliers = TaskParticipants.repliers(events)
    val threadIds = LinkedHashSet<String>().apply {
        addAll(listed.keys)
        latestCards.mapNotNullTo(this) { it.threadId }
        addAll(legacyArchive.filterValues { it }.keys)
    }
    val threads = threadIds.map { id ->
        val item = listed[id]
        val rootBody = (item?.rootEvent?.content as? MessageContent)?.body
            ?: latestCards.firstOrNull { it.threadId == id }?.title
        MatchaThread(
            id = ThreadId(id),
            metadata = ThreadMetadataProtocol.resolve(id, rootBody, patchesByRoot[id].orEmpty(), legacyArchive[id]),
            rootBody = rootBody,
            numberOfReplies = item?.numberOfReplies ?: 0,
            lastActivityTimestamp = item?.let { it.latestEvent?.timestamp ?: it.rootEvent.timestamp } ?: 0,
            rootTimestamp = item?.rootEvent?.timestamp ?: 0,
            isRootUnreadable = item?.rootEvent?.content is UnableToDecryptContent,
            hasHiddenFirstReply = id in bootstrapped,
            rootSenderId = item?.rootEvent?.senderId?.value,
            participants = buildSet {
                item?.rootEvent?.senderId?.let { add(it.value) }
                item?.latestEvent?.senderId?.let { add(it.value) }
                addAll(threadRepliers[id].orEmpty())
                addAll(eventRepliers[id].orEmpty())
            },
        )
    }.sortedByDescending { it.lastActivityTimestamp }

    val board = boardRootId?.let { root ->
        val threadParticipants = threads.associate { it.id.value to it.participants }
        val standaloneAuthors = TaskParticipants.standaloneAuthors(events, root)
        val cards = KanbanProtocol.project(latestCards, threads.associate { it.id.value to it.metadata }).map { card ->
            card.copy(participants = TaskParticipants.of(card, threadParticipants, standaloneAuthors, directMembers))
        }
        MatchaBoard(rootId = root, cards = cards)
    }
    return ThreadsAndBoard(threads, board)
}

/**
 * The events without a repeated event id, the first of each kept, unless it can't be decrypted and a later copy can: a card read
 * through relations before its key arrived gives way to the scan's decrypted copy.
 */
private fun List<RawEvent>.distinctReadable(): List<RawEvent> {
    val byId = LinkedHashMap<Any, RawEvent>()
    for (event in this) {
        val key = event.eventId ?: event
        val kept = byId[key]
        if (kept == null || kept.type == DocsProtocol.EVENT_ROOM_ENCRYPTED) byId[key] = event
    }
    return byId.values.toList()
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
