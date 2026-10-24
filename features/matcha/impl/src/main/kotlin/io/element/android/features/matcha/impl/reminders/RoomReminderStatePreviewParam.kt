/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.reminders

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.appconfig.MatchaConfig
import io.element.android.libraries.matrix.api.core.UserId
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class RoomReminderStatePreviewParam : PreviewParameterProvider<RoomReminderState> {
    override val values: Sequence<RoomReminderState>
        get() = sequenceOf(
            aRoomReminderState(),
            aRoomReminderState(
                message = "Send the slides",
                link = null,
                quickOptions = aQuickOptions(deadline = PREVIEW_DEADLINE),
            ),
            aRoomReminderState(selected = RoomReminderOption.InHours(2), sending = RoomReminderOption.InHours(2)),
            aRoomReminderState(message = "", messageError = RoomReminderError.EmptyMessage, hasSendFailed = true),
            aRoomReminderState(message = "A".repeat(2_001), messageError = RoomReminderError.MessageTooLong),
            aRoomReminderState(step = RoomReminderStep.Custom),
            aRoomReminderState(
                step = RoomReminderStep.Custom,
                customDate = LocalDate.of(2026, 11, 10),
                customTime = LocalTime.of(17, 30),
                customLabel = "10 Nov, 17:30",
            ),
            aRoomReminderState(
                step = RoomReminderStep.Custom,
                customDate = PREVIEW_NOW.toLocalDate(),
                customTime = LocalTime.of(8, 0),
                customError = RoomReminderError.NotInFuture,
                customLabel = "08:00",
            ),
            aRoomReminderState(step = RoomReminderStep.Custom, customError = RoomReminderError.NonexistentTime, customLabel = null),
            aRoomReminderState(step = RoomReminderStep.Custom, sending = RoomReminderOption.Custom),
            aRoomReminderState(botState = RoomReminderBotState.Checking),
            aRoomReminderState(botState = RoomReminderBotState.Waiting(isChecking = false)),
            aRoomReminderState(botState = RoomReminderBotState.Waiting(isChecking = true)),
            aRoomReminderState(botState = RoomReminderBotState.CanInvite(isInviting = false, hasFailed = false)),
            aRoomReminderState(botState = RoomReminderBotState.CanInvite(isInviting = true, hasFailed = false)),
            aRoomReminderState(botState = RoomReminderBotState.CanInvite(isInviting = false, hasFailed = true)),
            aRoomReminderState(botState = RoomReminderBotState.CannotInvite),
            aRoomReminderState(botState = RoomReminderBotState.CannotSend),
            aRoomReminderState(message = "Call the venue", link = RoomReminderLink.Thread),
            aRoomReminderState(selected = RoomReminderOption.InHours(8)),
            aRoomReminderState(botState = RoomReminderBotState.UnavailableInDm),
        )
}

private val PREVIEW_NOW: ZonedDateTime = ZonedDateTime.of(2026, 11, 2, 12, 5, 0, 0, ZoneId.of("Asia/Jakarta"))
private val PREVIEW_DEADLINE: ZonedDateTime = PREVIEW_NOW.plusDays(1).withHour(17).withMinute(0)

private fun aQuickOptions(deadline: ZonedDateTime? = null) = buildList {
    RoomReminderPresenter.QUICK_OPTION_HOURS.forEach { add(RoomReminderOption.InHours(it)) }
    deadline?.let { add(RoomReminderOption.AtDeadline(it.toInstant().toEpochMilli())) }
}

internal fun aRoomReminderState(
    message: String = "Can someone book the room for Thursday?",
    link: RoomReminderLink? = RoomReminderLink.Message,
    messageError: RoomReminderError? = null,
    botState: RoomReminderBotState = RoomReminderBotState.Joined,
    step: RoomReminderStep = RoomReminderStep.Options,
    quickOptions: List<RoomReminderOption> = aQuickOptions(),
    selected: RoomReminderOption? = null,
    sending: RoomReminderOption? = null,
    hasSendFailed: Boolean = false,
    customDate: LocalDate = PREVIEW_NOW.toLocalDate().plusDays(1),
    customTime: LocalTime = RoomReminderPresenter.DEFAULT_CUSTOM_TIME,
    customError: RoomReminderError? = null,
    /** How DeadlineFormatter writes the custom date and time; null when that time doesn't exist. */
    customLabel: String? = "Tomorrow 09:00",
    eventSink: (RoomReminderEvent) -> Unit = {},
) = RoomReminderState(
    botUserId = UserId("@matcha-reminder:matrix.org"),
    message = message,
    link = link,
    maxMessageLength = MatchaConfig.REMINDER_MESSAGE_MAX_LENGTH - if (link != null) 60 else 0,
    messageError = messageError,
    botState = botState,
    step = step,
    now = PREVIEW_NOW,
    quickOptions = quickOptions.toImmutableList(),
    optionLabels = buildMap {
        put(RoomReminderOption.InHours(2), "Today 14:05")
        put(RoomReminderOption.InHours(8), "Today 20:05")
        put(RoomReminderOption.InHours(24), "Tomorrow 12:05")
        put(RoomReminderOption.AtDeadline(PREVIEW_DEADLINE.toInstant().toEpochMilli()), "Tomorrow 17:00")
        customLabel?.let { put(RoomReminderOption.Custom, it) }
    }.toImmutableMap(),
    selected = selected,
    sending = sending,
    hasSendFailed = hasSendFailed,
    customDate = customDate,
    customTime = customTime,
    customError = customError,
    isRequested = false,
    eventSink = eventSink,
)
