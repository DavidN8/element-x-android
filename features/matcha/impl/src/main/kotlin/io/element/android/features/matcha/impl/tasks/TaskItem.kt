/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.tasks

import androidx.compose.runtime.Immutable
import io.element.android.features.matcha.impl.protocol.ProjectedCard
import io.element.android.features.matcha.impl.protocol.TaskStatus
import io.element.android.features.matcha.impl.store.MatchaRoomSnapshot
import io.element.android.libraries.matrix.api.core.ThreadId

/** A task as shown in a row or a card of the board, and in the Tasks tab. */
@Immutable
data class TaskItem(
    val id: String,
    /** The task name, or null when only the thread's event id is known; drawn as "Untitled thread". */
    val title: String?,
    /** The first line of the description, or empty. */
    val summary: String,
    val status: TaskStatus,
    val isArchived: Boolean,
    /** The task's thread, or null for an older standalone card. */
    val threadId: ThreadId?,
    val dueAt: Long?,
    /** The formatted deadline, or null without a deadline. */
    val deadline: String?,
    /** The deadline has passed and the task isn't done. */
    val isOverdue: Boolean,
    /** Replies in the task's thread, without the hidden reply that set it up; null for a standalone card. */
    val replyCount: Int?,
    /** A reminder was requested; only the bot's reply confirms it, so this never says "scheduled". */
    val hasReminder: Boolean,
)

/**
 * The board's tasks, active and archived, ordered by deadline (soonest first, none last), then newest thread first.
 */
internal fun MatchaRoomSnapshot.toTaskItems(deadlineFormatter: DeadlineFormatter, nowMillis: Long): List<TaskItem> {
    val cards = board?.cards ?: return emptyList()
    val threadsById = threads.associateBy { it.id.value }
    return cards
        .sortedWith(
            compareBy<ProjectedCard> { it.dueAt ?: Long.MAX_VALUE }
                .thenByDescending { card -> card.threadId?.let { threadsById[it]?.rootTimestamp } ?: 0L }
        )
        .map { card ->
            val threadId = card.threadId
            TaskItem(
                id = card.id,
                // Without a shared name or a readable root, the name falls back to the thread id.
                title = card.title.takeUnless { it == threadId },
                summary = card.description.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty(),
                status = card.status,
                isArchived = card.archived,
                threadId = threadId?.let(::ThreadId),
                dueAt = card.dueAt,
                deadline = card.dueAt?.let(deadlineFormatter::format),
                isOverdue = card.dueAt != null && card.dueAt < nowMillis && card.status != TaskStatus.Done,
                replyCount = threadId?.let { threadsById[it]?.visibleReplies() ?: 0 },
                hasReminder = card.reminderRequested != null,
            )
        }
}
