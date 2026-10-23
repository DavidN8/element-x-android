/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.tasks

import android.os.Parcelable
import androidx.compose.runtime.Immutable
import io.element.android.features.matcha.impl.protocol.TaskStatus
import kotlinx.collections.immutable.ImmutableList
import kotlinx.parcelize.Parcelize

/** What the task editor opens on. */
@Immutable
sealed interface TaskEditorTarget : Parcelable {
    /** A new task, which also creates its thread. */
    @Parcelize
    data class New(val status: TaskStatus) : TaskEditorTarget

    @Parcelize
    data class Edit(val taskId: String) : TaskEditorTarget
}

data class TaskEditorState(
    val isNew: Boolean,
    /** The task has a thread, whose name follows the task's. */
    val isLinked: Boolean,
    val title: String,
    val description: String,
    val status: TaskStatus,
    val dueAt: Long?,
    /** The formatted deadline, or null without one. */
    val deadline: String?,
    /** False when the task's thread can't be renamed by this user, so the name is read-only. */
    val canRename: Boolean,
    /** Remind me is offered (edit mode, when reminders are available here). */
    val canRemind: Boolean,
    /** When the requested reminder is due, formatted, or null when none was requested. */
    val reminderRequestedAt: String?,
    /** Names of older cards linked to the same thread. */
    val earlierCards: ImmutableList<String>,
    val titleMaxLength: Int,
    val titleError: TaskFieldError?,
    val descriptionError: TaskFieldError?,
    val deadlineError: TaskDeadlineError?,
    /** Why the last save failed, shown above the button; the sheet stays open. */
    val saveError: TaskWriteError?,
    val isSaving: Boolean,
    /** Closing would drop changes, so it asks first. */
    val hasChanges: Boolean,
    val showDiscardDialog: Boolean,
    val eventSink: (TaskEditorEvent) -> Unit,
)

@Immutable
sealed interface TaskFieldError {
    data object Empty : TaskFieldError

    data class TooLong(val maxLength: Int) : TaskFieldError
}

enum class TaskDeadlineError {
    /** A warning only: saving is still allowed. */
    Past,

    /** The local time doesn't exist, because the clocks change then. */
    Skipped,
    OutOfRange,
}
