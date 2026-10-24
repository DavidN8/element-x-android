/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.reminders

import java.time.LocalDate
import java.time.LocalTime

sealed interface RoomReminderEvent {
    data class UpdateMessage(val message: String) : RoomReminderEvent

    /** Chooses a quick option for the confirm button; nothing is sent yet. */
    data class Select(val option: RoomReminderOption) : RoomReminderEvent

    /** Requests the reminder, from the confirm button: the chosen quick option, or [RoomReminderOption.Custom] for the picked date and time. */
    data class Send(val option: RoomReminderOption) : RoomReminderEvent

    data object OpenCustom : RoomReminderEvent
    data object CloseCustom : RoomReminderEvent
    data class SetDate(val date: LocalDate) : RoomReminderEvent
    data class SetTime(val time: LocalTime) : RoomReminderEvent

    /** Invites the reminder bot, after the user confirmed it. */
    data object InviteBot : RoomReminderEvent

    /** Checks again whether the reminder bot has joined. */
    data object CheckBotAgain : RoomReminderEvent
}
