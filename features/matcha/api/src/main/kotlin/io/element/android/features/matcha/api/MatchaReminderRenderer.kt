/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.api

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import io.element.android.appconfig.MatchaConfig
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.ThreadId
import io.element.android.libraries.matrix.api.room.BaseRoom
import io.element.android.libraries.matrix.api.room.RoomMembershipState

/**
 * The "Remind me" sheet: the reminder bot posts the reminder in the chat. Room scoped.
 * Shown from a message's long-press menu, the chip bar, a thread chip and tasks. Renders nothing when [target] is null.
 */
interface MatchaReminderRenderer {
    @Composable
    fun Render(
        target: MatchaReminderTarget?,
        onDismiss: () -> Unit,
    )
}

/** What a reminder is about, used to prefill the sheet. */
@Immutable
data class MatchaReminderTarget(
    /** Prefilled message text. */
    val message: String = "",
    /** The message the reminder is about; a link to it is appended to the reminder once. */
    val eventId: EventId? = null,
    /** The thread the reminder is about; a link to it is appended to the reminder once. */
    val threadId: ThreadId? = null,
    /** A task's deadline, offered as "At the deadline". */
    val dueAt: Long? = null,
    /** Set when reminding about a task, so its card records the request. */
    val cardId: String? = null,
)

/** Whether the reminder bot, which posts the reminders, has joined this room; in a DM it must, since inviting it would make a group. */
suspend fun BaseRoom.isReminderBotJoined(): Boolean =
    getUpdatedMember(MatchaConfig.reminderBotUserId(sessionId)).getOrNull()?.membership == RoomMembershipState.JOIN
