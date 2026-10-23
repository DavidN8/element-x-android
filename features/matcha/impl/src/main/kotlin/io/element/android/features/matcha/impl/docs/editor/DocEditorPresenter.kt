/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.features.matcha.impl.docs.DocNameError
import io.element.android.features.matcha.impl.docs.DocNameSheetState
import io.element.android.features.matcha.impl.docs.DocsProtocol
import io.element.android.features.matcha.impl.docs.SpaceDocsContext
import io.element.android.features.matcha.impl.docs.docNameError
import io.element.android.features.matcha.impl.docs.transport.CatalogState
import io.element.android.features.matcha.impl.docs.transport.DocumentError
import io.element.android.features.matcha.impl.docs.transport.DocumentSessionState
import io.element.android.features.matcha.impl.docs.transport.DocumentSync
import io.element.android.features.networkmonitor.api.NetworkMonitor
import io.element.android.features.networkmonitor.api.NetworkStatus
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * One doc: a preview of its Markdown, edit mode with a text field whose changes go into the shared document, and honest save
 * states. The document session lives as long as the screen; edits not acknowledged when leaving are lost, after a warning.
 */
@AssistedInject
class DocEditorPresenter(
    @Assisted private val context: SpaceDocsContext,
    @Assisted private val documentId: String,
    @Assisted private val onDone: () -> Unit,
    private val networkMonitor: NetworkMonitor,
    private val dispatchers: CoroutineDispatchers,
) : Presenter<DocEditorState> {
    @AssistedFactory
    fun interface Factory {
        fun create(context: SpaceDocsContext, documentId: String, onDone: () -> Unit): DocEditorPresenter
    }

    @Composable
    override fun present(): DocEditorState {
        val coroutineScope = rememberCoroutineScope()
        val session = remember { context.openDocument(documentId) }
        DisposableEffect(session) { onDispose { session.close() } }
        val documentState by session.state.collectAsState()
        val catalogState by remember { context.catalog?.state ?: flowOf(null) }.collectAsState(null)
        val connectivity by networkMonitor.connectivity.collectAsState()
        val roomName by remember { context.room.roomInfoFlow.map { it.name } }.collectAsState(context.room.info().name)
        var isEditing by remember { mutableStateOf(false) }
        var showLeaveDialog by remember { mutableStateOf(false) }
        var nameSheet by remember { mutableStateOf<DocNameSheetState?>(null) }
        var applyingEdits by remember { mutableIntStateOf(0) }
        var isLeaving by remember { mutableStateOf(false) }
        val text = documentState.text
        // Off the main thread: any member writes the text, and a long doc takes a while to read.
        val blocks by produceState<ImmutableList<MarkdownBlock>>(persistentListOf(), text) {
            value = withContext(dispatchers.computation) { MarkdownParser.parse(text).toImmutableList() }
        }

        val isEarlier = context.isEarlierChatDocument
        val catalog = catalogState
        val document = catalog?.documents?.firstOrNull { it.id == documentId }
        val canWriteCatalog = !isEarlier && catalog?.canEdit == true && document != null
        val canEdit = documentState.canEdit && canWriteCatalog
        val hasUnsaved = documentState.pendingUpdates > 0
        LaunchedEffect(canEdit) {
            // Losing the right to edit, or an error, ends edit mode; the strip says why.
            if (!canEdit) isEditing = false
        }

        fun submitName() = coroutineScope.launch {
            val sheet = nameSheet?.takeIf { !it.isSaving } ?: return@launch
            val error = if (sheet.name.isBlank()) DocNameError.Empty else docNameError(sheet.name)
            if (error != null) {
                nameSheet = sheet.copy(error = error)
                return@launch
            }
            nameSheet = sheet.copy(isSaving = true, error = null)
            val result = context.catalog?.rename(documentId, sheet.name) ?: return@launch
            nameSheet = result.fold(
                onSuccess = { null },
                onFailure = {
                    Timber.w(it, "Could not rename a doc")
                    sheet.copy(isSaving = false, error = DocNameError.SaveFailed)
                },
            )
        }

        fun handleEvent(event: DocEditorEvent) {
            when (event) {
                is DocEditorEvent.Edit -> if (canEdit) {
                    applyingEdits++
                    coroutineScope.launch {
                        try {
                            session.edit(event.before, event.after)
                        } finally {
                            applyingEdits--
                        }
                    }
                }
                DocEditorEvent.StartEditing -> if (canEdit) isEditing = true
                DocEditorEvent.StopEditing -> {
                    isEditing = false
                    coroutineScope.launch { session.flush() }
                }
                DocEditorEvent.Retry -> coroutineScope.launch {
                    // A failed save only needs sending again; anything else reads the doc again.
                    if (session.state.value.error != DocumentError.SendError) {
                        context.catalog?.retry()
                        session.retry().join()
                    }
                    session.flush()
                }
                DocEditorEvent.Back -> when {
                    isEditing -> isEditing = false
                    !hasUnsaved -> onDone()
                    isLeaving -> Unit
                    // Edits still waiting for the debounce go now; only what the server refused is at risk.
                    else -> coroutineScope.launch {
                        isLeaving = true
                        session.flush()
                        isLeaving = false
                        if (session.state.value.pendingUpdates > 0) showLeaveDialog = true else onDone()
                    }
                }
                DocEditorEvent.ConfirmLeave -> {
                    showLeaveDialog = false
                    onDone()
                }
                DocEditorEvent.CancelLeave -> showLeaveDialog = false
                DocEditorEvent.Rename -> if (canWriteCatalog) nameSheet = DocNameSheetState.rename(documentId, document.title.orEmpty())
                is DocEditorEvent.UpdateName -> nameSheet = nameSheet?.copy(name = event.name, error = docNameError(event.name))
                DocEditorEvent.SubmitName -> submitName()
                DocEditorEvent.DismissNameSheet -> nameSheet = null
            }
        }

        val isOffline = connectivity == NetworkStatus.Disconnected
        return DocEditorState(
            title = when {
                isEarlier -> roomName.orEmpty()
                document != null -> document.title
                documentId == DocsProtocol.MAIN_DOCUMENT_ID -> null
                else -> ""
            },
            status = status(documentState, catalog, isEarlier = isEarlier, canEdit = canEdit, isOffline = isOffline),
            notice = notice(documentState.error, isOffline = isOffline && !isEarlier && (canEdit || hasUnsaved)),
            isLoadError = documentState.error in LOAD_ERRORS || catalog?.isReady == true && document == null,
            text = text,
            blocks = blocks,
            isApplyingEdits = applyingEdits > 0,
            isEditing = isEditing && canEdit,
            canEdit = canEdit,
            canRename = canWriteCatalog,
            hasUnsavedChanges = hasUnsaved,
            showLeaveDialog = showLeaveDialog,
            nameSheet = nameSheet,
            eventSink = ::handleEvent,
        )
    }

    private fun status(state: DocumentSessionState, catalog: CatalogState?, isEarlier: Boolean, canEdit: Boolean, isOffline: Boolean): DocStatus {
        val hasUnsaved = state.pendingUpdates > 0
        return when {
            state.sync == DocumentSync.Loading || catalog?.sync == DocumentSync.Loading -> DocStatus.Loading
            isEarlier -> DocStatus.EarlierChat
            state.error == DocumentError.SendError -> if (isOffline) DocStatus.Offline else DocStatus.Failed
            hasUnsaved && state.error != null -> DocStatus.Failed
            !canEdit -> DocStatus.ViewOnly
            hasUnsaved -> DocStatus.Saving
            else -> DocStatus.Saved
        }
    }

    private fun notice(error: DocumentError?, isOffline: Boolean) = when (error) {
        DocumentError.DecryptError -> DocNotice.DecryptError
        DocumentError.IncompleteUpdate -> DocNotice.Incomplete
        DocumentError.InvalidUpdate -> DocNotice.Invalid
        DocumentError.PermissionError -> DocNotice.PermissionError
        DocumentError.TooLargeError -> DocNotice.TooLarge
        else -> if (isOffline) DocNotice.Offline else null
    }

    private companion object {
        val LOAD_ERRORS = setOf(DocumentError.LoadError, DocumentError.AnchorError)
    }
}
