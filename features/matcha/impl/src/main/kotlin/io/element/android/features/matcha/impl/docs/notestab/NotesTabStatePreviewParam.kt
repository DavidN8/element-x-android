/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs.notestab

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.features.matcha.impl.docs.DocNameError
import io.element.android.features.matcha.impl.docs.DocNameSheetState
import io.element.android.features.matcha.impl.docs.list.DocRow
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.collections.immutable.persistentListOf

open class NotesTabStatePreviewParam : PreviewParameterProvider<NotesTabState> {
    override val values: Sequence<NotesTabState>
        get() = sequenceOf(
            aNotesTabState(),
            aNotesTabState(content = NotesTabContent.Loading, canCreate = false),
            aNotesTabState(content = NotesTabContent.Empty(hasSpaces = false), canCreate = false),
            aNotesTabState(content = NotesTabContent.Empty(hasSpaces = true), canCreate = false),
            aNotesTabState(
                content = NotesTabContent.Spaces(
                    persistentListOf(
                        NotesSection(aLaunchSpace, NotesSectionContent.Notes(persistentListOf(DocRow(id = "main", title = null, editedAt = null)))),
                        NotesSection(aDesignSpace, NotesSectionContent.LoadError),
                        NotesSection(aCompanySpace, NotesSectionContent.TurnOn),
                    )
                ),
                canCreate = false,
            ),
            aNotesTabState(sheet = NotesTabSheet.ChooseSpace(persistentListOf(aDesignSpace, aLaunchSpace))),
            aNotesTabState(sheet = NotesTabSheet.Name(DocNameSheetState.new())),
            aNotesTabState(sheet = NotesTabSheet.Name(DocNameSheetState.new().copy(name = "Trip plans", error = DocNameError.SaveFailed))),
        )
}

internal fun aNotesTabState(
    content: NotesTabContent = NotesTabContent.Spaces(
        persistentListOf(
            NotesSection(
                aDesignSpace,
                NotesSectionContent.Notes(
                    persistentListOf(
                        DocRow(id = "main", title = null, editedAt = "Yesterday"),
                        DocRow(id = "11111111-1111-4111-8111-111111111111", title = "Trip plans", editedAt = "14:05"),
                        DocRow(
                            id = "22222222-2222-4222-8222-222222222222",
                            title = "A guide with a name long enough to be cut at the end of the row",
                            editedAt = "Mon"
                        ),
                    )
                ),
            ),
            NotesSection(aLaunchSpace, NotesSectionContent.Loading),
            NotesSection(aCompanySpace, NotesSectionContent.TurnOn),
        )
    ),
    canCreate: Boolean = true,
    sheet: NotesTabSheet? = null,
) = NotesTabState(
    isVisible = true,
    content = content,
    canCreate = canCreate,
    sheet = sheet,
    eventSink = {},
)

private val aDesignSpace = aNotesTabSpace("!design:matrix.org", "Design team")
private val aLaunchSpace = aNotesTabSpace("!launch:matrix.org", "Launch")
private val aCompanySpace = aNotesTabSpace("!company:matrix.org", null)

private fun aNotesTabSpace(roomId: String, name: String?) = NotesTabSpace(
    spaceId = RoomId(roomId),
    name = name,
    avatar = AvatarData(id = roomId, name = name, size = AvatarSize.SpaceListItem),
)
