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

    /** Requests the reminder: a quick option sends straight away, [RoomReminderOption.Custom] sends the picked date and time. */
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
