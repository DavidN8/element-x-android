/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.tasks

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.appconfig.MatchaConfig
import io.element.android.features.matcha.impl.protocol.TaskStatus
import kotlinx.collections.immutable.toImmutableList

open class TaskEditorStatePreviewParam : PreviewParameterProvider<TaskEditorState> {
    override val values: Sequence<TaskEditorState>
        get() = sequenceOf(
            aTaskEditorState(),
            aTaskEditorState(isNew = false, title = "Book the venue", description = "Somewhere near the station", deadline = "Tomorrow 09:00"),
            aTaskEditorState(isNew = false, title = "Book the venue", canRemind = true, reminderRequestedAt = "Tomorrow 09:00", earlierCards = listOf("Venue")),
            aTaskEditorState(isNew = false, title = "Book the venue", canRename = false),
            aTaskEditorState(titleError = TaskFieldError.Empty),
            aTaskEditorState(title = "A".repeat(104), titleError = TaskFieldError.TooLong(MatchaConfig.THREAD_NAME_MAX_LENGTH)),
            aTaskEditorState(title = "Book the venue", deadline = "Yesterday", deadlineError = TaskDeadlineError.Past),
            aTaskEditorState(title = "Book the venue", deadlineError = TaskDeadlineError.Skipped),
            aTaskEditorState(title = "Book the venue", isSaving = true),
            aTaskEditorState(title = "Book the venue", saveError = TaskWriteError.Failed),
            aTaskEditorState(isNew = false, title = "Book the venue", saveError = TaskWriteError.BoardChanged),
            aTaskEditorState(title = "Book the venue", showDiscardDialog = true),
        )
}

internal fun aTaskEditorState(
    isNew: Boolean = true,
    title: String = "",
    description: String = "",
    status: TaskStatus = TaskStatus.Todo,
    deadline: String? = null,
    canRename: Boolean = true,
    canRemind: Boolean = false,
    reminderRequestedAt: String? = null,
    earlierCards: List<String> = emptyList(),
    titleError: TaskFieldError? = null,
    deadlineError: TaskDeadlineError? = null,
    saveError: TaskWriteError? = null,
    isSaving: Boolean = false,
    showDiscardDialog: Boolean = false,
) = TaskEditorState(
    isNew = isNew,
    isLinked = true,
    title = title,
    description = description,
    status = status,
    dueAt = deadline?.let { 1L },
    deadline = deadline,
    canRename = canRename,
    canRemind = canRemind,
    reminderRequestedAt = reminderRequestedAt,
    earlierCards = earlierCards.toImmutableList(),
    titleMaxLength = MatchaConfig.THREAD_NAME_MAX_LENGTH,
    titleError = titleError,
    descriptionError = null,
    deadlineError = deadlineError,
    saveError = saveError,
    isSaving = isSaving,
    hasChanges = title.isNotEmpty(),
    showDiscardDialog = showDiscardDialog,
    eventSink = {},
)
