/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.chatbar

import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.appconfig.MatchaConfig
import io.element.android.features.matcha.api.MatchaChatBarActions
import io.element.android.features.matcha.api.MatchaChatBarSelection
import io.element.android.features.matcha.api.MatchaReminderTarget
import io.element.android.features.matcha.impl.R
import io.element.android.features.matcha.impl.protocol.TaskStatus
import io.element.android.features.matcha.impl.protocol.ThreadMetadataProtocol
import io.element.android.features.matcha.impl.protocol.ThreadNameSource
import io.element.android.features.matcha.impl.store.MatchaRoomSnapshot
import io.element.android.features.matcha.impl.store.MatchaRoomStore
import io.element.android.features.matcha.impl.store.MatchaThread
import io.element.android.features.matcha.impl.store.NamedThreadResult
import io.element.android.features.matcha.impl.store.createNamedThread
import io.element.android.features.matcha.impl.store.retrySetup
import io.element.android.features.matcha.impl.tasks.TaskEditorPresenter
import io.element.android.features.matcha.impl.tasks.TaskEditorTarget
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarMessage
import io.element.android.libraries.di.annotations.RoomCoroutineScope
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.matrix.api.core.ThreadId
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * The chat chip bar: General, the active threads, New thread and More. The chat's top bar has All threads and the task board.
 *
 * Writes run in the room's scope, so they finish (and their snackbars still show) when the screen under the bar is
 * replaced, for example after archiving the open thread; navigation only happens while this bar is still shown.
 */
