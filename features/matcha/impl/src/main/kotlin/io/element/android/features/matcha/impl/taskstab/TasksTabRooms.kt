/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.taskstab

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.appconfig.MatchaConfig
import io.element.android.features.matcha.impl.protocol.KanbanProtocol
import io.element.android.features.matcha.impl.store.MatchaRoomSnapshot
import io.element.android.features.matcha.impl.store.MatchaRoomStore
import io.element.android.features.matcha.impl.store.NoBoardAnswers
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.BaseRoom
import io.element.android.libraries.matrix.api.room.CurrentUserMembership
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.room.StateEventType
import io.element.android.libraries.matrix.api.roomlist.RoomList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/** A chat with a board, held open by the Tasks tab. */
internal data class TabRoom(
    val room: JoinedRoom,
    val store: MatchaRoomStore,
    /** Writes started from the tab; the room is destroyed only once they are done. */
    val writeScope: CoroutineScope,
)

/** A chat with a board and what its store has read. */
internal data class TabChat(
    val roomId: RoomId,
    val name: String?,
    val avatarUrl: String?,
    val room: TabRoom,
    val snapshot: MatchaRoomSnapshot,
)

/** What the Tasks tab knows about the user's chats. */
internal data class TabChats(
    /** The joined chats with a board, in the room list's order. */
    val boards: List<TabChat> = emptyList(),
    /** Chats still being checked for a board. */
    val checkingCount: Int = 0,
    /** Chats that could not be checked. */
    val failedCount: Int = 0,
    /** False until the room list has loaded, so a chat that isn't listed isn't known to be absent yet. */
    val isRoomListLoaded: Boolean = false,
)

/**
 * The chats the Tasks tab reads, one per session. [run] checks every joined chat that isn't a space for a task board, a few at a
 * time, keeps a [MatchaRoomStore] open for each chat that has one, and destroys every room it opened once the user leaves that chat,
 * or [KEEP_ALIVE] after [run] ends unless it runs again: coming back to the tab, or from a task's thread, finds every board read.
 * A chat has a board when its board pointer is set, as on the web client. A pointer in the stored room state proves it, but the
 * sync only stores the custom state it asked for, so a chat without one is read like the board does: it has a board when its
 * store finds one in the history it reads first.
 *
 * It runs on the caller's dispatcher (the main thread, for a composition) and the session's (also main), the only ones that change its
 * state; one [run] goes at a time.
 *
 * ponytail: at most [MAX_CONCURRENT_CHECKS] chats are opened and read at once, but every chat without a stored pointer has its
 * recent history read each time the tab is shown after [KEEP_ALIVE], a board older than that history is missed (the chat's own board
 * finds it), every chat with a board stays open while the tab is shown and for [KEEP_ALIVE] after, and a chat without one is only
 * checked again the next time. Remembering board presence too, and reading the pointer from the server (Path B), would lift these.
 */
