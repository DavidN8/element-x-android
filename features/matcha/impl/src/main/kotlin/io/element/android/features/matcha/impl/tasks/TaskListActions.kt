/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.tasks

import androidx.compose.material3.SnackbarDuration
import io.element.android.features.matcha.impl.R
import io.element.android.features.matcha.impl.protocol.TaskStatus
import io.element.android.features.matcha.impl.store.MatchaRoomStore
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarMessage
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.minutes

/**
 * Moves, archives and restores the tasks of one chat from a list of tasks, for the board and the Tasks tab: archiving offers Undo
 * and a failed write offers Retry. Writes run in [scope], so they finish when the list is gone; a write's job lasts until its
 * snackbar is gone, so whoever owns [scope] can keep it while Undo or Retry may still use it.
 */
internal class TaskListActions(
    private val store: MatchaRoomStore,
    private val snackbarDispatcher: SnackbarDispatcher,
    private val scope: CoroutineScope,
) {
    private val writer = TaskWriter(store)

    fun move(taskId: String, status: TaskStatus) {
        scope.launch {
            val board = store.snapshot.value.board ?: return@launch
            board.cards.firstOrNull { it.id == taskId }?.takeIf { it.status != status } ?: return@launch
            writer.update(board.rootId, taskId) { it.copy(status = status) }
                ?.let { postWriteError(it) { move(taskId, status) } }
        }
    }

    /** Archives or restores a task and, for a thread's task, the thread too. */
    fun setArchived(taskId: String, archived: Boolean, announce: Boolean = true) {
        scope.launch {
            val board = store.snapshot.value.board ?: return@launch
            val card = board.cards.firstOrNull { it.id == taskId }?.takeIf { it.archived != archived } ?: return@launch
            val error = writer.update(board.rootId, taskId, archiveThread = card.threadId != null) { it.copy(archived = archived) }
            when {
                error != null -> postWriteError(error) { setArchived(taskId, archived, announce) }
                announce -> postAndHold(
                    SnackbarMessage(
                        messageResId = if (archived) R.string.screen_matcha_tasks_archived_snackbar else R.string.screen_matcha_tasks_unarchived_snackbar,
                        duration = SnackbarDuration.Long,
                        actionResId = CommonStrings.action_matcha_undo,
                        action = { setArchived(taskId, !archived, announce = false) },
                    )
                )
            }
        }
    }

    private suspend fun postWriteError(error: TaskWriteError, retry: () -> Unit) {
        postAndHold(
            SnackbarMessage(
                messageResId = error.messageResId(),
                duration = SnackbarDuration.Long,
                actionResId = CommonStrings.action_retry,
                action = retry,
            )
        )
    }

    /** Posts [message] and waits until it has been shown and dismissed, or for [SNACKBAR_HOLD] when no screen shows it. */
    private suspend fun postAndHold(message: SnackbarMessage) {
        snackbarDispatcher.post(message)
        withTimeoutOrNull(SNACKBAR_HOLD) {
            snackbarDispatcher.snackbarMessage.first { it?.id == message.id }
            snackbarDispatcher.snackbarMessage.first { it?.id != message.id }
        }
    }

    private companion object {
        val SNACKBAR_HOLD = 1.minutes
    }
}
