/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs.notestab

import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.element.android.features.matcha.impl.docs.SpaceDocsContext
import io.element.android.features.matcha.impl.docs.SpaceDocsContextFactory
import io.element.android.features.matcha.impl.docs.list.CATALOG_LOAD_ERRORS
import io.element.android.features.matcha.impl.docs.list.lastEdits
import io.element.android.features.matcha.impl.docs.setup.readSpaceDocsStatus
import io.element.android.features.matcha.impl.docs.transport.CatalogState
import io.element.android.features.matcha.impl.docs.transport.DocumentCatalog
import io.element.android.features.matcha.impl.docs.transport.DocumentSync
import io.element.android.features.matcha.impl.store.MatchaRoomStore
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

/** A joined space as the Notes tab lists it, with what is known about its notes. */
data class NotesSpace(
    val spaceId: RoomId,
    val name: String?,
    val avatarUrl: String?,
    val notes: SpaceNotes,
)

/** What the Notes tab knows about the notes of a space. */
sealed interface SpaceNotes {
    /** Not checked yet. */
    data object Unknown : SpaceNotes

    /** Notes are off and the user can't turn them on. */
    data object Off : SpaceNotes

    /** Notes are off and the user can turn them on. */
    data object CanTurnOn : SpaceNotes

    data class On(
        /** The last catalog read, kept once the space is closed; null until one has been read. */
        val catalog: CatalogState?,
        /** When each note was last edited, from the same read. */
        val lastEdits: Map<String, Long>,
        /** The catalog could not be opened or read; [NotesTabReader.retry] reads it again. */
        val isFailed: Boolean,
    ) : SpaceNotes
}

/**
 * The notes of every joined space, for the Notes tab. The spaces come from the space service, in the order of the Spaces list (the
 * room list never holds joined spaces), and whether each has notes is checked from its stored state, a few at a time.
 *
 * A space's catalog is expensive to read: it is a Yjs document replayed from the space's history, which takes a Matcha timeline,
 * a scan of that history, the events related to the space's create event and an engine document. So [run] reads catalogs only
 * for the spaces on screen ([setShown]), [MAX_CONCURRENT_READS] at a time, keeps each live while it stays on screen and closes it
 * [CLOSE_DELAY] after it scrolls away, and closes every one when the tab goes. Every catalog is one document in the session's
 * single engine WebView ([io.element.android.features.matcha.impl.docs.engine.DefaultYjsEngine]), which goes when the last
 * document closes. What was read is kept for the session, so the tab shows it at once when it comes back.
 *
 * It runs on the caller's dispatcher (the main thread, for a composition), which is the only one that changes its state.
 *
 * ponytail: a closed catalog is read in full again each time its space comes back on screen, setup is checked again for every space
 * each time the tab is shown, and the engine WebView is created again after the tab has been left. A persistent cache of names, or
 * reading the catalog's frames from the server without a timeline (the port plan's Path B), would lift these ceilings. Only top-level
 * spaces are listed, as the space service gives them: a joined subspace of a joined space is reached from its parent's screen;
 * walking each space's children would list it here too.
 */