@SingleIn(SessionScope::class)
internal class TasksTabRooms(
    private val client: MatrixClient,
    /** Monotonic milliseconds, for how long a "no board" answer is reused. */
    private val clock: () -> Long = NoBoardAnswers::now,
    /** Where each board's store parses events and builds snapshots, see [MatchaRoomStore]. */
    private val computation: CoroutineDispatcher? = null,
) {
    @Inject
    constructor(client: MatrixClient, dispatchers: CoroutineDispatchers) : this(client, NoBoardAnswers::now, dispatchers.computation)

    private sealed interface Check {
        data object Pending : Check

        data object NoBoard : Check

        data object Failed : Check

        data class Board(val room: TabRoom) : Check
    }

    private data class ChatInfo(val name: String?, val avatarUrl: String?)

    private val checks = MutableStateFlow<Map<RoomId, Check>>(emptyMap())

    /** The joined chats, or null until the room list has loaded. */
    private val joinedChats = MutableStateFlow<Map<RoomId, ChatInfo>?>(null)
    private val retries = MutableStateFlow(0)
    private val runLock = Mutex()

    /** Closes everything once the tab has been gone for [KEEP_ALIVE]; the next [run] cancels it. */
    private var closeJob: Job? = null

    @OptIn(ExperimentalCoroutinesApi::class)
    val chats: Flow<TabChats> = combine(checks, joinedChats, ::Pair).flatMapLatest { (checks, joined) ->
        val boards = joined.orEmpty().mapNotNull { (roomId, info) ->
            val room = (checks[roomId] as? Check.Board)?.room ?: return@mapNotNull null
            room.store.snapshot.map { TabChat(roomId, info.name, info.avatarUrl, room, it) }
        }
        val checking = checks.values.count { it == Check.Pending }
        val failed = checks.values.count { it == Check.Failed }
        val snapshots = if (boards.isEmpty()) flowOf(emptyList()) else combine(boards) { it.toList() }
        snapshots.map { TabChats(boards = it, checkingCount = checking, failedCount = failed, isRoomListLoaded = joined != null) }
    }

    /** Checks the chats again that could not be checked, and opens again the boards that failed to open. */
    fun retry() = retries.update { it + 1 }

    /** Follows the user's joined chats until cancelled, then destroys every room it opened once [KEEP_ALIVE] passes without another run. */
    suspend fun run() = runLock.withLock {
        closeJob?.cancel()
        closeJob = null
        val semaphore = Semaphore(MAX_CONCURRENT_CHECKS)
        val jobs = HashMap<RoomId, Job>()
        var handledRetries = retries.value
        try {
            coroutineScope {
                fun startCheck(roomId: RoomId) {
                    checks.update { it + (roomId to Check.Pending) }
                    jobs[roomId] = launch { semaphore.withPermit { check(roomId) } }
                }
                combine(joinedChatsFlow(), retries, ::Pair).collect { (joined, retry) ->
                    joinedChats.value = joined
                    // A chat the user left drops out at once, with its tasks.
                    for (roomId in checks.value.keys - joined.keys) {
                        jobs.remove(roomId)?.cancel()
                        close(roomId)
                    }
                    for (roomId in joined.keys - checks.value.keys) startCheck(roomId)
                    if (retry != handledRetries) {
                        handledRetries = retry
                        for ((roomId, check) in checks.value) {
                            when (check) {
                                Check.Failed -> startCheck(roomId)
                                is Check.Board -> check.room.store.retryLoad()
                                else -> Unit
                            }
                        }
                    }
                }
            }
        } finally {
            // A check cut short is done again next time, rather than counting as checking for ever.
            checks.update { current -> current.filterValues { it != Check.Pending } }
            closeJob = client.sessionCoroutineScope.launch {
                delay(KEEP_ALIVE)
                // Everything goes, chats without a board too, so a board made meanwhile is found next time.
                checks.value.keys.forEach(::close)
                joinedChats.value = null
            }
        }
    }

    private fun joinedChatsFlow(): Flow<Map<RoomId, ChatInfo>> {
        val roomList = client.roomListService.allRooms
        return combine(roomList.loadingState, roomList.summaries) { loadingState, summaries ->
            summaries.takeIf { loadingState is RoomList.LoadingState.Loaded }
        }
            .filterNotNull()
            .map { summaries ->
                summaries
                    .filter { it.info.currentUserMembership == CurrentUserMembership.JOINED && !it.info.isSpace }
                    .associate { it.roomId to ChatInfo(name = it.info.name, avatarUrl = it.info.avatarUrl) }
            }
            .distinctUntilChanged()
    }

    private suspend fun check(roomId: RoomId) {
        fun recordUnopened() {
            Timber.w("Could not open %s to look for its board", roomId)
            record(roomId, Check.Failed)
        }
        // The pointer is read from a room without a timeline; only a chat that has or may have a board is opened in full.
        val pointerRoom = client.getRoom(roomId) ?: return recordUnopened()
        val (hasStoredPointer, serverPointer) = try {
            val isStored = pointerRoom.getStateEventsJson(StateEventType.Custom(MatchaConfig.STATE_KANBAN))
                .onFailure { Timber.w(it, "Could not read the board pointer of %s", roomId) }
                .getOrNull()
                ?.get("")
                ?.let(KanbanProtocol::parseBoardPointerJson) != null
            // The sync doesn't store the pointer, so ask the homeserver; a chat without a board is then skipped unread.
            isStored to (if (isStored) ServerPointer.Unknown else readServerPointer(pointerRoom) ?: return)
        } finally {
            pointerRoom.destroy()
        }
        if (serverPointer == ServerPointer.Absent) {
            record(roomId, Check.NoBoard)
            return
        }
        val room = client.getJoinedRoom(roomId) ?: return recordUnopened()
        var isKept = false
        try {
            // The store starts from the homeserver's answer rather than asking again, so a failed second read can't lose the board.
            val store = MatchaRoomStore(
                room = room,
                coroutineScope = room.roomCoroutineScope,
                knownServerPointer = (serverPointer as? ServerPointer.Present)?.rootId,
                computation = computation,
            )
            fun keep() = record(roomId, Check.Board(TabRoom(room, store, newWriteScope())))
            // Either way, keep the permit while the store reads the chat's history, so only a few chats read at once.
            if (hasStoredPointer || serverPointer is ServerPointer.Present) {
                isKept = keep()
                if (isKept) withTimeoutOrNull(STORE_LOAD_TIMEOUT) { store.snapshot.first { it.isLoaded || it.loadFailed } }
                return
            }
            val loaded = withTimeoutOrNull(STORE_LOAD_TIMEOUT) { store.snapshot.first { it.isLoaded || it.loadFailed } }
            when {
                loaded == null || loaded.loadFailed -> record(roomId, Check.Failed)
                loaded.board == null -> record(roomId, Check.NoBoard)
                else -> isKept = keep()
            }
        } finally {
            if (!isKept) room.destroy()
        }
    }

    /** What the homeserver says about a chat's board pointer. */
    private sealed interface ServerPointer {
        data class Present(val rootId: String) : ServerPointer

        data object Absent : ServerPointer

        /** The Rust SDK in use can't ask (it needs the Matcha SDK patch), so the store reads the chat's history instead. */
        data object Unknown : ServerPointer
    }

    /**
     * Asks the homeserver whether [room] has a board pointer; returns null after recording a failed check. A recent "no board"
     * answer is reused (see [NoBoardAnswers]), so showing the tab again doesn't ask every chat again.
     */
    private suspend fun readServerPointer(room: BaseRoom): ServerPointer? {
        if (NoBoardAnswers.isRecent(room.sessionId, room.roomId, clock())) return ServerPointer.Absent
        val read = room.fetchStateEventContentJson(MatchaConfig.STATE_KANBAN, "")
        return when {
            read.exceptionOrNull() is UnsupportedOperationException -> ServerPointer.Unknown
            read.isFailure -> {
                Timber.w(read.exceptionOrNull(), "Could not fetch the board pointer of %s", room.roomId)
                record(room.roomId, Check.Failed)
                null
            }
            else -> {
                val rootId = KanbanProtocol.parseBoardPointerJson(read.getOrNull())
                if (rootId != null) {
                    NoBoardAnswers.forget(room.sessionId, room.roomId)
                    ServerPointer.Present(rootId)
                } else {
                    NoBoardAnswers.record(room.sessionId, room.roomId, clock())
                    ServerPointer.Absent
                }
            }
        }
    }

    private fun newWriteScope() = client.sessionCoroutineScope + SupervisorJob(client.sessionCoroutineScope.coroutineContext[Job])

    /** Records [check] unless the chat was dropped meanwhile; returns whether it was recorded. */
    private fun record(roomId: RoomId, check: Check): Boolean {
        var isRecorded = false
        checks.update { current ->
            isRecorded = roomId in current
            if (isRecorded) current + (roomId to check) else current
        }
        return isRecorded
    }

    private fun close(roomId: RoomId) {
        val check = checks.value[roomId]
        checks.update { it - roomId }
        val room = (check as? Check.Board)?.room ?: return
        val writes = room.writeScope.coroutineContext.job
        // A write the user started still finishes, and lasts while its snackbar shows; the room goes once nothing uses it, so an
        // Undo or Retry tapped after the user left the tab still writes.
        client.sessionCoroutineScope.launch {
            while (true) {
                val running = writes.children.toList().takeIf { it.isNotEmpty() } ?: break
                running.joinAll()
            }
            writes.cancel()
            room.room.destroy()
        }
    }

    companion object {
        const val MAX_CONCURRENT_CHECKS = 4

        /** How long the boards stay open after the tab is left. */
        val KEEP_ALIVE = 2.minutes
        private val STORE_LOAD_TIMEOUT = 30.seconds
    }
}
