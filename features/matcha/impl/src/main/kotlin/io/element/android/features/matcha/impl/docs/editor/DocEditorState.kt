/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs.editor

import io.element.android.features.matcha.impl.docs.DocNameSheetState
import kotlinx.collections.immutable.ImmutableList

data class DocEditorState(
    /** The doc's name; null for Space notes before anyone names it. */
    val title: String?,
    val status: DocStatus,
    val notice: DocNotice?,
    /** The doc could not be loaded at all: a full-page error with Retry. */
    val isLoadError: Boolean,
    /** The Markdown, including edits not saved yet. */
    val text: String,
    /** [text] read as Markdown for the preview; it may lag behind [text] while it is read. */
    val blocks: ImmutableList<MarkdownBlock>,
    /** Typed edits are still going into the document, so [text] may lag behind the field. */
    val isApplyingEdits: Boolean,
    val isEditing: Boolean,
    val canEdit: Boolean,
    val canRename: Boolean,
    /** Edits that have not reached the server: leaving loses them. */
    val hasUnsavedChanges: Boolean,
    val showLeaveDialog: Boolean,
    val nameSheet: DocNameSheetState?,
    val eventSink: (DocEditorEvent) -> Unit,
)

/** The top bar's subtitle. */
enum class DocStatus { Loading, Saving, Saved, Offline, Failed, ViewOnly, EarlierChat }

/** The strip under the top bar. */
enum class DocNotice { Offline, DecryptError, Incomplete, Invalid, PermissionError, TooLarge }
