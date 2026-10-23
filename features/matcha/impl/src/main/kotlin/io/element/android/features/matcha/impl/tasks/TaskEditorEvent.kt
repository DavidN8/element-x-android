/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.tasks

import io.element.android.features.matcha.impl.protocol.TaskStatus
import java.time.LocalDateTime

sealed interface TaskEditorEvent {
    data class SetTitle(val title: String) : TaskEditorEvent
    data class SetDescription(val description: String) : TaskEditorEvent
    data class SetStatus(val status: TaskStatus) : TaskEditorEvent

    /** A deadline picked as a local date and time, in the device's time zone. */
    data class SetDeadline(val dateTime: LocalDateTime) : TaskEditorEvent
    data object ClearDeadline : TaskEditorEvent
    data object Save : TaskEditorEvent
    data object RemindMe : TaskEditorEvent
    data object OpenThread : TaskEditorEvent

    /** ✕ or back: asks before dropping changes. */
    data object Close : TaskEditorEvent
    data object Discard : TaskEditorEvent
    data object KeepEditing : TaskEditorEvent
}
