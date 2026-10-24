/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs.notestab

import io.element.android.libraries.matrix.api.core.RoomId

sealed interface NotesTabEvent {
    /** Opens a note, or the space's notes list when [documentId] is null. */
    data class OpenNote(val spaceId: RoomId, val documentId: String?) : NotesTabEvent

    data class Retry(val spaceId: RoomId) : NotesTabEvent

    /** The spaces with a header or a row on screen: only their notes are read. */
    data class SpacesShown(val spaceIds: Set<RoomId>) : NotesTabEvent

    /** The New note button: the name sheet, after Choose a space when several spaces can take the note. */
    data object NewNote : NotesTabEvent
    data class ChooseSpace(val spaceId: RoomId) : NotesTabEvent
    data class UpdateName(val name: String) : NotesTabEvent
    data object SubmitName : NotesTabEvent
    data object DismissSheet : NotesTabEvent
}