@Inject
class ChatBarPresenter(
    private val room: JoinedRoom,
    private val store: MatchaRoomStore,
    private val controller: DefaultMatchaRoomController,
    private val featureFlagService: FeatureFlagService,
    private val snackbarDispatcher: SnackbarDispatcher,
    @RoomCoroutineScope private val roomCoroutineScope: CoroutineScope,
    editorPresenterFactory: TaskEditorPresenter.Factory,
) {
    private val editorPresenter = editorPresenterFactory.create(room, store, roomCoroutineScope)

    @Composable
    fun present(selection: MatchaChatBarSelection, actions: MatchaChatBarActions): ChatBarState {
        val isEnabled by remember { featureFlagService.isFeatureEnabledFlow(FeatureFlags.MatchaWorkspaces) }.collectAsState(initial = false)
        val snapshot by store.snapshot.collectAsState()
        val currentSelection by rememberUpdatedState(selection)
        val currentActions by rememberUpdatedState(actions)
        val localScope = rememberCoroutineScope()
        var sheet by remember { mutableStateOf<ChatBarSheet?>(null) }
        var reminderTarget by remember { mutableStateOf<MatchaReminderTarget?>(null) }
        var editorTarget by rememberSaveable { mutableStateOf<TaskEditorTarget?>(null) }
        // Threads created from this bar, named until the thread list includes them.
        val createdNames = remember { mutableStateMapOf<ThreadId, String>() }

        val permissions = snapshot.permissions
        val canManage = permissions.canManageThreads
        val canCreate = permissions.canCreateThreads
        val canRemind = rememberCanRemind(room, permissions.canSendMessages)
        val threads = chipThreads(snapshot, selection, createdNames)
        val request by controller.requests.collectAsState()

        val canAddTask = permissions.canAddTasks
        LaunchedEffect(request, isEnabled) {
            val current = request ?: return@LaunchedEffect
            if (!isEnabled) return@LaunchedEffect
            when (current) {
                ChatBarRequest.NewThread -> if (canCreate) sheet = ChatBarSheet.ThreadName(threadId = null, name = "", error = null, isSending = false)
                // Without a board, the board's setup explains how to start one; without the right to add, the board says why.
                ChatBarRequest.NewTask -> if (snapshot.board != null && canAddTask) {
                    editorTarget = TaskEditorTarget.New(TaskStatus.Todo)
                } else {
                    currentActions.openTasks()
                }
            }
            controller.consume(current)
        }
        val editor = editorTarget?.let { target ->
            key(target) {
                editorPresenter.present(
                    target = target,
                    onClose = { editorTarget = null },
                    onRemind = { reminderTarget = it },
                    onOpenThread = {
                        editorTarget = null
                        currentActions.openThread(it)
                    },
                )
            }
        }

        fun submitName(current: ChatBarSheet.ThreadName) {
            if (current.isSending) return
            val name = current.name.trim()
            val error = validate(name, submitting = true)
            if (error != null) {
                sheet = current.copy(error = error)
                return
            }
            sheet = current.copy(error = null, isSending = true)
            localScope.launch {
                val threadId = current.threadId
                if (threadId == null) {
                    when (val result = roomCoroutineScope.async { createThread(name) }.await()) {
                        is NamedThreadResult.Created -> {
                            createdNames[result.threadId] = name
                            sheet = null
                            currentActions.openThread(result.threadId)
                        }
                        // The root exists, so the thread opens; the snackbar offers to finish setting it up.
                        is NamedThreadResult.RootOnly -> {
                            createdNames[result.threadId] = name
                            sheet = null
                            currentActions.openThread(result.threadId)
                        }
                        NamedThreadResult.Failed -> sheet = current.copy(error = ThreadNameError.CreateFailed, isSending = false)
                    }
                } else if (name == threads.firstOrNull { it.id == threadId }?.name) {
                    sheet = null
                } else {
                    val renamed = roomCoroutineScope.async { rename(threadId, name) }.await()
                    sheet = if (renamed) null else current.copy(error = ThreadNameError.RenameFailed, isSending = false)
                }
            }
        }

        fun handleEvent(event: ChatBarEvent) {
            when (event) {
                is ChatBarEvent.Select -> if (event.target != currentSelection) currentActions.open(event.target)
                ChatBarEvent.OpenSpaceDocs -> currentActions.openSpaceDocs?.invoke()
                is ChatBarEvent.RemindMe -> {
                    sheet = null
                    if (canRemind) {
                        reminderTarget = MatchaReminderTarget(threadId = event.threadId ?: (currentSelection as? MatchaChatBarSelection.Thread)?.threadId)
                    }
                }
                ChatBarEvent.DismissReminder -> reminderTarget = null
                ChatBarEvent.NewThread -> if (canCreate) sheet = ChatBarSheet.ThreadName(threadId = null, name = "", error = null, isSending = false)
                is ChatBarEvent.ShowThreadActions -> threads.firstOrNull { it.id == event.threadId }?.let { sheet = ChatBarSheet.ThreadActions(it) }
                is ChatBarEvent.RenameThread -> {
                    val thread = threads.firstOrNull { it.id == event.threadId }
                    sheet = if (canManage && thread != null) {
                        ChatBarSheet.ThreadName(threadId = thread.id, name = thread.name.orEmpty(), error = null, isSending = false)
                    } else {
                        null
                    }
                }
                is ChatBarEvent.UpdateThreadName -> (sheet as? ChatBarSheet.ThreadName)
                    ?.takeUnless { it.isSending }
                    ?.let { sheet = it.copy(name = event.name, error = validate(event.name.trim(), submitting = false)) }
                ChatBarEvent.SubmitThreadName -> (sheet as? ChatBarSheet.ThreadName)?.let(::submitName)
                is ChatBarEvent.SetArchived -> {
                    sheet = null
                    if (!canManage) return
                    localScope.launch {
                        val done = roomCoroutineScope.async { setArchived(event.threadId, event.archived, announce = true) }.await()
                        if (done && event.archived && currentSelection == MatchaChatBarSelection.Thread(event.threadId)) currentActions.openGeneral()
                    }
                }
                is ChatBarEvent.ViewTask -> {
                    sheet = null
                    currentActions.openThreadTask(event.threadId)
                }
                // A send keeps going after the sheet is dismissed; a failure brings the sheet back with its error.
                ChatBarEvent.DismissSheet -> sheet = null
            }
        }

        return ChatBarState(
            isVisible = isEnabled,
            // The board always draws the bar; elsewhere it waits for the data, and appears only once the chat uses the features.
            showBar = isEnabled && (selection == MatchaChatBarSelection.Tasks || snapshot.isLoaded && snapshot.hasChatBar()),
            selection = selection,
            threads = threads.toImmutableList(),
            canCreateThread = canCreate,
            canManageThreads = canManage,
            canRemind = canRemind,
            canOpenSpaceDocs = actions.openSpaceDocs != null,
            warning = snapshot.warning,
            sheet = sheet,
            editor = editor,
            reminderTarget = reminderTarget,
            eventSink = ::handleEvent,
        )
    }

    /** Creates the thread; a thread that could not be set up is reported here, since the bar may be gone by then. */
    private suspend fun createThread(name: String): NamedThreadResult = store.createNamedThread(name).also {
        if (it is NamedThreadResult.RootOnly) postSetupFailed(it.threadId, name)
    }

    private fun postSetupFailed(threadId: ThreadId, name: String) {
        snackbarDispatcher.post(
            SnackbarMessage(
                messageResId = R.string.screen_matcha_threads_setup_failed,
                duration = SnackbarDuration.Long,
                actionResId = CommonStrings.action_retry,
                action = {
                    roomCoroutineScope.launch {
                        store.retrySetup(threadId, name).onFailure { postSetupFailed(threadId, name) }
                    }
                },
            )
        )
    }

    /** Renames a thread for everyone; a failure is shown in the sheet. Returns whether it was sent. */
    private suspend fun rename(threadId: ThreadId, name: String): Boolean =
        store.send(MatchaConfig.EVENT_THREAD_METADATA, ThreadMetadataProtocol.patchContent(threadId.value, name = name))
            .onFailure { Timber.w(it, "Could not rename thread %s", threadId.value) }
            .isSuccess

    /**
     * Archives or restores a thread for everyone. With [announce], a snackbar offers to undo it; a failure always offers to
     * retry. Returns whether it was sent.
     */
    private suspend fun setArchived(threadId: ThreadId, archived: Boolean, announce: Boolean): Boolean {
        val result = store.send(MatchaConfig.EVENT_THREAD_METADATA, ThreadMetadataProtocol.patchContent(threadId.value, archived = archived))
        result.onFailure {
            Timber.w(it, "Could not update thread %s", threadId.value)
            snackbarDispatcher.post(
                SnackbarMessage(
                    messageResId = CommonStrings.common_matcha_write_failed,
                    duration = SnackbarDuration.Long,
                    actionResId = CommonStrings.action_retry,
                    action = { roomCoroutineScope.launch { setArchived(threadId, archived, announce) } },
                )
            )
        }
        if (result.isSuccess && announce) {
            snackbarDispatcher.post(
                SnackbarMessage(
                    messageResId = if (archived) R.string.screen_matcha_threads_archived_snackbar else R.string.screen_matcha_threads_unarchived_snackbar,
                    duration = SnackbarDuration.Long,
                    actionResId = CommonStrings.action_matcha_undo,
                    action = { roomCoroutineScope.launch { setArchived(threadId, !archived, announce = false) } },
                )
            )
        }
        return result.isSuccess
    }

    companion object {
        internal const val MAX_THREAD_CHIPS = 4
    }
}

