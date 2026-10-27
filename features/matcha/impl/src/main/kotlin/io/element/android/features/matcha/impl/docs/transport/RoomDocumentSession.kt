/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs.transport

import io.element.android.appconfig.MatchaConfig
import io.element.android.features.matcha.impl.docs.DocFrameResult
import io.element.android.features.matcha.impl.docs.DocPacket
import io.element.android.features.matcha.impl.docs.DocTooLargeException
import io.element.android.features.matcha.impl.docs.DocsProtocol
import io.element.android.features.matcha.impl.docs.engine.TextDiff
import io.element.android.features.matcha.impl.docs.engine.YjsDocument
import io.element.android.features.matcha.impl.docs.engine.YjsEngine
import io.element.android.features.matcha.impl.docs.engine.YjsEngineException
import io.element.android.features.matcha.impl.protocol.RawEvent
import io.element.android.features.matcha.impl.store.MatchaRoomStore
import io.element.android.features.networkmonitor.api.NetworkMonitor
import io.element.android.features.networkmonitor.api.NetworkStatus
import io.element.android.libraries.matrix.api.room.CurrentUserMembership
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.room.MessageEventType
import io.element.android.libraries.matrix.api.room.StateEventType
import io.element.android.libraries.matrix.api.room.powerlevels.permissionsFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.JsonObject
import timber.log.Timber
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Failures that must never be shown as an empty or saved document. Same codes as the web client. */
enum class DocumentError {
    LoadError,
    DecryptError,
    InvalidUpdate,
    IncompleteUpdate,
    PermissionError,
    SendError,
    TooLargeError,
    AnchorError,
}

enum class DocumentSync { Loading, Saved, Saving, Offline, Error }

data class DocumentSessionState(
    /** The whole history has been replayed and nothing is missing. */
    val isReady: Boolean,
    val canEdit: Boolean,
    val sync: DocumentSync,
    /** Local updates not yet acknowledged by the server. */
    val pendingUpdates: Int,
    val error: DocumentError?,
    val text: String,
    /** The raw entries of the "documents" map, for the catalog. */
    val entries: JsonObject,
)

private class DocumentException(val error: DocumentError, cause: Throwable? = null) : Exception(error.name, cause)

/**
 * One shared document of a room, synced like the web client's `RoomDocumentSession`: every `com.matchahoneylatte.docs.update`
 * frame related to the room's `m.room.create` event is replayed from the store (once it has read that event's relations) before
 * editing is allowed; complete updates are deduplicated by sender and update id and checked by the engine before they apply;
 * local edits are debounced for 500 ms, merged, split into frames and only count as saved once the server accepted every frame.
 * Unsent edits live in memory only.
 *
 * Known difference: [JoinedRoom.sendRawEvent] can't reuse transaction ids, so retrying a frame whose first attempt reached the
 * server sends it twice. That is harmless: the copies are identical, and receivers deduplicate by sender and update id.
 */
