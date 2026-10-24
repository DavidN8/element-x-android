/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs.notestab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import dev.zacsweers.metro.Inject
import io.element.android.features.matcha.impl.docs.DocNameSheetState
import io.element.android.features.matcha.impl.docs.SpaceDocsContextFactory
import io.element.android.features.matcha.impl.docs.docNameError
import io.element.android.features.matcha.impl.docs.list.docRows
import io.element.android.features.matcha.impl.docs.saveFailed
import io.element.android.features.matcha.impl.docs.saveIn
import io.element.android.features.matcha.impl.docs.transport.DocumentCatalog
import io.element.android.features.matcha.impl.docs.transport.DocumentSync
import io.element.android.features.matcha.impl.docs.withNameError
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.dateformatter.api.DateFormatter
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

/**
 * The Notes tab: a section per joined space with its notes, read by [NotesTabReader], and New note. A new note is named in its
 * space's own catalog, which is opened with the name sheet and closed with it. A sheet dismissed while it saves is only hidden, so
 * the save finishes in that catalog, which closes after it.
 */
@Inject
class NotesTabPresenter(
    private val reader: NotesTabReader,
    private val contextFactory: SpaceDocsContextFactory,
    private val featureFlagService: FeatureFlagService,
    private val dateFormatter: DateFormatter,
) {
    private sealed interface SheetTarget {
        data object ChooseSpace : SheetTarget

        data class Name(val spaceId: RoomId) : SheetTarget
    }

    /** The name sheet of a new note and what it handles; it is hidden after being dismissed while it saves. */
    private data class NameSheet(val state: DocNameSheetState, val isHidden: Boolean, val eventSink: (NotesTabEvent) -> Unit)

    /** @param onOpenNote shows a note of a space, or the space's notes list when the document id is null. */
    @Composable
    fun present(onOpenNote: (spaceId: RoomId, documentId: String?) -> Unit): NotesTabState {
        val isEnabled by remember { featureFlagService.isFeatureEnabledFlow(FeatureFlags.MatchaWorkspaces) }.collectAsState(initial = false)
        if (isEnabled) {
            // Leaving the tab cancels this, which closes every catalog the tab opened.
            LaunchedEffect(Unit) { reader.run() }
        }
        val spaces by remember { reader.spaces }.collectAsState(initial = null)
        val currentOnOpenNote by rememberUpdatedState(onOpenNote)
        var sheetTarget by remember { mutableStateOf<SheetTarget?>(null) }

        val all = spaces.orEmpty()
        val sections = remember(all) { all.mapNotNull { it.toSection() } }
        val newNoteSpaces = remember(all) { all.filter { it.canTakeNote() } }
        val content = when {
            sections.isNotEmpty() -> NotesTabContent.Spaces(sections.toImmutableList())
            spaces == null || all.any { it.notes == SpaceNotes.Unknown } -> NotesTabContent.Loading
            else -> NotesTabContent.Empty(hasSpaces = all.isNotEmpty())
        }

        val nameSheet = (sheetTarget as? SheetTarget.Name)?.let { target ->
            key(target) {
                presentNameSheet(
                    spaceId = target.spaceId,
                    onClose = { sheetTarget = null },
                    // Like the docs list, a new note opens once saved, even when its sheet was dismissed while it saved.
                    onCreate = { documentId ->
                        sheetTarget = null
                        currentOnOpenNote(target.spaceId, documentId)
                    },
                )
            }
        }

        fun handleEvent(event: NotesTabEvent) {
            when (event) {
                is NotesTabEvent.OpenNote -> currentOnOpenNote(event.spaceId, event.documentId)
                is NotesTabEvent.Retry -> reader.retry(event.spaceId)
                is NotesTabEvent.SpacesShown -> reader.setShown(event.spaceIds)
                // A note still saving after its sheet was dismissed shows that sheet again, rather than a second note being started.
                NotesTabEvent.NewNote -> if (nameSheet != null) {
                    nameSheet.eventSink(event)
                } else {
                    sheetTarget = when {
                        newNoteSpaces.size == 1 -> SheetTarget.Name(newNoteSpaces.single().spaceId)
                        newNoteSpaces.isNotEmpty() -> SheetTarget.ChooseSpace
                        else -> null
                    }
                }
                is NotesTabEvent.ChooseSpace -> if (sheetTarget == SheetTarget.ChooseSpace && newNoteSpaces.any { it.spaceId == event.spaceId }) {
                    sheetTarget = SheetTarget.Name(event.spaceId)
                }
                is NotesTabEvent.UpdateName, NotesTabEvent.SubmitName -> nameSheet?.eventSink?.invoke(event)
                NotesTabEvent.DismissSheet -> if (nameSheet != null) nameSheet.eventSink(event) else sheetTarget = null
            }
        }

        return NotesTabState(
            isVisible = isEnabled,
            content = content,
            canCreate = newNoteSpaces.isNotEmpty(),
            sheet = when (sheetTarget) {
                null -> null
                SheetTarget.ChooseSpace -> NotesTabSheet.ChooseSpace(newNoteSpaces.map { it.toNotesTabSpace() }.toImmutableList())
                is SheetTarget.Name -> nameSheet?.takeUnless { it.isHidden }?.let { NotesTabSheet.Name(it.state) }
            },
            eventSink = ::handleEvent,
        )
    }

    /**
     * The name sheet of a new note in [spaceId]. Its catalog reads the space while the name is typed. Dismissed while it saves, it
     * is only hidden, so this composition, with the save and the catalog, stays until the save ends; like the docs list's sheet, a
     * failed save shows it again with the error, and Create then saves the same note.
     *
     * ponytail: leaving the tab while a note saves still cancels the save with the tab's composition, so the note may not be sent.
     * Saving in the session's scope, with the catalog closed after it, would let it finish.
     */
    @Composable
    private fun presentNameSheet(spaceId: RoomId, onClose: () -> Unit, onCreate: (documentId: String) -> Unit): NameSheet {
        val coroutineScope = rememberCoroutineScope()
        val currentOnClose by rememberUpdatedState(onClose)
        val currentOnCreate by rememberUpdatedState(onCreate)
        var sheet by remember { mutableStateOf(DocNameSheetState.new()) }
        var isHidden by remember { mutableStateOf(false) }
        val catalog by produceState<Result<DocumentCatalog>?>(null) {
            val context = runCatchingExceptions { contextFactory.create(spaceId, isEarlierChatDocument = false, parentScope = this) }.getOrNull()
            value = context?.catalog?.let { Result.success(it) } ?: Result.failure(IllegalStateException("The space can't be opened"))
            try {
                awaitCancellation()
            } finally {
                context?.close()
            }
        }

        fun submit() {
            val current = sheet.takeIf { !it.isSaving } ?: return
            current.withNameError()?.let {
                sheet = it
                return
            }
            // Saving from here on, so a dismissal from now only hides the sheet.
            sheet = current.copy(isSaving = true, error = null)
            coroutineScope.launch {
                snapshotFlow { catalog }.filterNotNull().first()
                    .fold(
                        onSuccess = { catalog ->
                            // The name can be typed before the catalog has read the space.
                            withTimeoutOrNull(NotesTabReader.LOAD_TIMEOUT) { catalog.state.first { it.sync != DocumentSync.Loading } }
                            current.saveIn(catalog)
                        },
                        onFailure = { Result.failure(it) },
                    )
                    .onSuccess { currentOnCreate(it) }
                    .onFailure {
                        Timber.w(it, "Could not create a note in %s", spaceId)
                        sheet = current.saveFailed(it)
                        isHidden = false
                    }
            }
        }

        return NameSheet(sheet, isHidden) { event ->
            when (event) {
                is NotesTabEvent.UpdateName -> sheet = sheet.copy(name = event.name, error = docNameError(event.name))
                NotesTabEvent.SubmitName -> submit()
                NotesTabEvent.DismissSheet -> if (sheet.isSaving) isHidden = true else currentOnClose()
                NotesTabEvent.NewNote -> isHidden = false
                else -> Unit
            }
        }
    }

    private fun NotesSpace.toSection(): NotesSection? {
        val content = when (val notes = notes) {
            SpaceNotes.Unknown, SpaceNotes.Off -> return null
            SpaceNotes.CanTurnOn -> NotesSectionContent.TurnOn
            is SpaceNotes.On -> {
                val catalog = notes.catalog
                when {
                    notes.isFailed -> NotesSectionContent.LoadError
                    // Partial content is shown as soon as it exists, so a skeleton only stands for an empty catalog.
                    catalog == null || catalog.sync == DocumentSync.Loading && catalog.documents.size <= 1 -> NotesSectionContent.Loading
                    else -> NotesSectionContent.Notes(docRows(catalog.documents, notes.lastEdits, dateFormatter).toImmutableList())
                }
            }
        }
        return NotesSection(space = toNotesTabSpace(), content = content)
    }
}

/** Notes are on and the catalog can be written; a catalog not read yet is assumed writable, and creating checks it. */
private fun NotesSpace.canTakeNote(): Boolean {
    val on = notes as? SpaceNotes.On ?: return false
    val catalog = on.catalog
    return !on.isFailed && (catalog == null || catalog.sync == DocumentSync.Loading || catalog.canEdit)
}

private fun NotesSpace.toNotesTabSpace() = NotesTabSpace(
    spaceId = spaceId,
    name = name,
    avatar = AvatarData(id = spaceId.value, name = name, url = avatarUrl, size = AvatarSize.SpaceListItem),
)
