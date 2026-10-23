/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.reminders

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.appconfig.MatchaConfig
import kotlinx.collections.immutable.toImmutableList
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
                quickOptions = aQuickOptions(deadline = PREVIEW_NOW.plusDays(1).withHour(17).withMinute(0)),
            ),
            aRoomReminderState(selected = RoomReminderOption.InHours(2), sending = RoomReminderOption.InHours(2)),
            aRoomReminderState(message = "", messageError = RoomReminderError.EmptyMessage, hasSendFailed = true),
            aRoomReminderState(message = "A".repeat(2_001), messageError = RoomReminderError.MessageTooLong),
            aRoomReminderState(step = RoomReminderStep.Custom),
            aRoomReminderState(step = RoomReminderStep.Custom, customDate = LocalDate.of(2026, 11, 10), customTime = LocalTime.of(17, 30)),
            aRoomReminderState(
                step = RoomReminderStep.Custom,
                customDate = PREVIEW_NOW.toLocalDate(),
                customTime = LocalTime.of(8, 0),
                customError = RoomReminderError.NotInFuture
            ),
            aRoomReminderState(step = RoomReminderStep.Custom, customError = RoomReminderError.NonexistentTime),
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
    eventSink: (RoomReminderEvent) -> Unit = {},
) = RoomReminderState(
    message = message,
    link = link,
    maxMessageLength = MatchaConfig.REMINDER_MESSAGE_MAX_LENGTH - if (link != null) 60 else 0,
    messageError = messageError,
    botState = botState,
    step = step,
    now = PREVIEW_NOW,
    quickOptions = quickOptions.toImmutableList(),
    selected = selected,
    sending = sending,
    hasSendFailed = hasSendFailed,
    customDate = customDate,
    customTime = customTime,
    customError = customError,
    isRequested = false,
    eventSink = eventSink,
)
