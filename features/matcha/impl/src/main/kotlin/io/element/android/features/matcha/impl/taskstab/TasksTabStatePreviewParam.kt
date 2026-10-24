/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.taskstab

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.features.matcha.impl.protocol.TaskStatus
import io.element.android.features.matcha.impl.tasks.TaskItem
import io.element.android.features.matcha.impl.tasks.aTaskItem
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

open class TasksTabStatePreviewParam : PreviewParameterProvider<TasksTabState> {
    override val values: Sequence<TasksTabState>
        get() = sequenceOf(
            aTasksTabState(),
            aTasksTabState(content = TasksTabContent.Loading, checkingCount = 12),
            aTasksTabState(content = TasksTabContent.Empty),
            aTasksTabState(content = aTasksContent(isDoneExpanded = true)),
            aTasksTabState(
                filter = TasksTabFilter.Done,
                content = TasksTabContent.Tasks(
                    groups = persistentListOf(TaskGroup(section = null, tasks = aDoneTasks())),
                    done = persistentListOf(),
                    isDoneExpanded = false,
                ),
            ),
            aTasksTabState(
                filter = TasksTabFilter.Archived,
                chatFilter = aDesignChat,
                content = TasksTabContent.Tasks(groups = persistentListOf(), done = persistentListOf(), isDoneExpanded = false),
            ),
            aTasksTabState(checkingCount = 3, failedCount = 2, isUnreadable = true, hasMore = true),
            aTasksTabState(hasMore = true, isLoadingMore = true),
            aTasksTabState(sheet = TasksTabSheet.ChooseChat(ChooseChatPurpose.NewTask, persistentListOf(aDesignChat, aLaunchChat), TextFieldState())),
            aTasksTabState(sheet = TasksTabSheet.ChooseChat(ChooseChatPurpose.NewTask, persistentListOf(), TextFieldState())),
            aTasksTabState(
                chatFilter = null,
                sheet = TasksTabSheet.ChooseChat(ChooseChatPurpose.Filter, persistentListOf(aDesignChat, aLaunchChat), TextFieldState("zzz")),
            ),
            aTasksTabState(sheet = TasksTabSheet.Actions(task = aTabTask(aTaskItem(id = "t1", title = "Book the venue")), canEdit = true, canArchive = true)),
            aTasksTabState(sheet = TasksTabSheet.Actions(task = aTabTask(aTaskItem(id = "t1", title = "Book the venue")), canEdit = false, canArchive = false)),
            aTasksTabState(personFilter = aBob),
            aTasksTabState(sheet = TasksTabSheet.ChoosePerson(persistentListOf(aBob, aCarol), selectedUserId = null)),
            aTasksTabState(personFilter = aCarol, sheet = TasksTabSheet.ChoosePerson(persistentListOf(aBob, aCarol), selectedUserId = aCarol.userId)),
            aTasksTabState(isTable = true, content = aTableContent()),
            aTasksTabState(isTable = true, content = aTableContent(sort = TaskTableSort(TaskTableColumn.Task, isAscending = false))),
            aTasksTabState(isTable = true, filter = TasksTabFilter.Archived, content = TasksTabContent.Table(persistentListOf(), TaskTableSort())),
            aTasksTabState(spaceName = "Acme"),
            aTasksTabState(spaceName = "Acme", content = TasksTabContent.Empty),
            aTasksTabState(spaceName = "", content = TasksTabContent.Loading, checkingCount = 3),
        )
}

internal fun aTasksTabState(
    filter: TasksTabFilter = TasksTabFilter.All,
    chatFilter: TasksTabChat? = null,
    content: TasksTabContent = aTasksContent(),
    checkingCount: Int = 0,
    failedCount: Int = 0,
    isUnreadable: Boolean = false,
    hasMore: Boolean = false,
    isLoadingMore: Boolean = false,
    sheet: TasksTabSheet? = null,
    personFilter: TasksTabPerson? = null,
    hasPeople: Boolean = true,
    isTable: Boolean = false,
    spaceName: String? = null,
) = TasksTabState(
    isVisible = true,
    filter = filter,
    chatFilter = chatFilter,
    personFilter = personFilter,
    hasPeople = hasPeople,
    isTable = isTable,
    content = content,
    checkingCount = checkingCount,
    failedCount = failedCount,
    isUnreadable = isUnreadable,
    hasMore = hasMore,
    isLoadingMore = isLoadingMore,
    spaceName = spaceName,
    sheet = sheet,
    editor = null,
    eventSink = {},
)

