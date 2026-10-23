/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs.list

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.appconfig.MatchaConfig
import io.element.android.features.matcha.impl.docs.DocNameError
import io.element.android.features.matcha.impl.docs.DocNameSheetState
import io.element.android.features.matcha.impl.docs.DocsProtocol
import io.element.android.features.matcha.impl.docs.SpaceDocsContext
import io.element.android.features.matcha.impl.docs.docNameError
import io.element.android.features.matcha.impl.docs.setup.SpaceDocsSetup
import io.element.android.features.matcha.impl.docs.setup.SpaceDocsSetupStatus
import io.element.android.features.matcha.impl.docs.transport.CatalogException
import io.element.android.features.matcha.impl.docs.transport.CatalogState
import io.element.android.features.matcha.impl.docs.transport.DocumentCatalog
import io.element.android.features.matcha.impl.docs.transport.DocumentError
import io.element.android.features.matcha.impl.docs.transport.DocumentSync
import io.element.android.features.matcha.impl.protocol.RawEvent
import io.element.android.features.matcha.impl.protocol.stringOrNull
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.dateformatter.api.DateFormatter
import io.element.android.libraries.dateformatter.api.DateFormatterMode
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber

/** The docs of a space: Space notes first, then the others by last edit, with New doc and Rename. */
@AssistedInject
class SpaceDocsListPresenter(
    @Assisted private val context: SpaceDocsContext,
    @Assisted private val navigator: Navigator,
    private val dateFormatter: DateFormatter,
) : Presenter<SpaceDocsListState> {
    interface Navigator {
        fun openDocument(documentId: String)
        fun openSetup()
    }

    @AssistedFactory
    fun interface Factory {
        fun create(context: SpaceDocsContext, navigator: Navigator): SpaceDocsListPresenter
    }

    private val catalog: DocumentCatalog = requireNotNull(context.catalog) { "A chat's earlier document has no list" }
    private val setup = SpaceDocsSetup(context.room)

    @Composable
    override fun present(): SpaceDocsListState {
        val coroutineScope = rememberCoroutineScope()
        val catalogState by remember { catalog.state }.collectAsState(CatalogState.Loading)
        val roomInfo by context.room.roomInfoFlow.collectAsState()
        val lastEdits by remember { context.store.snapshot.map { lastEdits(it.events) }.distinctUntilChanged() }.collectAsState(emptyMap())
        var setupStatus by remember { mutableStateOf<SpaceDocsSetupStatus?>(null) }
        var nameSheet by remember { mutableStateOf<DocNameSheetState?>(null) }

        LaunchedEffect(Unit) {
            // Turning docs on changes encryption and power levels, which arrive with the room info.
            context.room.roomInfoFlow
                .map { Triple(it.isEncrypted, it.roomPowerLevels, it.currentUserMembership) }
                .distinctUntilChanged()
                .collect { setupStatus = setup.status() }
        }

        fun submitName() = coroutineScope.launch {
            val sheet = nameSheet?.takeIf { !it.isSaving } ?: return@launch
            val error = if (sheet.name.isBlank()) DocNameError.Empty else docNameError(sheet.name)
            if (error != null) {
                nameSheet = sheet.copy(error = error)
                return@launch
            }
            nameSheet = sheet.copy(isSaving = true, error = null)
            val existingId = sheet.documentId ?: sheet.unsavedDocumentId
            val result = if (existingId != null) catalog.rename(existingId, sheet.name).map { existingId } else catalog.create(sheet.name)
            result
                .onSuccess { documentId ->
                    nameSheet = null
                    if (sheet.documentId == null) navigator.openDocument(documentId)
                }
                .onFailure {
                    Timber.w(it, "Could not save a doc name")
                    nameSheet =
                        sheet.copy(isSaving = false, error = DocNameError.SaveFailed, unsavedDocumentId = (it as? CatalogException)?.documentId ?: existingId)
                }
        }

        fun handleEvent(event: SpaceDocsListEvent) {
            when (event) {
                is SpaceDocsListEvent.OpenDocument -> navigator.openDocument(event.documentId)
                SpaceDocsListEvent.OpenSetup -> navigator.openSetup()
                SpaceDocsListEvent.NewDocument -> nameSheet = DocNameSheetState.new()
                is SpaceDocsListEvent.Rename -> {
                    val title = catalogState.documents.firstOrNull { it.id == event.documentId }?.title.orEmpty()
                    nameSheet = DocNameSheetState.rename(event.documentId, title)
                }
                is SpaceDocsListEvent.UpdateName -> nameSheet = nameSheet?.copy(name = event.name, error = docNameError(event.name))
                SpaceDocsListEvent.SubmitName -> submitName()
                SpaceDocsListEvent.DismissNameSheet -> nameSheet = null
                SpaceDocsListEvent.Retry -> coroutineScope.launch { catalog.retry() }
            }
        }

        val status = setupStatus
        val content = when {
            status == null -> SpaceDocsListContent.Loading
            !status.isConfigured -> SpaceDocsListContent.NotSetUp(canSetUp = status.canSetup)
            catalogState.error in LOAD_ERRORS -> SpaceDocsListContent.LoadError
            // Partial content is shown as soon as it exists, so a skeleton only stands for an empty catalog.
            catalogState.sync == DocumentSync.Loading && catalogState.documents.size <= 1 -> SpaceDocsListContent.Loading
            else -> SpaceDocsListContent.Documents(rows(catalogState, lastEdits).toImmutableList())
        }
        return SpaceDocsListState(
            spaceName = roomInfo.name,
            content = content,
            canCreate = content is SpaceDocsListContent.Documents && catalogState.canEdit,
            hasUnreadableNames = content is SpaceDocsListContent.Documents && catalogState.error in UNREADABLE_ERRORS,
            nameSheet = nameSheet,
            eventSink = ::handleEvent,
        )
    }

    private fun rows(state: CatalogState, lastEdits: Map<String, Long>): List<DocRow> {
        fun editedAt(id: String, createdAt: Long) = lastEdits[id] ?: createdAt.takeIf { it > 0 }
        val (main, others) = state.documents.partition { it.id == DocsProtocol.MAIN_DOCUMENT_ID }
        return (main + others.sortedByDescending { editedAt(it.id, it.createdAt) ?: 0 }).map { document ->
            DocRow(
                id = document.id,
                title = document.title,
                editedAt = editedAt(document.id, document.createdAt)?.let { dateFormatter.format(it, DateFormatterMode.TimeOrDate, useRelative = true) },
            )
        }
    }

    /** The newest frame time of each document; frames are read newest first. */
    private fun lastEdits(events: List<RawEvent>): Map<String, Long> = buildMap {
        events.filter { it.type == MatchaConfig.EVENT_DOCS_UPDATE }.forEach { event ->
            val documentId = event.content.stringOrNull("doc")
            val timestamp = event.timestamp
            if (documentId != null && timestamp != null && documentId !in this) put(documentId, timestamp)
        }
    }

    private companion object {
        val LOAD_ERRORS = setOf(DocumentError.LoadError, DocumentError.AnchorError, DocumentError.InvalidUpdate)
        val UNREADABLE_ERRORS = setOf(DocumentError.DecryptError, DocumentError.IncompleteUpdate)
    }
}
