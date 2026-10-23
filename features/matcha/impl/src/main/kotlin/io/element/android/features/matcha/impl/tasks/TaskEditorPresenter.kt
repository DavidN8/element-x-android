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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.appconfig.MatchaConfig
import io.element.android.features.matcha.api.MatchaReminderTarget
import io.element.android.features.matcha.impl.chatbar.rememberCanRemind
import io.element.android.features.matcha.impl.protocol.CardRevision
import io.element.android.features.matcha.impl.protocol.KanbanProtocol
import io.element.android.features.matcha.impl.protocol.TaskStatus
import io.element.android.features.matcha.impl.store.MatchaRoomStore
import io.element.android.features.matcha.impl.store.NamedThreadResult
import io.element.android.features.matcha.impl.store.createNamedThread
import io.element.android.features.matcha.impl.store.retrySetup
import io.element.android.libraries.matrix.api.core.ThreadId
import io.element.android.libraries.matrix.api.room.JoinedRoom
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.time.ZoneId
import kotlin.time.Clock

/**
 * The task editor sheet, for a new task or an existing one, in one room. Hosted by the board, the chip bar and the Tasks tab,
 * which each pass the room's store; writes run in [writeScope] so they finish when the sheet is gone.
 */
@AssistedInject
class TaskEditorPresenter(
    @Assisted private val room: JoinedRoom,
    @Assisted private val store: MatchaRoomStore,
    @Assisted private val writeScope: CoroutineScope,
    private val deadlineFormatter: DeadlineFormatter,
    private val clock: Clock,
) {
    @AssistedFactory
    fun interface Factory {
        fun create(room: JoinedRoom, store: MatchaRoomStore, writeScope: CoroutineScope): TaskEditorPresenter
    }

    private val writer = TaskWriter(store)

    /**
     * @param target the task to edit, or a new task.
     * @param onClose the sheet is done: saved, discarded, or the task is gone.
     * @param onRemind shows the reminder sheet for the task, or null where reminders can't be shown (the Tasks tab).
     * @param onOpenThread shows the task's thread; the sheet is closed first.
     */
    @Composable
    fun present(
        target: TaskEditorTarget,
        onClose: () -> Unit,
        onRemind: ((MatchaReminderTarget) -> Unit)?,
        onOpenThread: (ThreadId) -> Unit,
    ): TaskEditorState {
        val snapshot by store.snapshot.collectAsState()
        val currentOnClose by rememberUpdatedState(onClose)
        val localScope = rememberCoroutineScope()
        // The card as it was when the editor opened; the fields start from it and changes are measured against it.
        val initial = remember { (target as? TaskEditorTarget.Edit)?.let { edit -> snapshot.board?.cards?.firstOrNull { it.id == edit.taskId } } }
        if (target is TaskEditorTarget.Edit && initial == null) {
            LaunchedEffect(Unit) { currentOnClose() }
        }
        // Without a shared name or a readable root, the name falls back to the thread id: the user is asked for a name instead.
        val initialTitle = initial?.title?.takeUnless { it == initial.threadId }.orEmpty()
        var title by rememberSaveable { mutableStateOf(initialTitle) }
        var description by rememberSaveable { mutableStateOf(initial?.description.orEmpty()) }
        var status by rememberSaveable { mutableStateOf(initial?.status ?: (target as? TaskEditorTarget.New)?.status ?: TaskStatus.Todo) }
        var dueAt by rememberSaveable { mutableStateOf(initial?.dueAt) }
        var deadlineError by remember { mutableStateOf<TaskDeadlineError?>(null) }
        var showErrors by remember { mutableStateOf(false) }
        var saveError by remember { mutableStateOf<TaskWriteError?>(null) }
        var isSaving by remember { mutableStateOf(false) }
        var exit by remember { mutableStateOf<Exit?>(null) }
        // The board the user saw; a save refused because it changed adopts the new one, so Save again writes there.
        var boardRootId by remember { mutableStateOf(snapshot.board?.rootId) }
        // Set once a new task's thread exists, so a retry writes the card instead of creating another thread.
        var createdThreadId by rememberSaveable { mutableStateOf<String?>(null) }
        var createdThreadName by rememberSaveable { mutableStateOf<String?>(null) }
        // False while the new thread's root was sent without the reply that makes it a thread; its card waits until it is.
        var isThreadSetUp by rememberSaveable { mutableStateOf(false) }

        val permissions = snapshot.permissions
        val threadId = initial?.threadId
        val isLinked = target is TaskEditorTarget.New || threadId != null
        val titleMaxLength = if (isLinked) MatchaConfig.THREAD_NAME_MAX_LENGTH else MatchaConfig.CARD_TITLE_MAX_LENGTH
        val canRename = threadId == null || permissions.canManageThreads
        val canRemind = rememberCanRemind(room, permissions.canSendMessages) && onRemind != null && initial != null && !initial.archived
        val hasChanges = title != initialTitle || description != initial?.description.orEmpty() ||
            dueAt != initial?.dueAt || initial != null && status != initial.status
        val titleError = titleError(title.trim(), titleMaxLength, includeEmpty = showErrors).takeIf { canRename }
        val descriptionError = TaskFieldError.TooLong(MatchaConfig.CARD_DESCRIPTION_MAX_LENGTH)
            .takeIf { description.length > MatchaConfig.CARD_DESCRIPTION_MAX_LENGTH }
        val nowMillis = clock.now().toEpochMilliseconds()
        val currentDueAt = dueAt

        suspend fun save(): TaskWriteError? {
            val root = boardRootId ?: return TaskWriteError.Failed
            val trimmed = title.trim()
            val error = if (initial == null) {
                val thread = createdThreadId ?: when (val result = store.createNamedThread(trimmed)) {
                    is NamedThreadResult.Created -> result.threadId.value.also { isThreadSetUp = true }
                    is NamedThreadResult.RootOnly -> {
                        createdThreadId = result.threadId.value
                        createdThreadName = trimmed
                        return TaskWriteError.ThreadSetupFailed
                    }
                    NamedThreadResult.Failed -> return TaskWriteError.Failed
                }
                if (createdThreadId == null) {
                    createdThreadId = thread
                    createdThreadName = trimmed
                }
                // A card linked to a root that isn't a thread breaks the board on the web client, so it waits for the thread.
                if (!isThreadSetUp) {
                    store.retrySetup(ThreadId(thread), trimmed).onFailure { return TaskWriteError.ThreadSetupFailed }
                    isThreadSetUp = true
                    createdThreadName = trimmed
                }
                val rename = createdThreadName != trimmed
                val card = CardRevision(
                    cardId = KanbanProtocol.threadCardId(thread),
                    title = trimmed,
                    description = description,
                    status = status,
                    archived = false,
                    threadId = thread,
                    dueAt = dueAt,
                    reminderRequested = null,
                    showToRoom = true,
                )
                // A retry after the name was changed renames the thread created by the first attempt.
                writer.create(root, card, renameThread = rename)
            } else {
                // Read-modify-write: only the fields the user changed are written over the newest revision.
                writer.update(root, initial.id) { current ->
                    current.copy(
                        title = if (canRename && trimmed != initialTitle) trimmed else current.title,
                        description = if (description != initial.description) description else current.description,
                        status = if (status != initial.status) status else current.status,
                        dueAt = if (dueAt != initial.dueAt) dueAt else current.dueAt,
                    )
                }
            }
            if (error == TaskWriteError.BoardChanged) boardRootId = store.snapshot.value.board?.rootId
            return error
        }

        fun handleEvent(event: TaskEditorEvent) {
            when (event) {
                is TaskEditorEvent.SetTitle -> if (canRename && !isSaving) title = event.title
                is TaskEditorEvent.SetDescription -> if (!isSaving) description = event.description
                is TaskEditorEvent.SetStatus -> if (!isSaving) status = event.status
                is TaskEditorEvent.SetDeadline -> when (val deadline = event.dateTime.toDeadline(ZoneId.systemDefault())) {
                    is LocalDeadline.Valid -> {
                        dueAt = deadline.epochMillis
                        deadlineError = null
                    }
                    LocalDeadline.Skipped -> deadlineError = TaskDeadlineError.Skipped
                    LocalDeadline.OutOfRange -> deadlineError = TaskDeadlineError.OutOfRange
                }
                TaskEditorEvent.ClearDeadline -> {
                    dueAt = null
                    deadlineError = null
                }
                TaskEditorEvent.Save -> {
                    if (isSaving) return
                    showErrors = true
                    // A name the user can't change isn't written, so it can't stop the save.
                    val hasTitleError = canRename && titleError(title.trim(), titleMaxLength, includeEmpty = true) != null
                    if (hasTitleError || descriptionError != null) return
                    isSaving = true
                    saveError = null
                    localScope.launch {
                        val error = writeScope.async { save() }.await()
                        isSaving = false
                        saveError = error
                        if (error == null) currentOnClose()
                    }
                }
                TaskEditorEvent.RemindMe -> if (canRemind && initial != null) {
                    onRemind?.invoke(MatchaReminderTarget(message = title.trim(), threadId = threadId?.let(::ThreadId), dueAt = dueAt, cardId = initial.id))
                }
                TaskEditorEvent.OpenThread -> if (threadId != null) {
                    if (hasChanges) exit = Exit.ToThread else onOpenThread(ThreadId(threadId))
                }
                TaskEditorEvent.Close -> if (hasChanges) exit = Exit.Close else onClose()
                TaskEditorEvent.Discard -> {
                    val pending = exit
                    exit = null
                    onClose()
                    if (pending == Exit.ToThread && threadId != null) onOpenThread(ThreadId(threadId))
                }
                TaskEditorEvent.KeepEditing -> exit = null
            }
        }

        return TaskEditorState(
            isNew = initial == null,
            isLinked = isLinked,
            title = title,
            description = description,
            status = status,
            dueAt = dueAt,
            deadline = dueAt?.let(deadlineFormatter::format),
            canRename = canRename,
            canRemind = canRemind,
            reminderRequestedAt = initial?.let { card -> snapshot.board?.cards?.firstOrNull { it.id == card.id } }?.reminderRequested?.at?.let(
                deadlineFormatter::format
            ),
            earlierCards = initial?.earlierCards.orEmpty().map { it.title }.toImmutableList(),
            titleMaxLength = titleMaxLength,
            titleError = titleError,
            descriptionError = descriptionError,
            deadlineError = deadlineError ?: TaskDeadlineError.Past.takeIf { currentDueAt != null && currentDueAt < nowMillis },
            saveError = saveError,
            isSaving = isSaving,
            hasChanges = hasChanges,
            showDiscardDialog = exit != null,
            eventSink = ::handleEvent,
        )
    }

    private enum class Exit { Close, ToThread }
}

/** An empty name is only an error once the user tries to save; a name that is too long is flagged while typing. */
private fun titleError(trimmedTitle: String, maxLength: Int, includeEmpty: Boolean): TaskFieldError? = when {
    trimmedTitle.length > maxLength -> TaskFieldError.TooLong(maxLength)
    includeEmpty && trimmedTitle.isEmpty() -> TaskFieldError.Empty
    else -> null
}
