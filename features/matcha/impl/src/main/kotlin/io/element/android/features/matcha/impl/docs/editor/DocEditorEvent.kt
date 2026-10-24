/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs.editor

sealed interface DocEditorEvent {
    /** The text field changed from [before] to [after]. */
    data class Edit(val before: String, val after: String) : DocEditorEvent
    data object StartEditing : DocEditorEvent
    data object StopEditing : DocEditorEvent
    data object Retry : DocEditorEvent
    data object Back : DocEditorEvent
    data object ConfirmLeave : DocEditorEvent
    data object CancelLeave : DocEditorEvent
    data object Rename : DocEditorEvent
    data class UpdateName(val name: String) : DocEditorEvent
    data object SubmitName : DocEditorEvent
    data object DismissNameSheet : DocEditorEvent
}
