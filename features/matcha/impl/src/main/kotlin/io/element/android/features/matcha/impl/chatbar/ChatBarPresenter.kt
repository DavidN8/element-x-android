/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.chatbar

import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.appconfig.MatchaConfig
import io.element.android.features.matcha.api.MatchaChatBarActions
import io.element.android.features.matcha.api.MatchaChatBarSelection
import io.element.android.features.matcha.api.MatchaReminderTarget
import io.element.android.features.matcha.impl.R
import io.element.android.features.matcha.impl.protocol.TaskStatus
import io.element.android.features.matcha.impl.protocol.ThreadMetadataProtocol
import io.element.android.features.matcha.impl.store.MatchaRoomSnapshot
import io.element.android.features.matcha.impl.store.MatchaRoomStore
import io.element.android.features.matcha.impl.store.MatchaThread
import io.element.android.features.matcha.impl.store.NamedThreadResult
import io.element.android.features.matcha.impl.store.createNamedThread
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarMessage
import io.element.android.libraries.di.annotations.RoomCoroutineScope
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.matrix.api.core.ThreadId
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * The chat chip bar: General, Tasks, the active named threads, New thread, All threads and More.
 *
 * Writes run in the room's scope, so they finish (and their snackbars still show) when the screen under the bar is
 * replaced, for example after archiving the open thread; navigation only happens while this bar is still shown.
 */
@Inject
class ChatBarPresenter(
    private val store: MatchaRoomStore,
    private val featureFlagService: FeatureFlagService,
    private val snackbarDispatcher: SnackbarDispatcher,
    @RoomCoroutineScope private val roomCoroutineScope: CoroutineScope,
) {
    @Composable
    fun present(selection: MatchaChatBarSelection, actions: MatchaChatBarActions): ChatBarState {
        val isEnabled by remember { featureFlagService.isFeatureEnabledFlow(FeatureFlags.MatchaWorkspaces) }.collectAsState(initial = false)
        val snapshot by store.snapshot.collectAsState()
        val currentSelection by rememberUpdatedState(selection)
        val currentActions by rememberUpdatedState(actions)
        val localScope = rememberCoroutineScope()
        var sheet by remember { mutableStateOf<ChatBarSheet?>(null) }
        var reminderTarget by remember { mutableStateOf<MatchaReminderTarget?>(null) }
        // Threads created from this bar, named until the thread list includes them.
        val createdNames = remember { mutableStateMapOf<ThreadId, String>() }

        val permissions = snapshot.permissions
        val canManage = permissions.canManageThreads
        val canCreate = canManage && permissions.canSendMessages
        val canRemind = permissions.canSendMessages
        val threads = chipThreads(snapshot, selection, createdNames)

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
                        NamedThreadResult.RootOnly -> sheet = null
                        NamedThreadResult.Failed -> sheet = current.copy(error = ThreadNameError.CreateFailed, isSending = false)
                    }
                } else if (name == threads.firstOrNull { it.id == threadId }?.name) {
                    sheet = null
                } else {
                    val renamed = roomCoroutineScope.async { patch(threadId, name = name) }.await()
                    sheet = if (renamed) null else current.copy(error = ThreadNameError.RenameFailed, isSending = false)
                }
            }
        }

        fun handleEvent(event: ChatBarEvent) {
            when (event) {
                is ChatBarEvent.Select -> if (event.target != currentSelection) currentActions.open(event.target)
                ChatBarEvent.OpenAllThreads -> currentActions.openAllThreads()
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
                        val done = roomCoroutineScope.async { patch(event.threadId, archived = event.archived, announce = true) }.await()
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

        val visibleIds = threads.mapTo(HashSet()) { it.id }
        return ChatBarState(
            isVisible = isEnabled,
            selection = selection,
            openTaskCount = if (snapshot.isLoaded) snapshot.board?.cards?.count { !it.archived && it.status != TaskStatus.Done } ?: 0 else 0,
            threads = threads.toImmutableList(),
            canCreateThread = canCreate,
            canManageThreads = canManage,
            canRemind = canRemind,
            canOpenSpaceDocs = actions.openSpaceDocs != null,
            showUnreadableNamesWarning = snapshot.threads.any { it.id in visibleIds && it.isRootUnreadable && !it.hasName() },
            sheet = sheet,
            reminderTarget = reminderTarget,
            eventSink = ::handleEvent,
        )
    }

    /** Creates the thread; a thread that could not be set up is reported here, since the bar may be gone by then. */
    private suspend fun createThread(name: String): NamedThreadResult = store.createNamedThread(name).also {
        if (it == NamedThreadResult.RootOnly) {
            snackbarDispatcher.post(SnackbarMessage(R.string.screen_matcha_threads_setup_failed, duration = SnackbarDuration.Long))
        }
    }

    /** Renames, archives or restores a thread for everyone; archiving offers Undo. Returns whether it was sent. */
    private suspend fun patch(threadId: ThreadId, name: String? = null, archived: Boolean? = null, announce: Boolean = false): Boolean {
        val result = store.send(MatchaConfig.EVENT_THREAD_METADATA, ThreadMetadataProtocol.patchContent(threadId.value, name = name, archived = archived))
        result.onFailure {
            Timber.w(it, "Could not update thread %s", threadId.value)
            if (archived != null) snackbarDispatcher.post(SnackbarMessage(R.string.screen_matcha_threads_update_failed))
        }
        if (result.isSuccess && announce && archived != null) {
            snackbarDispatcher.post(
                if (archived) {
                    SnackbarMessage(
                        messageResId = R.string.screen_matcha_threads_archived_snackbar,
                        duration = SnackbarDuration.Long,
                        actionResId = R.string.action_matcha_undo,
                        action = { roomCoroutineScope.launch { patch(threadId, archived = false) } },
                    )
                } else {
                    SnackbarMessage(R.string.screen_matcha_threads_unarchived_snackbar)
                }
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

/** The name fell back to the root id: neither a shared name nor a readable root message exists. */
private fun MatchaThread.hasName() = metadata.name != id.value

private fun MatchaThread.toChip() = ChatBarThread(id = id, name = metadata.name.takeIf { hasName() }, isArchived = metadata.archived)

/** Thread chips appear once the metadata has loaded, so names never change under the user's finger. */
private fun chipThreads(snapshot: MatchaRoomSnapshot, selection: MatchaChatBarSelection, createdNames: Map<ThreadId, String>): List<ChatBarThread> {
    if (!snapshot.isLoaded) return emptyList()
    val active = snapshot.threads
        .filter { !it.metadata.archived }
        .sortedByDescending { it.rootTimestamp }
        .take(ChatBarPresenter.MAX_THREAD_CHIPS)
        .map { it.toChip() }
    val selectedId = (selection as? MatchaChatBarSelection.Thread)?.threadId
    if (selectedId == null || active.any { it.id == selectedId }) return active
    val selected = snapshot.threads.firstOrNull { it.id == selectedId }?.toChip()
        ?: ChatBarThread(id = selectedId, name = createdNames[selectedId], isArchived = false)
    return active + selected
}
