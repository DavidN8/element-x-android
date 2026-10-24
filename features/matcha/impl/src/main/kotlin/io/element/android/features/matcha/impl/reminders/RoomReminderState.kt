/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.reminders

import androidx.compose.runtime.Immutable
import io.element.android.libraries.matrix.api.core.UserId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

data class RoomReminderState(
    /** The reminder bot of the user's homeserver. */
    val botUserId: UserId,
    val message: String,
    /** What the link appended to the reminder points at, or null when no link is added. */
    val link: RoomReminderLink?,
    /** The longest message that still fits once the link is appended. */
    val maxMessageLength: Int,
    val messageError: RoomReminderError?,
    val botState: RoomReminderBotState,
    val step: RoomReminderStep,
    /** When the sheet last read the clock; the quick options are shown relative to it. */
    val now: ZonedDateTime,
    val quickOptions: ImmutableList<RoomReminderOption>,
    /** When each quick option and the custom date and time remind, like "Today 14:05"; none for a custom time that doesn't exist. */
    val optionLabels: ImmutableMap<RoomReminderOption, String>,
    /** The quick option the confirm button sends; nothing is chosen at first, so a single tap never posts. */
    val selected: RoomReminderOption?,
    /** The option being sent, if any; the other options are disabled meanwhile. */
    val sending: RoomReminderOption?,
    val hasSendFailed: Boolean,
    val customDate: LocalDate,
    val customTime: LocalTime,
    /** Why the picked date and time cannot be requested, shown above the confirm button. */
    val customError: RoomReminderError?,
    /** The reminder was sent; the sheet closes. */
    val isRequested: Boolean,
    val eventSink: (RoomReminderEvent) -> Unit,
)

enum class RoomReminderLink {
    Message,
    Thread,
}

enum class RoomReminderStep {
    Options,
    Custom,
}

enum class RoomReminderError {
    EmptyMessage,
    MessageTooLong,
    NotInFuture,
    TooFar,
    NonexistentTime,
}

@Immutable
sealed interface RoomReminderOption {
    /** Sent with the bot's relative mode, `!remind <seconds>s`. */
    data class InHours(val hours: Int) : RoomReminderOption

    /** A task's deadline, sent with the bot's absolute mode. */
    data class AtDeadline(val at: Long) : RoomReminderOption

    /** The date and time picked on the custom step, sent with the bot's absolute mode. */
    data object Custom : RoomReminderOption
}

/** Whether the reminder bot can post in this chat. */
@Immutable
sealed interface RoomReminderBotState {
    data object Checking : RoomReminderBotState

    data object Joined : RoomReminderBotState

    /** The bot is invited and has not joined yet; nothing can be sent until it has. */
    data class Waiting(val isChecking: Boolean) : RoomReminderBotState

    /** The bot is not in the chat and the user may invite it, after confirming. */
    data class CanInvite(val isInviting: Boolean, val hasFailed: Boolean) : RoomReminderBotState

    /** The bot is not in the chat and the user may not invite it. */
    data object CannotInvite : RoomReminderBotState

    /** The user cannot post in this chat, so the bot command cannot be sent. */
    data object CannotSend : RoomReminderBotState

    /** A DM without the bot: adding it would make the DM a group, so reminders are off. */
    data object UnavailableInDm : RoomReminderBotState
}
