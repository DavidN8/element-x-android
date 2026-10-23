/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs.editor

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.features.matcha.impl.docs.DocNameSheetState
import kotlinx.collections.immutable.toImmutableList

class DocEditorStatePreviewParam : PreviewParameterProvider<DocEditorState> {
    override val values: Sequence<DocEditorState>
        get() = sequenceOf(
            aDocEditorState(),
            aDocEditorState(status = DocStatus.Loading, text = "", canEdit = false),
            aDocEditorState(status = DocStatus.Saving, isEditing = true, hasUnsavedChanges = true),
            aDocEditorState(status = DocStatus.Offline, notice = DocNotice.Offline, hasUnsavedChanges = true),
            aDocEditorState(status = DocStatus.Failed, hasUnsavedChanges = true),
            aDocEditorState(status = DocStatus.ViewOnly, canEdit = false, canRename = false),
            aDocEditorState(status = DocStatus.EarlierChat, title = "Weekend trip", canEdit = false, canRename = false),
            aDocEditorState(status = DocStatus.ViewOnly, notice = DocNotice.DecryptError, canEdit = false),
            aDocEditorState(status = DocStatus.ViewOnly, notice = DocNotice.Incomplete, canEdit = false),
            aDocEditorState(status = DocStatus.ViewOnly, notice = DocNotice.Invalid, canEdit = false),
            aDocEditorState(status = DocStatus.ViewOnly, isLoadError = true, text = "", canEdit = false),
            aDocEditorState(title = null, text = ""),
            aDocEditorState(title = null, text = "", status = DocStatus.ViewOnly, canEdit = false),
            aDocEditorState(hasUnsavedChanges = true, status = DocStatus.Failed, showLeaveDialog = true),
            aDocEditorState(status = DocStatus.Failed, notice = DocNotice.PermissionError, canEdit = false, hasUnsavedChanges = true),
            aDocEditorState(status = DocStatus.Failed, notice = DocNotice.TooLarge, canEdit = false, hasUnsavedChanges = true),
        )
}

internal fun aDocEditorState(
    title: String? = "Trip plans",
    status: DocStatus = DocStatus.Saved,
    notice: DocNotice? = null,
    isLoadError: Boolean = false,
    text: String = "# Trip plans\nWe leave on **Friday**.\n\n- [ ] Tickets\n- [x] Charger",
    isEditing: Boolean = false,
    canEdit: Boolean = true,
    canRename: Boolean = true,
    hasUnsavedChanges: Boolean = false,
    showLeaveDialog: Boolean = false,
    nameSheet: DocNameSheetState? = null,
) = DocEditorState(
    title = title,
    status = status,
    notice = notice,
    isLoadError = isLoadError,
    text = text,
    blocks = MarkdownParser.parse(text).toImmutableList(),
    isApplyingEdits = false,
    isEditing = isEditing,
    canEdit = canEdit,
    canRename = canRename,
    hasUnsavedChanges = hasUnsavedChanges,
    showLeaveDialog = showLeaveDialog,
    nameSheet = nameSheet,
    eventSink = {},
)