class RoomDocumentSession(
    private val documentId: String,
    private val room: JoinedRoom,
    private val store: MatchaRoomStore,
    private val engine: YjsEngine,
    private val networkMonitor: NetworkMonitor,
    parentScope: CoroutineScope,
    private val isReadOnly: Boolean = false,
    private val newUpdateId: () -> String = { UUID.randomUUID().toString() },
) {
    private val scope = CoroutineScope(parentScope.coroutineContext + SupervisorJob(parentScope.coroutineContext.job))
    private val lock = Mutex()

    private var document: YjsDocument? = null
    private var anchor: String? = null
    private var isLoading = true

    /** The whole history has been replayed; [publish] also checks that no update is missing. */
    private var isReady = false
    private var error: DocumentError? = null
    private var text = ""
    private var entries = JsonObject(emptyMap())
    private var isMissingInEngine = false
    private val buffered = mutableListOf<ByteArray>()
    private val packets = ArrayDeque<OutgoingPacket>()
    private val incoming = HashMap<String, DocPacket>()
    private val applied = HashSet<String>()
    private val seen = HashSet<String>()
    private var loadJob: Job? = null
    private var flushJob: Job? = null
    private var debounceJob: Job? = null

    /** An update being sent; [unsent] shrinks as the server accepts each frame. */
    private class OutgoingPacket(val key: String, val update: ByteArray, frames: List<String>) {
        val unsent = ArrayDeque(frames)
    }

    /** Joined, allowed to send document frames, and to send encrypted events in an encrypted room. */
    private val canSend: StateFlow<Boolean> = combine(
        room.roomInfoFlow.map { (it.currentUserMembership == CurrentUserMembership.JOINED) to (it.isEncrypted == true) }.distinctUntilChanged(),
        room.permissionsFlow(false to false) { permissions ->
            permissions.canOwnUserSendMessage(MessageEventType.Other(MatchaConfig.EVENT_DOCS_UPDATE)) to
                permissions.canOwnUserSendMessage(MessageEventType.RoomEncrypted)
        },
    ) { (isJoined, isEncrypted), (canSendFrames, canSendEncrypted) ->
        isJoined && canSendFrames && (!isEncrypted || canSendEncrypted)
    }.stateIn(scope, SharingStarted.Eagerly, false)

    private val _state = MutableStateFlow(
        DocumentSessionState(
            isReady = false,
            canEdit = false,
            sync = DocumentSync.Loading,
            pendingUpdates = 0,
            error = null,
            text = "",
            entries = JsonObject(emptyMap()),
        )
    )
    val state: StateFlow<DocumentSessionState> = _state.asStateFlow()

    init {
        canSend.onEach { allowed ->
            if (!allowed && hasPending()) error = DocumentError.PermissionError
            publish()
        }.launchIn(scope)
        networkMonitor.connectivity.onEach { status ->
            publish()
            if (status == NetworkStatus.Connected && error == DocumentError.SendError) startFlush()
        }.launchIn(scope)
        store.snapshot.map { it.events }.distinctUntilChanged().onEach(::onEvents).launchIn(scope)
        retry()
    }

    /** Replays the whole history again, keeping unsent edits, then sends them. Used to recover from any error. */
    fun retry(): Job {
        loadJob?.takeIf { it.isActive }?.let { return it }
        isLoading = true
        isReady = false
        error = null
        publish()
        return scope.launch { load() }.also { loadJob = it }
    }

    /** Applies an edit of the text field, from [before] to [after]; edits that arrived meanwhile are kept. */
    suspend fun edit(before: String, after: String) {
        lock.withLock {
            val document = document ?: return
            if (!_state.value.canEdit) return
            val changes = TextDiff.rebase(TextDiff.changes(before, after), before, text)
            if (changes.isEmpty()) return
            val update = try {
                document.edit(changes)
            } catch (e: YjsEngineException) {
                fail(e)
                null
            }
            if (error == null || error == DocumentError.SendError) {
                text = TextDiff.apply(text, changes)
                update?.let(buffered::add)
            }
        }
        publish()
        scheduleFlush()
    }

    /** Sets an entry of the catalog's "documents" map; call [flush] to save it. False when it could not be set. */
    suspend fun setEntry(key: String, value: JsonObject): Boolean {
        val isSet = lock.withLock {
            val document = document?.takeIf { _state.value.canEdit } ?: return@withLock false
            try {
                document.setEntry(key, value)?.let(buffered::add)
                entries = document.snapshot().entries
                true
            } catch (e: YjsEngineException) {
                fail(e)
                false
            }
        }
        publish()
        return isSet
    }

    /** Sends unsent edits now and waits until they are acknowledged or failed. */
    suspend fun flush() {
        debounceJob?.cancel()
        startFlush()?.join()
    }

    fun close() {
        scope.cancel()
        document?.close()
        document = null
    }

    private suspend fun load() {
        try {
            awaitFrames()
            val anchor = anchor ?: findAnchor() ?: throw DocumentException(DocumentError.AnchorError)
            lock.withLock {
                val events = store.snapshot.value.events.asReversed()
                this.anchor = anchor
                val document = document ?: openDocument()
                ingestAll(document, anchor, events)
                refresh(document)
                isReady = true
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            fail(e)
        } finally {
            isLoading = false
            publish()
        }
        // Frames that arrived while loading, then the edits kept from before.
        onEvents(store.snapshot.value.events)
        if (isReady && hasPending()) startFlush()
    }

    /**
     * Waits until the store holds every frame it can read. Frames relate to the anchor, whose relations the store reads in full; this
     * SDK's timelines drop custom events, so older history would only add undecryptable copies of frames the relations hold too.
     * Without the SDK's relations, or when the store warns that some events are unreadable or a read failed, the whole history is read
     * instead.
     */
    private suspend fun awaitFrames() {
        // Bounded: a failed read cleared by a later one leaves the store unloaded without a warning.
        withTimeoutOrNull(STORE_READ_TIMEOUT) { store.awaitRelations() }
        store.loadHistoryUntil { !store.relationsUnavailable.value && it.isLoaded && it.warning == null }
            .getOrElse { throw DocumentException(DocumentError.LoadError, it) }
    }

    private suspend fun onEvents(events: List<RawEvent>) {
        if (isLoading) return
        var needsReload = false
        lock.withLock {
            val document = document ?: return@withLock
            val anchor = anchor ?: return@withLock
            when (error) {
                DocumentError.DecryptError -> {
                    // The keys arrived: read everything again.
                    needsReload = events.none { DocsProtocol.isUndecryptedFrame(it, anchor) }
                    return@withLock
                }
                DocumentError.LoadError, DocumentError.InvalidUpdate, DocumentError.AnchorError -> return@withLock
                else -> Unit
            }
            try {
                // Only an applied update changes the text, the entries or what is missing; edits update them locally.
                if (ingestAll(document, anchor, events.asReversed())) refresh(document)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e)
            }
        }
        publish()
        if (needsReload) retry()
    }

    /**
     * Ingests [events], oldest first, and applies the updates they complete in one engine call. Returns whether an update was
     * applied. Fails like ingesting the events one at a time would: on the same event, with the same events seen.
     */
    private suspend fun ingestAll(document: YjsDocument, anchor: String, events: List<RawEvent>): Boolean {
        val seenBefore = HashSet(seen)
        val appliedBefore = HashSet(applied)
        val incomingBefore = HashMap(incoming)
        val updates = mutableListOf<ByteArray>()
        val failure = try {
            events.forEach { event -> ingest(anchor, event, updates::add) }
            null
        } catch (e: Exception) {
            e
        }
        val count = if (updates.isEmpty()) 0 else document.applyAll(updates)
        if (count < updates.size) {
            // An update was malformed: go through the events again as if each update had been applied on its own, which fails on it.
            seen.retainAll(seenBefore)
            applied.retainAll(appliedBefore)
            incoming.clear()
            incoming.putAll(incomingBefore)
            var index = 0
            events.forEach { event -> ingest(anchor, event) { index++ < count } }
        }
        failure?.let { throw it }
        return count > 0
    }

    /** Hands the update [event] completes, if any, to [apply], which returns false when the engine refused it. */
    private fun ingest(anchor: String, event: RawEvent, apply: (ByteArray) -> Boolean) {
        val eventId = event.eventId ?: return
        if (eventId in seen) return
        // Not marked as seen: once decrypted, the same event comes back as a frame.
        if (DocsProtocol.isUndecryptedFrame(event, anchor)) throw DocumentException(DocumentError.DecryptError)
        when (val result = DocsProtocol.parseFrame(event, anchor, documentId)) {
            DocFrameResult.Unrelated -> Unit
            DocFrameResult.Invalid -> throw DocumentException(DocumentError.InvalidUpdate)
            is DocFrameResult.Frame -> ingestFrame(result, apply)
        }
        seen += eventId
    }

    private fun ingestFrame(result: DocFrameResult.Frame, apply: (ByteArray) -> Boolean) {
        val frame = result.frame
        val key = DocsProtocol.packetKey(result.senderId, frame.updateId)
        if (key in applied) return
        val packet = (incoming[key] ?: DocPacket(frame.chunks)).plus(frame) ?: throw DocumentException(DocumentError.InvalidUpdate)
        if (!packet.isComplete) {
            incoming[key] = packet
            return
        }
        if (!apply(packet.update())) throw DocumentException(DocumentError.InvalidUpdate)
        applied += key
        incoming -= key
    }

    /**
     * The room's `m.room.create` event, from the room state like the web client, or the one a room id of room version 12 is
     * derived from. Never taken from the frames: any member can relate a frame to any event.
     */
    private suspend fun findAnchor(): String? {
        room.getStateEventId(StateEventType.RoomCreate, "")
            .onFailure { Timber.w(it, "Could not read the create event of the room") }
            .getOrNull()
            ?.let { return it.value }
        return room.roomId.value.takeIf { !it.contains(':') }?.let { "$" + it.removePrefix("!") }
    }

    /** Opens the engine document; after an engine restart this replays everything again, with the local edits kept. */
    private suspend fun openDocument(): YjsDocument {
        val document = engine.open()
        seen.clear()
        incoming.clear()
        applied.clear()
        packets.forEach {
            applied += it.key
            document.apply(it.update)
        }
        buffered.forEach { document.apply(it) }
        this.document = document
        return document
    }

    private suspend fun refresh(document: YjsDocument) {
        val snapshot = document.snapshot()
        text = snapshot.text
        entries = snapshot.entries
        isMissingInEngine = snapshot.isMissingUpdates
    }

    private fun hasMissingUpdates() = isMissingInEngine || incoming.values.any { it.isCommitted && !it.isComplete }

    private fun hasPending() = buffered.isNotEmpty() || packets.isNotEmpty()

    private fun scheduleFlush() {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(DEBOUNCE)
            startFlush()
        }
    }

    private fun startFlush(): Job? {
        if (!isReady || isLoading) return null
        flushJob?.takeIf { it.isActive }?.let { return it }
        return scope.launch { sendPending() }.also { flushJob = it }
    }

    private suspend fun sendPending() {
        try {
            if (!canSend.value) {
                if (hasPending()) error = DocumentError.PermissionError
                return
            }
            if (error in SEND_ERRORS) error = null
            publish()
            while (hasPending()) {
                val packet = packets.firstOrNull() ?: lock.withLock { nextPacket() }
                while (packet.unsent.isNotEmpty()) {
                    if (!canSend.value) throw DocumentException(DocumentError.PermissionError)
                    store.send(MatchaConfig.EVENT_DOCS_UPDATE, packet.unsent.first())
                        .getOrElse { throw DocumentException(DocumentError.SendError, it) }
                    packet.unsent.removeFirst()
                }
                packets.removeFirst()
                publish()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: DocumentException) {
            Timber.w(e, "Document edits remain unsent")
            error = e.error
        } catch (e: YjsEngineException) {
            fail(e)
        } finally {
            publish()
        }
    }

    /** Merges the buffered edits into one update and splits it into frames. */
    private suspend fun nextPacket(): OutgoingPacket {
        val document = document ?: throw DocumentException(DocumentError.LoadError)
        val anchor = anchor ?: throw DocumentException(DocumentError.AnchorError)
        val update = buffered.singleOrNull() ?: document.merge(buffered)
        val updateId = newUpdateId()
        val frames = try {
            DocsProtocol.frames(update, anchor, updateId, documentId)
        } catch (e: DocTooLargeException) {
            throw DocumentException(DocumentError.TooLargeError, e)
        }
        // Already in the local document: our own echo is skipped.
        val packet = OutgoingPacket(DocsProtocol.packetKey(room.sessionId.value, updateId), update, frames)
        packets += packet
        applied += packet.key
        buffered.clear()
        return packet
    }

    private fun fail(e: Throwable) {
        Timber.w(e, "Could not sync the document")
        error = (e as? DocumentException)?.error ?: DocumentError.LoadError
        isReady = false
        if (e is YjsEngineException) {
            document?.close()
            document = null
        }
    }

    private fun publish() {
        val pending = buffered.size + packets.size
        // The causal guard: while an update misses its predecessors the document is incomplete and read-only, even when a send
        // failed meanwhile. Unsent edits are still sent; they only build on what this device has.
        val isMissing = !isLoading && hasMissingUpdates()
        val error = if (isMissing && (error == null || error in SEND_ERRORS)) DocumentError.IncompleteUpdate else error
        val isReady = isReady && !isMissing
        val isOffline = networkMonitor.connectivity.value == NetworkStatus.Disconnected
        _state.value = DocumentSessionState(
            isReady = isReady,
            canEdit = isReady && !isLoading && !isReadOnly && canSend.value && (error == null || error == DocumentError.SendError),
            sync = when {
                isLoading -> DocumentSync.Loading
                error == DocumentError.SendError && isOffline -> DocumentSync.Offline
                error != null -> DocumentSync.Error
                pending > 0 -> DocumentSync.Saving
                else -> DocumentSync.Saved
            },
            pendingUpdates = pending,
            error = error,
            text = text,
            entries = entries,
        )
    }

    private companion object {
        val DEBOUNCE = 500.milliseconds
        val STORE_READ_TIMEOUT = 10.seconds
        val SEND_ERRORS = setOf(DocumentError.SendError, DocumentError.PermissionError, DocumentError.TooLargeError)
    }
}