private val aDesignChat = aTasksTabChat("!design:matrix.org", "Design team")
private val aLaunchChat = aTasksTabChat("!launch:matrix.org", "Launch")
private val aBob = TasksTabPerson("@bob:matrix.org", "Bob")
private val aCarol = TasksTabPerson("@carol:matrix.org", "Carol")

private fun aTasksTabChat(roomId: String, name: String) = TasksTabChat(
    roomId = RoomId(roomId),
    name = name,
    avatar = AvatarData(id = roomId, name = name, size = AvatarSize.RoomListItem),
)

private fun aTabTask(task: TaskItem, chat: TasksTabChat = aDesignChat, isSelected: Boolean = false, people: List<String> = emptyList()) =
    TabTask(chat = chat, task = task, createdAt = 0, isSelected = isSelected, people = people.toImmutableList())

private fun aDoneTasks() = persistentListOf(
    aTabTask(aTaskItem(id = "d1", title = "Pick the date", status = TaskStatus.Done, replyCount = 12)),
    aTabTask(aTaskItem(id = "d2", title = "Send the invites", status = TaskStatus.Done), chat = aLaunchChat),
)

internal fun aTableContent(sort: TaskTableSort = TaskTableSort()) = TasksTabContent.Table(
    rows = listOf(
        aTabTask(
            aTaskItem(id = "t1", title = "Book the venue", deadline = "Yesterday", isOverdue = true, isSharedWithChat = true),
            people = listOf("Bob", "Carol"),
        ),
        aTabTask(aTaskItem(id = "t2", title = "Draft the press release", deadline = "Today 17:00"), chat = aLaunchChat, isSelected = true),
        aTabTask(aTaskItem(id = "t3", title = "Order the banners", status = TaskStatus.Doing, deadline = "Tue 17:00"), people = listOf("Bob")),
        aTabTask(aTaskItem(id = "t4", title = null), people = listOf("Carol")),
        aTabTask(aTaskItem(id = "d1", title = "Pick the date", status = TaskStatus.Done), chat = aLaunchChat, people = listOf("Bob", "Carol", "Dan")),
    ).toImmutableList(),
    sort = sort,
)

private fun aTasksContent(isDoneExpanded: Boolean = false) = TasksTabContent.Tasks(
    groups = listOf(
        TaskGroup(
            section = TaskSection.Overdue,
            tasks = persistentListOf(
                aTabTask(
                    aTaskItem(id = "t1", title = "Book the venue", deadline = "Yesterday", isOverdue = true, hasReminder = true, isSharedWithChat = true),
                    people = listOf("Bob", "Carol"),
                ),
            ),
        ),
        TaskGroup(
            section = TaskSection.Today,
            tasks = persistentListOf(
                aTabTask(
                    aTaskItem(id = "t2", title = "Draft the press release", summary = "Two paragraphs, then quotes", deadline = "Today 17:00"),
                    chat = aLaunchChat,
                    isSelected = true,
                ),
            ),
        ),
        TaskGroup(
            section = TaskSection.Upcoming,
            tasks = persistentListOf(
                aTabTask(aTaskItem(id = "t3", title = "Order the banners", status = TaskStatus.Doing, deadline = "Tue 17:00"), people = listOf("Bob")),
            ),
        ),
        TaskGroup(
            section = TaskSection.NoDeadline,
            tasks = persistentListOf(
                aTabTask(aTaskItem(id = "t4", title = null, replyCount = 0)),
                aTabTask(aTaskItem(id = "legacy", title = "An older card without a thread", threadId = null), chat = aLaunchChat),
            ),
        ),
    ).toImmutableList(),
    done = aDoneTasks(),
    isDoneExpanded = isDoneExpanded,
)
