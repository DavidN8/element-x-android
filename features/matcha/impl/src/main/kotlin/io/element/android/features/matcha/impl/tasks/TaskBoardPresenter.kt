/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.tasks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.features.matcha.api.MatchaReminderTarget
import io.element.android.features.matcha.impl.R
import io.element.android.features.matcha.impl.chatbar.rememberCanRemind
import io.element.android.features.matcha.impl.protocol.TaskStatus
import io.element.android.features.matcha.impl.store.MatchaPermissions
import io.element.android.features.matcha.impl.store.MatchaRoomSnapshot
import io.element.android.features.matcha.impl.store.MatchaRoomStore
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarMessage
import io.element.android.libraries.designsystem.utils.snackbar.collectSnackbarMessageAsState
import io.element.android.libraries.di.annotations.RoomCoroutineScope
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.matrix.api.core.ThreadId
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.spaces.SpaceService
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlin.time.Clock

/**
 * A chat's task board, its archived tasks, and the sheets on top of them. It opens with the editor for the task of
 * `initialThreadId` when one is given (a thread chip's "View task"), and `openThread` shows a thread in place of the board.
 *
 * Writes run in the room's scope, so they finish (and their snackbars still show) when the user leaves the board.
 */
@AssistedInject
class TaskBoardPresenter(
    @Assisted initialThreadId: ThreadId?,
    @Assisted private val openThread: (ThreadId) -> Unit,
    private val room: JoinedRoom,
    private val store: MatchaRoomStore,
    private val spaceService: SpaceService,
    private val featureFlagService: FeatureFlagService,
    private val snackbarDispatcher: SnackbarDispatcher,
    private val deadlineFormatter: DeadlineFormatter,
    private val clock: Clock,
    editorPresenterFactory: TaskEditorPresenter.Factory,
    @RoomCoroutineScope private val roomCoroutineScope: CoroutineScope,
) : Presenter<TaskBoardState> {
    @AssistedFactory
    fun interface Factory {
        fun create(initialThreadId: ThreadId?, openThread: (ThreadId) -> Unit): TaskBoardPresenter
    }

    private val writer = TaskWriter(store)
    private val listActions = TaskListActions(store, snackbarDispatcher, roomCoroutineScope)
    private val editorPresenter = editorPresenterFactory.create(room, store, roomCoroutineScope)

    // Outlives the composition, so the editor opens once rather than every time the user comes back to the board.
    private var pendingThreadId: ThreadId? = initialThreadId

    @Composable
    override fun present(): TaskBoardState {
        val isEnabled by remember { featureFlagService.isFeatureEnabledFlow(FeatureFlags.MatchaWorkspaces) }.collectAsState(initial = false)
        val snapshot by store.snapshot.collectAsState()
        val roomInfo by room.roomInfoFlow.collectAsState()
        val hasSpaceDocs by produceState(false) {
            value = spaceService.joinedParents(room.roomId).getOrNull().orEmpty().isNotEmpty()
        }
        val snackbarMessage by snackbarDispatcher.collectSnackbarMessageAsState()
        val localScope = rememberCoroutineScope()
        var editorTarget by rememberSaveable { mutableStateOf<TaskEditorTarget?>(null) }
        var showArchived by rememberSaveable { mutableStateOf(false) }
        var actionsTaskId by remember { mutableStateOf<String?>(null) }
        var reminderTarget by remember { mutableStateOf<MatchaReminderTarget?>(null) }
        var isCreatingBoard by remember { mutableStateOf(false) }
        var createFailed by remember { mutableStateOf(false) }

        val permissions = snapshot.permissions
        val canRemind = rememberCanRemind(room, permissions.canSendMessages)
        val tasks = remember(snapshot) { snapshot.toTaskItems(deadlineFormatter, clock.now().toEpochMilliseconds()) }
        val content = boardContent(snapshot, tasks, canRemind, isCreatingBoard, createFailed)

        LaunchedEffect(snapshot) {
            val threadId = pendingThreadId ?: return@LaunchedEffect
            val task = tasks.firstOrNull { it.threadId == threadId }
            when {
                task != null && permissions.canWriteCards -> editorTarget = TaskEditorTarget.Edit(task.id)
                task != null -> actionsTaskId = task.id
                // Without a board, wait: the task exists as soon as the user creates the board.
                snapshot.board == null || !snapshot.isLoaded -> return@LaunchedEffect
            }
            pendingThreadId = null
        }

        val editor = editorTarget?.let { target ->
            key(target) {
                editorPresenter.present(
                    target = target,
                    onClose = { editorTarget = null },
                    onRemind = { reminderTarget = it },
                    onOpenThread = {
                        editorTarget = null
                        openThread(it)
                    },
                )
            }
        }

        fun handleEvent(event: TaskBoardEvent) {
            val board = content as? TaskBoardContent.Board
            when (event) {
                TaskBoardEvent.CreateBoard -> {
                    if (isCreatingBoard || snapshot.board != null || !permissions.canCreateBoard) return
                    isCreatingBoard = true
                    createFailed = false
                    localScope.launch {
                        when (roomCoroutineScope.async { writer.createBoard() }.await()) {
                            CreateBoardResult.Created -> Unit
                            CreateBoardResult.AlreadyExists -> snackbarDispatcher.post(SnackbarMessage(R.string.screen_matcha_tasks_already_has_board))
                            CreateBoardResult.Failed -> createFailed = true
                        }
                        isCreatingBoard = false
                    }
                }
                TaskBoardEvent.RetryLoad -> store.retryLoad()
                is TaskBoardEvent.OpenTask -> {
                    val task = tasks.firstOrNull { it.id == event.taskId } ?: return
                    when {
                        task.threadId != null -> openThread(task.threadId)
                        board?.canEdit == true -> editorTarget = TaskEditorTarget.Edit(task.id)
                        else -> actionsTaskId = task.id
                    }
                }
                is TaskBoardEvent.ShowActions -> actionsTaskId = event.taskId
                TaskBoardEvent.DismissActions -> actionsTaskId = null
                is TaskBoardEvent.Move -> {
                    actionsTaskId = null
                    if (board?.canEdit == true) listActions.move(event.taskId, event.status)
                }
                is TaskBoardEvent.SetArchived -> {
                    actionsTaskId = null
                    val task = tasks.firstOrNull { it.id == event.taskId } ?: return
                    if (board?.canArchive(task) == true) listActions.setArchived(event.taskId, event.archived)
                }
                is TaskBoardEvent.RemindMe -> {
                    actionsTaskId = null
                    val task = tasks.firstOrNull { it.id == event.taskId }
                    if (task != null && !task.isArchived && canRemind) {
                        reminderTarget = MatchaReminderTarget(message = task.title.orEmpty(), threadId = task.threadId, dueAt = task.dueAt, cardId = task.id)
                    }
                }
                TaskBoardEvent.DismissReminder -> reminderTarget = null
                is TaskBoardEvent.NewTask -> if (board?.canAdd == true) editorTarget = TaskEditorTarget.New(event.status)
                is TaskBoardEvent.EditTask -> {
                    actionsTaskId = null
                    if (board?.canEdit == true) editorTarget = TaskEditorTarget.Edit(event.taskId)
                }
                is TaskBoardEvent.EditThreadTask -> tasks.firstOrNull { it.threadId == event.threadId }?.let { task ->
                    if (board?.canEdit == true) editorTarget = TaskEditorTarget.Edit(task.id) else actionsTaskId = task.id
                }
                TaskBoardEvent.ShowArchived -> showArchived = true
                TaskBoardEvent.HideArchived -> showArchived = false
                TaskBoardEvent.LoadMore -> roomCoroutineScope.launch { store.loadMore() }
            }
        }

        return TaskBoardState(
            isVisible = isEnabled,
            roomName = roomInfo.name,
            roomAvatar = AvatarData(id = room.roomId.value, name = roomInfo.name, url = roomInfo.avatarUrl, size = AvatarSize.TimelineRoom),
            hasSpaceDocs = hasSpaceDocs,
            content = content,
            showArchived = showArchived,
            actionsSheet = actionsTaskId?.let { id -> tasks.firstOrNull { it.id == id } },
            editor = editor,
            reminderTarget = reminderTarget,
            snackbarMessage = snackbarMessage,
            eventSink = ::handleEvent,
        )
    }

    private fun boardContent(
        snapshot: MatchaRoomSnapshot,
        tasks: List<TaskItem>,
        canRemind: Boolean,
        isCreatingBoard: Boolean,
        createFailed: Boolean,
    ): TaskBoardContent {
        val permissions: MatchaPermissions = snapshot.permissions
        return when {
            snapshot.board != null -> {
                val (archived, active) = tasks.partition { it.isArchived }
                TaskBoardContent.Board(
                    todo = active.filter { it.status == TaskStatus.Todo }.toImmutableList(),
                    doing = active.filter { it.status == TaskStatus.Doing }.toImmutableList(),
                    done = active.filter { it.status == TaskStatus.Done }.toImmutableList(),
                    archived = archived.toImmutableList(),
                    isLoading = !snapshot.isLoaded && tasks.isEmpty(),
                    canEdit = permissions.canWriteCards,
                    canManageThreads = permissions.canManageThreads,
                    // Adding a task also creates its thread.
                    canAdd = permissions.canWriteCards && permissions.canManageThreads && permissions.canSendMessages,
                    canRemind = canRemind,
                    isUnreadable = snapshot.unreadable,
                    hasMore = snapshot.hasMoreHistory,
                    isLoadingMore = snapshot.isLoadingMore,
                )
            }
            snapshot.loadFailed -> TaskBoardContent.LoadFailed
            // Until the pointer is read, a board made elsewhere may exist: offering to create one could orphan its cards.
            !snapshot.isLoaded || !snapshot.boardPointerRead -> TaskBoardContent.LookingForBoard
            else -> TaskBoardContent.Setup(canCreate = permissions.canCreateBoard, isCreating = isCreatingBoard, createFailed = createFailed)
        }
    }
}
