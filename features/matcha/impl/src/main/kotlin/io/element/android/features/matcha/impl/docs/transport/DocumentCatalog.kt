/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs.transport

import io.element.android.features.matcha.impl.docs.DocsProtocol
import io.element.android.features.matcha.impl.protocol.longOrNull
import io.element.android.features.matcha.impl.protocol.stringOrNull
import io.element.android.libraries.matrix.api.room.JoinedRoom
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.UUID

/** A document of a space, as named in its catalog. */
data class SpaceDocument(
    val id: String,
    /** The original main document has no name until someone names it; it is shown as "Space notes". */
    val title: String?,
    val createdAt: Long,
)

data class CatalogState(
    val documents: List<SpaceDocument>,
    val isReady: Boolean,
    val canEdit: Boolean,
    val sync: DocumentSync,
    val pendingUpdates: Int,
    val error: DocumentError?,
) {
    companion object {
        val Loading = CatalogState(documents = emptyList(), isReady = false, canEdit = false, sync = DocumentSync.Loading, pendingUpdates = 0, error = null)
    }
}

/**
 * The encrypted list of a space's document names, kept in the reserved `catalog` document like the web client's
 * `SpaceDocumentCatalog`. Names are written only when the space is encrypted.
 */
class DocumentCatalog(
    private val session: RoomDocumentSession,
    private val room: JoinedRoom,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newDocumentId: () -> String = { UUID.randomUUID().toString() },
) {
    val state: Flow<CatalogState> = combine(
        session.state,
        room.roomInfoFlow.map { it.isEncrypted == true }.distinctUntilChanged(),
    ) { state, isEncrypted -> catalogState(state, isEncrypted) }

    private fun catalogState(state: DocumentSessionState, isEncrypted: Boolean): CatalogState {
        var isInvalid = false
        val documents = state.entries.mapNotNull { (id, value) ->
            parseEntry(id, value as? JsonObject).also { if (it == null) isInvalid = true }
        }.toMutableList()
        // Existing spaces already have this document; it never needs a migration.
        if (documents.none { it.id == DocsProtocol.MAIN_DOCUMENT_ID }) documents += SpaceDocument(DocsProtocol.MAIN_DOCUMENT_ID, title = null, createdAt = 0)
        documents.sortWith(compareBy<SpaceDocument> { it.createdAt }.thenBy { it.id })
        return CatalogState(
            documents = documents,
            isReady = state.isReady && !isInvalid,
            canEdit = state.canEdit && !isInvalid && isEncrypted,
            sync = if (isInvalid) DocumentSync.Error else state.sync,
            pendingUpdates = state.pendingUpdates,
            error = if (isInvalid) DocumentError.InvalidUpdate else state.error,
        )
    }

    private fun parseEntry(id: String, metadata: JsonObject?): SpaceDocument? {
        if (!DocsProtocol.isDocumentId(id)) return null
        val title = metadata?.stringOrNull("title")?.takeIf(DocsProtocol::isValidName) ?: return null
        val createdAt = metadata.longOrNull("createdAt")?.takeIf { it in 0..MAX_SAFE_INTEGER } ?: return null
        return SpaceDocument(id = id, title = title, createdAt = createdAt)
    }

    private fun current() = catalogState(session.state.value, room.info().isEncrypted == true)

    /**
     * Publishes a new named document; returns its id once its catalog entry is acknowledged. When only the save failed, the
     * failure's [CatalogException.documentId] names the entry kept locally, so a retry renames it instead of adding another.
     */
    suspend fun create(title: String): Result<String> {
        checkWrite(title)?.let { return Result.failure(it) }
        val id = newDocumentId()
        val error = write(id, title.trim(), clock()).exceptionOrNull() ?: return Result.success(id)
        val isKept = error is CatalogException && session.state.value.pendingUpdates > 0
        return Result.failure(if (isKept) CatalogException(error.error, id) else error)
    }

    /** Renames a document's catalog entry, without touching its text. */
    suspend fun rename(id: String, title: String): Result<Unit> {
        checkWrite(title)?.let { return Result.failure(it) }
        val entry = current().documents.firstOrNull { it.id == id } ?: return Result.failure(CatalogException(DocumentError.InvalidUpdate))
        if (entry.title == title.trim()) return save()
        return write(id, title.trim(), entry.createdAt)
    }

    /** Recovers missing history or keys, and retries the unsent names. */
    suspend fun retry() {
        session.retry().join()
        session.flush()
    }

    private suspend fun write(id: String, title: String, createdAt: Long): Result<Unit> {
        val value = buildJsonObject {
            put("title", title)
            put("createdAt", createdAt)
        }
        if (!session.setEntry(id, value)) return Result.failure(CatalogException(session.state.value.error ?: DocumentError.PermissionError))
        return save()
    }

    private suspend fun save(): Result<Unit> {
        session.flush()
        val state = session.state.value
        return if (state.error != null ||
            state.pendingUpdates > 0) {
                Result.failure(CatalogException(state.error ?: DocumentError.SendError))
            } else {
                Result.success(Unit)
            }
    }

    private fun checkWrite(title: String): CatalogException? {
        if (!DocsProtocol.isValidName(title)) return CatalogException(DocumentError.InvalidUpdate)
        val state = current()
        return if (state.canEdit) null else CatalogException(state.error ?: DocumentError.PermissionError)
    }

    private companion object {
        /** JavaScript's Number.MAX_SAFE_INTEGER, as the web client requires. */
        const val MAX_SAFE_INTEGER = 9_007_199_254_740_991L
    }
}

class CatalogException(
    val error: DocumentError,
    /** The new document whose entry is kept, unsaved, after a failed create. */
    val documentId: String? = null,
) : Exception(error.name)