@SingleIn(SessionScope::class)
@Inject
class NotesTabReader(
    private val client: MatrixClient,
    private val contextFactory: SpaceDocsContextFactory,
) {
    private data class JoinedSpace(val spaceId: RoomId, val name: String?, val avatarUrl: String?)

    /** The joined spaces in the Spaces list's order; null until the space service has listed them once. */
    private val joined = MutableStateFlow<List<JoinedSpace>?>(null)
    private val notes = MutableStateFlow<Map<RoomId, SpaceNotes>>(emptyMap())
    private val shown = MutableStateFlow<Set<RoomId>>(emptySet())
    private val runLock = Mutex()

    /** The joined spaces in the Spaces list's order, or null until the space service has listed them. */
    val spaces: Flow<List<NotesSpace>?> = combine(joined, notes) { joined, notes ->
        joined?.map { NotesSpace(it.spaceId, it.name, it.avatarUrl, notes[it.spaceId] ?: SpaceNotes.Unknown) }
    }

    /** The spaces on screen: only their catalogs are read. */
    fun setShown(spaceIds: Set<RoomId>) {
        shown.value = spaceIds
    }

    /** Reads again the catalog of a space whose read failed. */
    fun retry(spaceId: RoomId) = updateOn(spaceId) { it.copy(catalog = null, isFailed = false) }

    /** Follows the joined spaces and reads the catalogs on screen until cancelled, then closes every catalog it opened. */
    suspend fun run() = runLock.withLock {
        try {
            coroutineScope {
                val checks = Semaphore(MAX_CONCURRENT_READS)
                val reads = Semaphore(MAX_CONCURRENT_READS)
                val readJobs = HashMap<RoomId, Job>()
                val closeJobs = HashMap<RoomId, Job>()
                val checked = HashSet<RoomId>()
                launch {
                    joinedSpacesFlow().collect { spaces ->
                        val ids = spaces.mapTo(HashSet()) { it.spaceId }
                        joined.value = spaces
                        notes.update { all -> all.filterKeys(ids::contains) }
                        checked.retainAll(ids)
                        for (spaceId in ids - checked) {
                            checked += spaceId
                            launch { checks.withPermit { check(spaceId) } }
                        }
                    }
                }
                combine(shown, notes) { shown, notes -> shown.filterTo(HashSet()) { (notes[it] as? SpaceNotes.On)?.isFailed == false } }
                    .distinctUntilChanged()
                    .collect { wanted ->
                        for (spaceId in wanted) {
                            closeJobs.remove(spaceId)?.cancel()
                            if (readJobs[spaceId]?.isActive != true) readJobs[spaceId] = launch { read(spaceId, reads) }
                        }
                        // A space that scrolls away briefly keeps its catalog; one that stays away lets it go.
                        for (spaceId in readJobs.keys - wanted - closeJobs.keys) {
                            closeJobs[spaceId] = launch {
                                delay(CLOSE_DELAY)
                                readJobs.remove(spaceId)?.cancel()
                                closeJobs.remove(spaceId)
                            }
                        }
                    }
            }
        } finally {
            shown.value = emptySet()
        }
    }

    /** The user's joined top-level spaces; it replays the latest list. */
    private fun joinedSpacesFlow(): Flow<List<JoinedSpace>> = client.spaceService.topLevelSpacesFlow
        .map { spaces -> spaces.map { JoinedSpace(spaceId = it.roomId, name = it.displayName, avatarUrl = it.avatarUrl) } }
        .distinctUntilChanged()

    /** Whether the space has notes on, from its stored state. A space that can't be checked is left out until the next run. */
    private suspend fun check(spaceId: RoomId) {
        // Only the stored state is read, so a room without a timeline will do.
        val room = client.getRoom(spaceId)
        val status = try {
            room?.let { baseRoom ->
                runCatchingExceptions { readSpaceDocsStatus(baseRoom) }
                    .onFailure { Timber.w(it, "Could not check the notes of %s", spaceId) }
                    .getOrNull()
            }
        } finally {
            room?.destroy()
        }
        notes.update { all ->
            val next = when {
                status == null -> SpaceNotes.Off
                // What was read before stays until the catalog is read again; a failed read is tried again.
                status.isConfigured -> (all[spaceId] as? SpaceNotes.On)?.copy(isFailed = false)
                    ?: SpaceNotes.On(catalog = null, lastEdits = emptyMap(), isFailed = false)
                status.canSetup -> SpaceNotes.CanTurnOn
                else -> SpaceNotes.Off
            }
            all + (spaceId to next)
        }
    }

    /** Opens the space's catalog and follows it until cancelled or until it fails to load, then closes it. */
    private suspend fun read(spaceId: RoomId, permits: Semaphore) = coroutineScope {
        var context: SpaceDocsContext? = null
        try {
            val following = permits.withPermit {
                val opened = runCatchingExceptions { contextFactory.create(spaceId, isEarlierChatDocument = false, parentScope = this) }
                    .onFailure { Timber.w(it, "Could not open the notes of %s", spaceId) }
                    .getOrNull()
                context = opened
                val catalog = opened?.catalog ?: return@withPermit null
                launch { follow(spaceId, opened.store, catalog) }.also {
                    // The permit is kept while the catalog replays the space's history, so only a few spaces read at once.
                    withTimeoutOrNull(LOAD_TIMEOUT) { catalog.state.first { it.sync != DocumentSync.Loading } }
                }
            }
            if (following == null) updateOn(spaceId) { it.copy(isFailed = true) } else following.join()
        } finally {
            context?.close()
        }
    }

    /** Keeps what the catalog shows, until it fails to load. */
    private suspend fun follow(spaceId: RoomId, store: MatchaRoomStore, catalog: DocumentCatalog) {
        combine(catalog.state, store.snapshot.map { lastEdits(it.events) }.distinctUntilChanged(), ::Pair).first { (state, edits) ->
            val isFailed = state.error in CATALOG_LOAD_ERRORS
            updateOn(spaceId) { previous ->
                // A catalog read again starts empty: what was read before is shown until the new read is done.
                val isReloading = state.sync == DocumentSync.Loading && previous.catalog != null
                previous.copy(
                    catalog = if (isReloading) previous.catalog else state,
                    lastEdits = if (isReloading) previous.lastEdits else edits,
                    isFailed = isFailed,
                )
            }
            if (isFailed) Timber.w("Could not load the notes of %s", spaceId)
            isFailed
        }
    }

    private fun updateOn(spaceId: RoomId, transform: (SpaceNotes.On) -> SpaceNotes.On) = notes.update { all ->
        val current = all[spaceId] as? SpaceNotes.On ?: return@update all
        all + (spaceId to transform(current))
    }

    companion object {
        const val MAX_CONCURRENT_READS = 3
        val CLOSE_DELAY = 20.seconds
        val LOAD_TIMEOUT = 30.seconds
    }
}
