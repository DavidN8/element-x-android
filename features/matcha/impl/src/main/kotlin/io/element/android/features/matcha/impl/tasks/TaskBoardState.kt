/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.tasks

import androidx.compose.runtime.Immutable
import io.element.android.features.matcha.api.MatchaReminderTarget
import io.element.android.features.matcha.impl.protocol.TaskStatus
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarMessage
import kotlinx.collections.immutable.ImmutableList

data class TaskBoardState(
    /** False when the Matcha workspaces feature is off: nothing is drawn. */
    val isVisible: Boolean,
    val roomName: String?,
    val roomAvatar: AvatarData,
    /** The chat has a joined parent space, so the chip bar offers its docs. */
    val hasSpaceDocs: Boolean,
    val content: TaskBoardContent,
    /** Archived tasks are shown instead of the board. */
    val showArchived: Boolean,
    /** The task whose actions sheet is open. */
    val actionsSheet: TaskItem?,
    /** The editor sheet, while it is open. */
    val editor: TaskEditorState?,
    /** The reminder sheet is shown while this is set. */
    val reminderTarget: MatchaReminderTarget?,
    val snackbarMessage: SnackbarMessage?,
    val eventSink: (TaskBoardEvent) -> Unit,
)

@Immutable
sealed interface TaskBoardContent {
    /** The board isn't proven absent yet, so creating a board could replace one made elsewhere; the history is being read. */
    data object LookingForBoard : TaskBoardContent

    /** The Matcha data of the chat could not be opened, or its history could not be read to look for the board. */
    data object LoadFailed : TaskBoardContent

    /** The chat has no board. */
    data class Setup(
        val canCreate: Boolean,
        val isCreating: Boolean,
        val createFailed: Boolean,
    ) : TaskBoardContent

    data class Board(
        val todo: ImmutableList<TaskItem>,
        val doing: ImmutableList<TaskItem>,
        val done: ImmutableList<TaskItem>,
        val archived: ImmutableList<TaskItem>,
        /** The tasks are still being read: skeleton rows stand in for them. */
        val isLoading: Boolean,
        /** The user may change tasks; otherwise the board is read-only. */
        val canEdit: Boolean,
        /** The user may rename and archive threads, so also linked tasks. */
        val canManageThreads: Boolean,
        /** The user may add tasks, which also creates their threads. */
        val canAdd: Boolean,
        val canRemind: Boolean,
        /** Some task updates can't be decrypted, so the board may be out of date. */
        val isUnreadable: Boolean,
        val hasMore: Boolean,
        val isLoadingMore: Boolean,
    ) : TaskBoardContent {
        fun tasks(status: TaskStatus): ImmutableList<TaskItem> = when (status) {
            TaskStatus.Todo -> todo
            TaskStatus.Doing -> doing
            TaskStatus.Done -> done
        }

        /** Archive and Unarchive also change a linked task's thread. */
        fun canArchive(task: TaskItem) = canEdit && (task.threadId == null || canManageThreads)
    }
}
