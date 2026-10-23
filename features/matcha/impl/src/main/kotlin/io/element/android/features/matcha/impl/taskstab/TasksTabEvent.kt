/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.taskstab

import io.element.android.features.matcha.impl.protocol.TaskStatus
import io.element.android.libraries.matrix.api.core.RoomId

sealed interface TasksTabEvent {
    data class SetFilter(val filter: TasksTabFilter) : TasksTabEvent

    /** Chat ▾: lists the chats to choose from, or shows every chat again when one is chosen. */
    data object ToggleChatFilter : TasksTabEvent

    /** A chat picked in the Choose a chat sheet, for its purpose. */
    data class ChooseChat(val roomId: RoomId) : TasksTabEvent
    data object ToggleDone : TasksTabEvent

    /** Tap on a task: its thread, or its chat for an older standalone card. */
    data class OpenTask(val roomId: RoomId, val taskId: String) : TasksTabEvent

    /** Long-press on a task. */
    data class ShowActions(val roomId: RoomId, val taskId: String) : TasksTabEvent
    data class Move(val roomId: RoomId, val taskId: String, val status: TaskStatus) : TasksTabEvent
    data class SetArchived(val roomId: RoomId, val taskId: String, val archived: Boolean) : TasksTabEvent
    data class EditTask(val roomId: RoomId, val taskId: String) : TasksTabEvent
    data class OpenChat(val roomId: RoomId) : TasksTabEvent

    /** The New task button: Choose a chat, then the editor. */
    data object NewTask : TasksTabEvent
    data object DismissSheet : TasksTabEvent
    data object Retry : TasksTabEvent
    data object LoadMore : TasksTabEvent
}
