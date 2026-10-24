/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.tasks

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.features.matcha.impl.protocol.TaskStatus
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.matrix.api.core.ThreadId
import kotlinx.collections.immutable.toImmutableList

open class TaskBoardStatePreviewParam : PreviewParameterProvider<TaskBoardState> {
    override val values: Sequence<TaskBoardState>
        get() = sequenceOf(
            aTaskBoardState(),
            aTaskBoardState(content = TaskBoardContent.LookingForBoard),
            aTaskBoardState(content = TaskBoardContent.Setup(canCreate = true, isCreating = false, createFailed = false)),
            aTaskBoardState(content = TaskBoardContent.Setup(canCreate = true, isCreating = true, createFailed = false)),
            aTaskBoardState(content = TaskBoardContent.Setup(canCreate = true, isCreating = false, createFailed = true)),
            aTaskBoardState(content = TaskBoardContent.Setup(canCreate = false, isCreating = false, createFailed = false)),
            aTaskBoardState(content = aBoardContent(tasks = emptyList(), archived = emptyList())),
            aTaskBoardState(content = aBoardContent(tasks = emptyList(), archived = emptyList(), isLoading = true)),
            aTaskBoardState(content = TaskBoardContent.LoadFailed),
            aTaskBoardState(content = aBoardContent(isUnreadable = true)),
            aTaskBoardState(content = aBoardContent(canEdit = false)),
            aTaskBoardState(content = aBoardContent(hasMore = true, isLoadingMore = true)),
            aTaskBoardState(actionsSheet = aTaskItemList().first()),
            aTaskBoardState(showArchived = true),
            aTaskBoardState(editor = aTaskEditorState()),
            aTaskBoardState(content = aBoardContent(tasks = aTaskItemList().map { it.copy(isSharedWithChat = it.id in setOf("t1", "t4", "legacy")) })),
        )
}

internal fun aTaskBoardState(
    content: TaskBoardContent = aBoardContent(),
    showArchived: Boolean = false,
    actionsSheet: TaskItem? = null,
    editor: TaskEditorState? = null,
) = TaskBoardState(
    isVisible = true,
    roomName = "Product launch",
    roomAvatar = AvatarData(id = "!room:matrix.org", name = "Product launch", size = AvatarSize.TimelineRoom),
    hasSpaceDocs = true,
    content = content,
    showArchived = showArchived,
    actionsSheet = actionsSheet,
    editor = editor,
    reminderTarget = null,
    snackbarMessage = null,
    eventSink = {},
)

internal fun aBoardContent(
    tasks: List<TaskItem> = aTaskItemList(),
    archived: List<TaskItem> = listOf(aTaskItem(id = "archived", title = "Old venue list", isArchived = true)),
    isLoading: Boolean = false,
    canEdit: Boolean = true,
    isUnreadable: Boolean = false,
    hasMore: Boolean = false,
    isLoadingMore: Boolean = false,
) = TaskBoardContent.Board(
    todo = tasks.filter { it.status == TaskStatus.Todo }.toImmutableList(),
    doing = tasks.filter { it.status == TaskStatus.Doing }.toImmutableList(),
    done = tasks.filter { it.status == TaskStatus.Done }.toImmutableList(),
    archived = archived.toImmutableList(),
    isLoading = isLoading,
    canEdit = canEdit,
    canManageThreads = canEdit,
    canAdd = canEdit,
    canRemind = true,
    isUnreadable = isUnreadable,
    hasMore = hasMore,
    isLoadingMore = isLoadingMore,
)

internal fun aTaskItemList() = listOf(
    aTaskItem(id = "t1", title = "Book the venue", deadline = "Yesterday", isOverdue = true, hasReminder = true),
    aTaskItem(id = "t2", title = "Draft the press release", summary = "Two paragraphs, then quotes from the team", deadline = "Tomorrow 09:00"),
    aTaskItem(id = "t3", title = null, replyCount = 0),
    aTaskItem(id = "t4", title = "Order the banners", status = TaskStatus.Doing, deadline = "Tue 17:00", replyCount = 4),
    aTaskItem(id = "t5", title = "Pick the date", status = TaskStatus.Done, replyCount = 12),
    aTaskItem(id = "legacy", title = "An older card without a thread", threadId = null),
)

internal fun aTaskItem(
    id: String,
    title: String?,
    summary: String = "",
    status: TaskStatus = TaskStatus.Todo,
    isArchived: Boolean = false,
    threadId: ThreadId? = ThreadId("\$$id"),
    deadline: String? = null,
    isOverdue: Boolean = false,
    replyCount: Int? = threadId?.let { 1 },
    hasReminder: Boolean = false,
    isSharedWithChat: Boolean = false,
    participants: Set<String> = emptySet(),
) = TaskItem(
    id = id,
    title = title,
    summary = summary,
    status = status,
    isArchived = isArchived,
    threadId = threadId,
    dueAt = deadline?.let { 1L },
    deadline = deadline,
    isOverdue = isOverdue,
    replyCount = replyCount,
    hasReminder = hasReminder,
    isSharedWithChat = isSharedWithChat,
    participants = participants,
)
