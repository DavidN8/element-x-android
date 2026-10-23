/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs

import dev.zacsweers.metro.Inject
import io.element.android.features.matcha.impl.docs.engine.YjsEngine
import io.element.android.features.matcha.impl.docs.transport.DocumentCatalog
import io.element.android.features.matcha.impl.docs.transport.RoomDocumentSession
import io.element.android.features.matcha.impl.store.MatchaRoomStore
import io.element.android.features.networkmonitor.api.NetworkMonitor
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.JoinedRoom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job

/**
 * What the docs screens of one space share while they are open: the space, its Matcha store (whose whole history the documents
 * read) and the catalog of names. For a chat's earlier document there is no catalog and everything is read-only.
 */
class SpaceDocsContext(
    val room: JoinedRoom,
    val store: MatchaRoomStore,
    val catalog: DocumentCatalog?,
    private val catalogSession: RoomDocumentSession?,
    private val scope: CoroutineScope,
    private val openSession: (documentId: String) -> RoomDocumentSession,
) {
    /** The chat's own earlier document, read-only, instead of a space's documents. */
    val isEarlierChatDocument get() = catalog == null

    /** A new session for one document; close it when its screen goes away. */
    fun openDocument(documentId: String): RoomDocumentSession = openSession(documentId)

    fun close() {
        catalogSession?.close()
        scope.cancel()
        room.destroy()
    }
}

@Inject
class SpaceDocsContextFactory(
    private val client: MatrixClient,
    private val engine: YjsEngine,
    private val networkMonitor: NetworkMonitor,
    private val dispatchers: CoroutineDispatchers,
) {
    /** Null when the room is not joined. */
    suspend fun create(roomId: RoomId, isEarlierChatDocument: Boolean, parentScope: CoroutineScope): SpaceDocsContext? {
        val room = client.getJoinedRoom(roomId) ?: return null
        val scope = CoroutineScope(parentScope.coroutineContext + dispatchers.main + SupervisorJob(parentScope.coroutineContext.job))
        // Undecryptable frames must be noticed rather than skipped.
        val store = MatchaRoomStore(room, scope, extraEventTypes = setOf(DocsProtocol.EVENT_ROOM_ENCRYPTED))
        fun session(documentId: String) = RoomDocumentSession(
            documentId = documentId,
            room = room,
            store = store,
            engine = engine,
            networkMonitor = networkMonitor,
            parentScope = scope,
            isReadOnly = isEarlierChatDocument,
        )
        val catalogSession = if (isEarlierChatDocument) null else session(DocsProtocol.CATALOG_DOCUMENT_ID)
        return SpaceDocsContext(
            room = room,
            store = store,
            catalog = catalogSession?.let { DocumentCatalog(it, room) },
            catalogSession = catalogSession,
            scope = scope,
            openSession = ::session,
        )
    }
}
