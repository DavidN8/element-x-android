/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.tasks

import io.element.android.features.matcha.impl.protocol.TaskStatus
import io.element.android.libraries.matrix.api.core.ThreadId

sealed interface TaskBoardEvent {
    data object CreateBoard : TaskBoardEvent
    data object RetryLoad : TaskBoardEvent

    /** Tap on a task: its thread, or the editor for an older standalone card. */
    data class OpenTask(val taskId: String) : TaskBoardEvent

    /** Long-press on a task. */
    data class ShowActions(val taskId: String) : TaskBoardEvent
    data object DismissActions : TaskBoardEvent
    data class Move(val taskId: String, val status: TaskStatus) : TaskBoardEvent
    data class SetArchived(val taskId: String, val archived: Boolean) : TaskBoardEvent
    data class RemindMe(val taskId: String) : TaskBoardEvent
    data object DismissReminder : TaskBoardEvent

    /** The New task button, on the tab of [status]. */
    data class NewTask(val status: TaskStatus) : TaskBoardEvent
    data class EditTask(val taskId: String) : TaskBoardEvent

    /** Edits the task of [threadId], from a thread chip's "View task". */
    data class EditThreadTask(val threadId: ThreadId) : TaskBoardEvent
    data object ShowArchived : TaskBoardEvent
    data object HideArchived : TaskBoardEvent
    data object LoadMore : TaskBoardEvent
}
