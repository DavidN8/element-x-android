/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.taskstab

import io.element.android.appconfig.MatchaConfig
import io.element.android.features.matcha.impl.protocol.KanbanProtocol
import io.element.android.features.matcha.impl.store.MatchaRoomSnapshot
import io.element.android.features.matcha.impl.store.MatchaRoomStore
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.CurrentUserMembership
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.room.StateEventType
import io.element.android.libraries.matrix.api.roomlist.RoomList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.coroutineScope
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import timber.log.Timber
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
 * The chats the Tasks tab reads. [run] checks every joined chat that isn't a space for a task board, a few at a time, keeps a
 * [MatchaRoomStore] open for each chat that has one, and destroys every room it opened once the user leaves that chat or [run] ends.
 * A chat has a board when its board pointer is set, as on the web client. A pointer in the stored room state proves it, but the
 * sync only stores the custom state it asked for, so a chat without one is read like the board does: it has a board when its
 * store finds one in the history it reads first.
 *
 * It runs on the caller's dispatcher (the main thread, for a composition), which is the only one that changes its state.
 *
 * ponytail: at most [MAX_CONCURRENT_CHECKS] chats are opened and read at once, but every chat without a stored pointer has its
 * recent history read each time the tab is shown, a board older than that history is missed (the chat's own board finds it),
 * every chat with a board stays open while the tab is shown, and a chat without one is only checked again the next time.
 * A session-wide cache of board presence and stores, and reading the pointer from the server (Path B), would lift these ceilings.
 */
internal class TasksTabRooms(private val client: MatrixClient) {
    private sealed interface Check {
        data object Pending : Check

        data object NoBoard : Check

        data object Failed : Check

        data class Board(val room: TabRoom) : Check
    }

    private data class ChatInfo(val name: String?, val avatarUrl: String?)

    private val json = Json { ignoreUnknownKeys = true }
    private val checks = MutableStateFlow<Map<RoomId, Check>>(emptyMap())

    /** The joined chats, or null until the room list has loaded. */
    private val joinedChats = MutableStateFlow<Map<RoomId, ChatInfo>?>(null)
    private val retries = MutableStateFlow(0)

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

    /** Follows the user's joined chats until cancelled, then destroys every room it opened. */
    suspend fun run() {
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
            checks.value.keys.forEach(::close)
            joinedChats.value = null
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
        val room = client.getJoinedRoom(roomId)
        if (room == null) {
            Timber.w("Could not open %s to look for its board", roomId)
            record(roomId, Check.Failed)
            return
        }
        var isKept = false
        try {
            val hasStoredPointer = room.getStateEventsJson(StateEventType.Custom(MatchaConfig.STATE_KANBAN))
                .onFailure { Timber.w(it, "Could not read the board pointer of %s", roomId) }
                .getOrNull()
                ?.get("")
                ?.let(::boardRootId) != null
            val store = MatchaRoomStore(room, room.roomCoroutineScope)
            fun keep() = record(roomId, Check.Board(TabRoom(room, store, newWriteScope())))
            // Either way, keep the permit while the store reads the chat's history, so only a few chats read at once.
            if (hasStoredPointer) {
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

    private fun newWriteScope() = client.sessionCoroutineScope + SupervisorJob(client.sessionCoroutineScope.coroutineContext[Job])

    private fun boardRootId(pointerJson: String): String? =
        runCatchingExceptions { json.parseToJsonElement(pointerJson).jsonObject }.getOrNull()?.let(KanbanProtocol::parseBoardPointer)

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
        private val STORE_LOAD_TIMEOUT = 30.seconds
    }
}