private fun MatchaChatBarActions.open(target: MatchaChatBarSelection) = when (target) {
    MatchaChatBarSelection.General -> openGeneral()
    MatchaChatBarSelection.Tasks -> openTasks()
    is MatchaChatBarSelection.Thread -> openThread(target.threadId)
}

/** Empty names are only an error once the user tries to save; a name that is too long is flagged while typing. */
private fun validate(trimmedName: String, submitting: Boolean): ThreadNameError? = when {
    trimmedName.length > MatchaConfig.THREAD_NAME_MAX_LENGTH -> ThreadNameError.TooLong
    submitting && trimmedName.isEmpty() -> ThreadNameError.Empty
    else -> null
}

private fun MatchaThread.toChip() = ChatBarThread(
    id = id,
    name = metadata.name.takeIf { metadata.nameSource != ThreadNameSource.EventId },
    isArchived = metadata.archived,
)

/** Thread chips appear once the metadata has loaded, so names never change under the user's finger. */
private fun chipThreads(snapshot: MatchaRoomSnapshot, selection: MatchaChatBarSelection, createdNames: Map<ThreadId, String>): List<ChatBarThread> {
    if (!snapshot.isLoaded) return emptyList()
    // The store's order: newest activity first.
    val active = snapshot.threads
        .filter { !it.metadata.archived }
        .take(ChatBarPresenter.MAX_THREAD_CHIPS)
        .map { it.toChip() }
    val selectedId = (selection as? MatchaChatBarSelection.Thread)?.threadId
    if (selectedId == null || active.any { it.id == selectedId }) return active
    val selected = snapshot.threads.firstOrNull { it.id == selectedId }?.toChip()
        ?: ChatBarThread(id = selectedId, name = createdNames[selectedId], isArchived = false)
    return active + selected
}
