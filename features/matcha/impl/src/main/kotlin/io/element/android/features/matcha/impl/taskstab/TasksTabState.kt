/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.taskstab

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Immutable
import io.element.android.features.matcha.impl.tasks.TaskEditorState
import io.element.android.features.matcha.impl.tasks.TaskItem
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

data class TasksTabState(
    /** False when the Matcha workspaces feature is off: nothing is drawn. */
    val isVisible: Boolean,
    val filter: TasksTabFilter,
    /** The chat the list is narrowed to with Chat ▾, or null for every chat. */
    val chatFilter: TasksTabChat?,
    /** The person the list is narrowed to with People ▾, or null for everyone the user works with. */
    val personFilter: TasksTabPerson?,
    /** The user's tasks involve someone else, so People ▾ is offered. */
    val hasPeople: Boolean,
    val content: TasksTabContent,
    /** Chats still being checked or read, for "Checking N more chats…". */
    val checkingCount: Int,
    /** Chats that could not be checked or opened, for "Couldn't check N chats". */
    val failedCount: Int,
    /** Some tasks can't be decrypted, so the list may be out of date. */
    val isUnreadable: Boolean,
    /** Some chats have older history that may hold more tasks. */
    val hasMore: Boolean,
    val isLoadingMore: Boolean,
    val sheet: TasksTabSheet?,
    /** The editor sheet, while it is open. */
    val editor: TaskEditorState?,
    val eventSink: (TasksTabEvent) -> Unit,
)

/** The filter chips, in their order. */
enum class TasksTabFilter { All, Todo, Doing, Done, Archived }

/** A chat with a board, as listed by Chat ▾ and Choose a chat. */
@Immutable
data class TasksTabChat(
    val roomId: RoomId,
    val name: String?,
    val avatar: AvatarData,
)

/** Someone the user's tasks involve, as listed by People ▾. */
@Immutable
data class TasksTabPerson(
    val userId: String,
    val name: String,
)

/** A task of one of the user's chats. */
@Immutable
data class TabTask(
    val chat: TasksTabChat,
    val task: TaskItem,
    /** When the task's thread started, for "newest first"; 0 for an older standalone card. */
    val createdAt: Long,
    /** The task's thread is open next to the list (two-pane). */
    val isSelected: Boolean,
    /** The names of the other people the task involves, for "With Bob, Carol"; empty for "Just you". */
    val people: ImmutableList<String>,
)

/** The headers that group the tasks by deadline, in their order. */
enum class TaskSection { Overdue, Today, Tomorrow, Upcoming, NoDeadline }

@Immutable
data class TaskGroup(
    /** The header, or null for a list without headers (Done, Archived). */
    val section: TaskSection?,
    val tasks: ImmutableList<TabTask>,
)

@Immutable
sealed interface TasksTabContent {
    /** No task is known yet and chats are still being checked: skeleton rows. */
    data object Loading : TasksTabContent

    /** None of the user's chats has a task. */
    data object Empty : TasksTabContent

    data class Tasks(
        /** The tasks the chips select; empty when none matches. */
        val groups: ImmutableList<TaskGroup>,
        /** With All, the done tasks, listed in a collapsed "Done · N" row. */
        val done: ImmutableList<TabTask>,
        val isDoneExpanded: Boolean,
    ) : TasksTabContent
}

@Immutable
sealed interface TasksTabSheet {
    /** A searchable list of chats: those with a board for Chat ▾, those where the user can add a task for New task. */
    data class ChooseChat(
        val purpose: ChooseChatPurpose,
        val chats: ImmutableList<TasksTabChat>,
        val searchQuery: TextFieldState,
    ) : TasksTabSheet {
        val visibleChats: ImmutableList<TasksTabChat>
            get() {
                val query = searchQuery.text.toString().trim()
                if (query.isEmpty()) return chats
                return chats.filter { it.name.orEmpty().contains(query, ignoreCase = true) }.toImmutableList()
            }
    }

    /** People ▾: everyone the user works with, or one of them. */
    data class ChoosePerson(
        val people: ImmutableList<TasksTabPerson>,
        /** The chosen person's user id, or null for everyone. */
        val selectedUserId: String?,
    ) : TasksTabSheet

    /** A task's long-press actions. */
    data class Actions(
        val task: TabTask,
        val canEdit: Boolean,
        val canArchive: Boolean,
    ) : TasksTabSheet
}

enum class ChooseChatPurpose {
    /** Chat ▾: narrow the list to one chat. */
    Filter,

    /** New task: the chat the task goes in. */
    NewTask,
}
