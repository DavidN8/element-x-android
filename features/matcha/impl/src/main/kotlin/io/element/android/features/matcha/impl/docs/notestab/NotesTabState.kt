/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs.notestab

import androidx.compose.runtime.Immutable
import io.element.android.features.matcha.impl.docs.DocNameSheetState
import io.element.android.features.matcha.impl.docs.list.DocRow
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.collections.immutable.ImmutableList

data class NotesTabState(
    /** False when the Matcha workspaces feature is off: nothing is drawn. */
    val isVisible: Boolean,
    val content: NotesTabContent,
    /** Shows the New note button: at least one space can take a new note. */
    val canCreate: Boolean,
    val sheet: NotesTabSheet?,
    val eventSink: (NotesTabEvent) -> Unit,
)

/** A space, as its section header and Choose a space show it. */
@Immutable
data class NotesTabSpace(
    val spaceId: RoomId,
    val name: String?,
    /** For a subspace, the name of the space it is listed under, shown below its own. */
    val parentName: String?,
    val avatar: AvatarData,
)

@Immutable
sealed interface NotesTabContent {
    /** The spaces are still being checked and none is known to have notes yet: skeleton rows. */
    data object Loading : NotesTabContent

    /** No space to list: the user has none, or none has notes and the user can't turn them on in any. */
    data class Empty(val hasSpaces: Boolean) : NotesTabContent

    /** A section per space, in the Spaces list's order, each top-level space followed by its subspaces. */
    data class Spaces(val sections: ImmutableList<NotesSection>) : NotesTabContent
}

@Immutable
data class NotesSection(
    val space: NotesTabSpace,
    val content: NotesSectionContent,
)

@Immutable
sealed interface NotesSectionContent {
    /** The space's notes are being read, or wait to come on screen. */
    data object Loading : NotesSectionContent

    /** The space's notes couldn't be read: a row with Retry. */
    data object LoadError : NotesSectionContent

    /** Notes are off and the user can turn them on: one quiet row. */
    data object TurnOn : NotesSectionContent

    /** Space notes first, then the others, most recently edited first. */
    data class Notes(val notes: ImmutableList<DocRow>) : NotesSectionContent
}

@Immutable
sealed interface NotesTabSheet {
    /** New note, when several spaces can take one. */
    data class ChooseSpace(val spaces: ImmutableList<NotesTabSpace>) : NotesTabSheet

    /** The new note's name. */
    data class Name(val state: DocNameSheetState) : NotesTabSheet
}
