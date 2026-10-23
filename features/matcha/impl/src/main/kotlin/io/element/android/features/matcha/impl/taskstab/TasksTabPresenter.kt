/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.taskstab

import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.features.matcha.impl.protocol.TaskStatus
import io.element.android.features.matcha.impl.store.MatchaPermissions
import io.element.android.features.matcha.impl.store.MatchaWarning
import io.element.android.features.matcha.impl.tasks.DeadlineFormatter
import io.element.android.features.matcha.impl.tasks.TaskEditorPresenter
import io.element.android.features.matcha.impl.tasks.TaskEditorTarget
import io.element.android.features.matcha.impl.tasks.TaskItem
import io.element.android.features.matcha.impl.tasks.TaskListActions
import io.element.android.features.matcha.impl.tasks.toTaskItems
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.ThreadId
import io.element.android.services.appnavstate.api.AppNavigationStateService
import io.element.android.services.appnavstate.api.currentRoomId
import io.element.android.services.appnavstate.api.currentThreadId
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.time.Clock

/**
 * The Tasks tab: the tasks of every joined chat with a board, narrowed by status and chat and grouped by deadline, and the sheets
 * that act on them. A task is changed through its chat's own store, which the tab holds while it is shown, so no chat has to be opened.
 */
@Inject
class TasksTabPresenter(
    private val client: MatrixClient,
    private val featureFlagService: FeatureFlagService,
    private val appNavigationStateService: AppNavigationStateService,
    private val snackbarDispatcher: SnackbarDispatcher,
    private val deadlineFormatter: DeadlineFormatter,
    private val clock: Clock,
    private val editorPresenterFactory: TaskEditorPresenter.Factory,
) {
    private sealed interface SheetTarget {
        data class ChooseChat(val purpose: ChooseChatPurpose) : SheetTarget

        data class Actions(val roomId: RoomId, val taskId: String) : SheetTarget
    }

    private data class EditorTarget(val roomId: RoomId, val target: TaskEditorTarget)

    /**
     * @param onOpenRoom shows a chat, for Open chat and for an older standalone card.
     * @param onOpenThread shows a thread of a chat, for a task.
     */
    @Composable
    fun present(onOpenRoom: (RoomId) -> Unit, onOpenThread: (RoomId, ThreadId) -> Unit): TasksTabState {
        val isEnabled by remember { featureFlagService.isFeatureEnabledFlow(FeatureFlags.MatchaWorkspaces) }.collectAsState(initial = false)
        val rooms = remember { TasksTabRooms(client) }
        if (isEnabled) {
            // Leaving the tab cancels this, which destroys every room the tab opened.
            LaunchedEffect(Unit) { rooms.run() }
        }
        val chats by rooms.chats.collectAsState(initial = TabChats())
        val navigation by appNavigationStateService.appNavigationState.collectAsState()
        val currentOnOpenRoom by rememberUpdatedState(onOpenRoom)
        val currentOnOpenThread by rememberUpdatedState(onOpenThread)
        val localScope = rememberCoroutineScope()
        var filter by remember { mutableStateOf(TasksTabFilter.All) }
        var chatFilterId by remember { mutableStateOf<RoomId?>(null) }
        var isDoneExpanded by remember { mutableStateOf(false) }
        var sheetTarget by remember { mutableStateOf<SheetTarget?>(null) }
        var editorTarget by remember { mutableStateOf<EditorTarget?>(null) }

        val openRoomId = navigation.navigationState.currentRoomId()
        val openThreadId = navigation.navigationState.currentThreadId()
        val tasks = remember(chats, openRoomId, openThreadId) { chats.toTabTasks(openRoomId, openThreadId) }
        val boards = chats.boards.associateBy { it.roomId }
        // A chosen chat that the user has left no longer narrows the list.
        val chatFilter = chatFilterId?.let(boards::get)
        val visibleBoards = chatFilter?.let(::listOf) ?: chats.boards
        val visibleTasks = if (chatFilter != null) tasks.filter { it.chat.roomId == chatFilter.roomId } else tasks
        val loadingCount = chats.checkingCount + chats.boards.count { it.isLoading() }
        val content = when {
            tasks.isEmpty() && (!chats.isRoomListLoaded || loadingCount > 0) -> TasksTabContent.Loading
            tasks.isEmpty() -> TasksTabContent.Empty
            else -> tasksContent(visibleTasks, filter, isDoneExpanded)
        }

        val editor = editorTarget?.let { target ->
            val board = boards[target.roomId] ?: return@let null
            key(target, board.room) {
                val editorPresenter = remember { editorPresenterFactory.create(board.room.room, board.room.store, board.room.writeScope) }
                editorPresenter.present(
                    target = target.target,
                    onClose = { editorTarget = null },
                    // ponytail: the reminder sheet is room scoped, so the tab doesn't offer Remind me; the task's chat does.
                    onRemind = null,
                    onOpenThread = {
                        editorTarget = null
                        currentOnOpenThread(target.roomId, it)
                    },
                )
            }
        }

        fun find(roomId: RoomId, taskId: String) = tasks.firstOrNull { it.chat.roomId == roomId && it.task.id == taskId }

        fun handleEvent(event: TasksTabEvent) {
            when (event) {
                is TasksTabEvent.SetFilter -> filter = event.filter
                TasksTabEvent.ToggleChatFilter -> if (chatFilter != null) {
                    chatFilterId = null
                } else {
                    sheetTarget = SheetTarget.ChooseChat(ChooseChatPurpose.Filter)
                }
                is TasksTabEvent.ChooseChat -> {
                    val purpose = (sheetTarget as? SheetTarget.ChooseChat)?.purpose ?: return
                    sheetTarget = null
                    when (purpose) {
                        ChooseChatPurpose.Filter -> chatFilterId = event.roomId
                        ChooseChatPurpose.NewTask -> if (boards[event.roomId]?.canAddTasks() == true) {
                            editorTarget = EditorTarget(event.roomId, TaskEditorTarget.New(filter.newTaskStatus()))
                        }
                    }
                }
                TasksTabEvent.ToggleDone -> isDoneExpanded = !isDoneExpanded
                is TasksTabEvent.OpenTask -> {
                    sheetTarget = null
                    val task = find(event.roomId, event.taskId)?.task ?: return
                    if (task.threadId != null) currentOnOpenThread(event.roomId, task.threadId) else currentOnOpenRoom(event.roomId)
                }
                is TasksTabEvent.ShowActions -> sheetTarget = SheetTarget.Actions(event.roomId, event.taskId)
                is TasksTabEvent.Move -> {
                    sheetTarget = null
                    val board = boards[event.roomId]?.takeIf { it.snapshot.permissions.canWriteCards } ?: return
                    board.listActions().move(event.taskId, event.status)
                }
                is TasksTabEvent.SetArchived -> {
                    sheetTarget = null
                    val task = find(event.roomId, event.taskId)?.task ?: return
                    val board = boards[event.roomId]?.takeIf { it.snapshot.permissions.canArchive(task) } ?: return
                    board.listActions().setArchived(event.taskId, event.archived)
                }
                is TasksTabEvent.EditTask -> {
                    sheetTarget = null
                    if (boards[event.roomId]?.snapshot?.permissions?.canWriteCards == true) {
                        editorTarget = EditorTarget(event.roomId, TaskEditorTarget.Edit(event.taskId))
                    }
                }
                is TasksTabEvent.OpenChat -> {
                    sheetTarget = null
                    currentOnOpenRoom(event.roomId)
                }
                TasksTabEvent.NewTask -> {
                    // With a chat chosen, the new task goes there without asking.
                    val chosen = chatFilter?.takeIf { it.canAddTasks() }
                    if (chosen != null) {
                        editorTarget = EditorTarget(chosen.roomId, TaskEditorTarget.New(filter.newTaskStatus()))
                    } else {
                        sheetTarget = SheetTarget.ChooseChat(ChooseChatPurpose.NewTask)
                    }
                }
                TasksTabEvent.DismissSheet -> sheetTarget = null
                TasksTabEvent.Retry -> rooms.retry()
                TasksTabEvent.LoadMore -> visibleBoards
                    .filter { it.snapshot.hasMoreHistory && !it.snapshot.isLoadingMore }
                    .forEach { board -> localScope.launch { board.room.store.loadMore() } }
            }
        }

        val sheet = when (val target = sheetTarget) {
            null -> null
            is SheetTarget.ChooseChat -> key(target) {
                val searchQuery = rememberTextFieldState()
                val choices = if (target.purpose == ChooseChatPurpose.NewTask) chats.boards.filter { it.canAddTasks() } else chats.boards
                TasksTabSheet.ChooseChat(target.purpose, choices.map { it.toTasksTabChat() }.toImmutableList(), searchQuery)
            }
            is SheetTarget.Actions -> find(target.roomId, target.taskId)?.let { task ->
                val permissions = boards[target.roomId]?.snapshot?.permissions ?: MatchaPermissions()
                TasksTabSheet.Actions(task = task, canEdit = permissions.canWriteCards, canArchive = permissions.canArchive(task.task))
            }
        }

        return TasksTabState(
            isVisible = isEnabled,
            filter = filter,
            chatFilter = chatFilter?.toTasksTabChat(),
            content = content,
            checkingCount = loadingCount,
            failedCount = chats.failedCount + chats.boards.count { it.snapshot.loadFailed },
            isUnreadable = visibleBoards.any { it.snapshot.unreadable },
            hasMore = visibleBoards.any { it.snapshot.hasMoreHistory },
            isLoadingMore = visibleBoards.any { it.snapshot.isLoadingMore },
            sheet = sheet,
            editor = editor,
            eventSink = ::handleEvent,
        )
    }

    private fun TabChat.listActions() = TaskListActions(room.store, snackbarDispatcher, room.writeScope)

    /** Every task of every chat, soonest deadline first (none last), then newest. */
    private fun TabChats.toTabTasks(openRoomId: RoomId?, openThreadId: ThreadId?): List<TabTask> {
        val nowMillis = clock.now().toEpochMilliseconds()
        return boards
            .flatMap { board ->
                val chat = board.toTasksTabChat()
                val rootTimestamps = board.snapshot.threads.associate { it.id to it.rootTimestamp }
                board.snapshot.toTaskItems(deadlineFormatter, nowMillis).map { task ->
                    TabTask(
                        chat = chat,
                        task = task,
                        createdAt = task.threadId?.let(rootTimestamps::get) ?: 0,
                        isSelected = board.roomId == openRoomId && task.threadId != null && task.threadId == openThreadId,
                    )
                }
            }
            .sortedWith(compareBy<TabTask> { it.task.dueAt ?: Long.MAX_VALUE }.thenByDescending { it.createdAt })
    }

    private fun tasksContent(tasks: List<TabTask>, filter: TasksTabFilter, isDoneExpanded: Boolean): TasksTabContent.Tasks {
        val (archived, active) = tasks.partition { it.task.isArchived }
        val newestFirst = compareByDescending<TabTask> { it.createdAt }
        val done = active.filter { it.task.status == TaskStatus.Done }.sortedWith(newestFirst)
        val groups = when (filter) {
            TasksTabFilter.All -> byDeadline(active.filter { it.task.status != TaskStatus.Done })
            TasksTabFilter.Todo -> byDeadline(active.filter { it.task.status == TaskStatus.Todo })
            TasksTabFilter.Doing -> byDeadline(active.filter { it.task.status == TaskStatus.Doing })
            TasksTabFilter.Done -> listOf(TaskGroup(section = null, tasks = done.toImmutableList()))
            TasksTabFilter.Archived -> listOf(TaskGroup(section = null, tasks = archived.sortedWith(newestFirst).toImmutableList()))
        }
        return TasksTabContent.Tasks(
            groups = groups.filter { it.tasks.isNotEmpty() }.toImmutableList(),
            done = if (filter == TasksTabFilter.All) done.toImmutableList() else persistentListOf(),
            isDoneExpanded = isDoneExpanded,
        )
    }

    /** Groups tasks already in deadline order under Overdue, Today, Tomorrow, Upcoming and No deadline, leaving out empty groups. */
    private fun byDeadline(tasks: List<TabTask>): List<TaskGroup> {
        val zone = ZoneId.systemDefault()
        val today = Instant.ofEpochMilli(clock.now().toEpochMilliseconds()).atZone(zone).toLocalDate()
        val grouped = tasks.groupBy { it.task.section(today, zone) }
        return TaskSection.entries.mapNotNull { section -> grouped[section]?.let { TaskGroup(section, it.toImmutableList()) } }
    }
}

private fun TaskItem.section(today: LocalDate, zone: ZoneId): TaskSection {
    val dueAt = dueAt ?: return TaskSection.NoDeadline
    if (isOverdue) return TaskSection.Overdue
    return when (Instant.ofEpochMilli(dueAt).atZone(zone).toLocalDate()) {
        today -> TaskSection.Today
        today.plusDays(1) -> TaskSection.Tomorrow
        else -> TaskSection.Upcoming
    }
}

/** A new task starts in the status the chips show, and in To do anywhere else. */
private fun TasksTabFilter.newTaskStatus() = when (this) {
    TasksTabFilter.Doing -> TaskStatus.Doing
    TasksTabFilter.Done -> TaskStatus.Done
    else -> TaskStatus.Todo
}

/** The chat's store is still reading; one whose read failed offers "Load older tasks" instead of checking forever. */
private fun TabChat.isLoading() = !snapshot.isLoaded && !snapshot.loadFailed && snapshot.warning != MatchaWarning.Network

/** Adding a task also creates its thread. */
private fun TabChat.canAddTasks(): Boolean {
    val permissions = snapshot.permissions
    return snapshot.board != null && permissions.canWriteCards && permissions.canManageThreads && permissions.canSendMessages
}

/** Archiving and restoring a thread's task also changes the thread. */
private fun MatchaPermissions.canArchive(task: TaskItem) = canWriteCards && (task.threadId == null || canManageThreads)

private fun TabChat.toTasksTabChat() = TasksTabChat(
    roomId = roomId,
    name = name,
    avatar = AvatarData(id = roomId.value, name = name, url = avatarUrl, size = AvatarSize.RoomListItem),
)
