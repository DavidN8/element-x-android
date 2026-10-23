/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.reminders

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.appconfig.MatchaConfig
import io.element.android.features.matcha.api.MatchaReminderTarget
import io.element.android.features.matcha.impl.R
import io.element.android.features.matcha.impl.protocol.CardRevision
import io.element.android.features.matcha.impl.protocol.KanbanProtocol
import io.element.android.features.matcha.impl.protocol.ReminderProtocol
import io.element.android.features.matcha.impl.protocol.ReminderRequestRef
import io.element.android.features.matcha.impl.protocol.ReminderTime
import io.element.android.features.matcha.impl.protocol.ReminderUnit
import io.element.android.features.matcha.impl.store.MatchaRoomStore
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarMessage
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.core.asEventId
import io.element.android.libraries.matrix.api.room.CurrentUserMembership
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.room.MessageEventType
import io.element.android.libraries.matrix.api.room.RoomMembershipState
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * The "Remind me" sheet: checks that the reminder bot is in the chat (inviting it after confirmation), then posts a `!remind`
 * command that the bot schedules. Only the bot's own reply confirms a reminder, so success here only means "requested".
 */
@AssistedInject
class RoomReminderPresenter(
    @Assisted private val target: MatchaReminderTarget,
    private val room: JoinedRoom,
    private val store: MatchaRoomStore,
    private val clock: ReminderClock,
    private val snackbarDispatcher: SnackbarDispatcher,
) : Presenter<RoomReminderState> {
    @AssistedFactory
    fun interface Factory {
        fun create(target: MatchaReminderTarget): RoomReminderPresenter
    }

    private val botUserId = UserId(MatchaConfig.REMINDER_BOT_USER_ID)

    @Composable
    override fun present(): RoomReminderState {
        val coroutineScope = rememberCoroutineScope()
        var message by remember { mutableStateOf(target.message.take(PREFILL_MAX_LENGTH)) }
        var linkUrl by remember { mutableStateOf<String?>(null) }
        var botState by remember { mutableStateOf<RoomReminderBotState>(RoomReminderBotState.Checking) }
        var step by remember { mutableStateOf(RoomReminderStep.Options) }
        var now by remember { mutableStateOf(clock.now()) }
        var sending by remember { mutableStateOf<RoomReminderOption?>(null) }
        var showEmptyMessageError by remember { mutableStateOf(false) }
        var hasSendFailed by remember { mutableStateOf(false) }
        var customDate by remember { mutableStateOf(now.toLocalDate().plusDays(1)) }
        var customTime by remember { mutableStateOf(DEFAULT_CUSTOM_TIME) }
        var isRequested by remember { mutableStateOf(false) }

        LaunchedEffect(Unit) {
            linkUrl = resolveLink()
            botState = checkBot()
        }
        val isWaiting = botState is RoomReminderBotState.Waiting
        LaunchedEffect(isWaiting) {
            if (!isWaiting) return@LaunchedEffect
            // Memberships arrive with syncs: look again whenever the member count changes, so the options appear once the bot joins.
            room.roomInfoFlow.map { it.joinedMembersCount }.distinctUntilChanged().drop(1).collect { botState = checkBot() }
        }

        val maxMessageLength = MatchaConfig.REMINDER_MESSAGE_MAX_LENGTH - (linkUrl?.let { it.length + 1 } ?: 0)
        val nowMillis = now.toInstant().toEpochMilli()
        val quickOptions = remember(nowMillis) {
            buildList {
                QUICK_OPTION_HOURS.forEach { add(RoomReminderOption.InHours(it)) }
                target.dueAt?.takeIf { isWithinBounds(it, nowMillis) }?.let { add(RoomReminderOption.AtDeadline(it)) }
            }.toImmutableList()
        }
        val messageError = when {
            message.trim().length > maxMessageLength -> RoomReminderError.MessageTooLong
            showEmptyMessageError && message.isBlank() -> RoomReminderError.EmptyMessage
            else -> null
        }
        val customError = validateReminder(localInstant(customDate, customTime, now.zone), message, maxMessageLength, nowMillis)

        fun send(option: RoomReminderOption) = coroutineScope.launch {
            if (sending != null || botState != RoomReminderBotState.Joined) return@launch
            val current = clock.now().also { now = it }
            val currentMillis = current.toInstant().toEpochMilli()
            val time = when (option) {
                is RoomReminderOption.InHours -> ReminderTime.In(option.hours.toLong(), ReminderUnit.Hours)
                is RoomReminderOption.AtDeadline -> ReminderTime.At(option.at)
                RoomReminderOption.Custom -> localInstant(customDate, customTime, current.zone)?.let(ReminderTime::At)
            }
            val error = validateReminder(time?.let { ReminderProtocol.dueAt(it, currentMillis) }, message, maxMessageLength, currentMillis)
            if (time == null || error != null) {
                // The custom step shows every error above its button; the quick options only fail on the message.
                showEmptyMessageError = error == RoomReminderError.EmptyMessage
                return@launch
            }
            sending = option
            hasSendFailed = false
            // The bot may have left, or the user lost the right to post, while the sheet was open.
            val bot = checkBot()
            if (bot != RoomReminderBotState.Joined) {
                botState = bot
                sending = null
                return@launch
            }
            val text = ReminderProtocol.withLink(message.trim(), linkUrl)
            val sentAt = clock.now().toInstant().toEpochMilli()
            if (!ReminderProtocol.isValid(time, text, sentAt)) {
                // The picked time passed while checking; the refreshed clock makes the step show why.
                now = clock.now()
                sending = null
                return@launch
            }
            store.send(EVENT_ROOM_MESSAGE, textMessageContent(ReminderProtocol.command(time, text)))
                .onSuccess { eventId ->
                    target.cardId?.let { recordRequest(it, eventId, ReminderProtocol.dueAt(time, sentAt)) }
                    snackbarDispatcher.post(SnackbarMessage(R.string.screen_matcha_reminder_requested))
                    isRequested = true
                }
                .onFailure {
                    Timber.w(it, "Could not request a reminder")
                    sending = null
                    hasSendFailed = true
                }
        }

        fun inviteBot() = coroutineScope.launch {
            val current = botState as? RoomReminderBotState.CanInvite ?: return@launch
            if (current.isInviting) return@launch
            botState = current.copy(isInviting = true, hasFailed = false)
            botState = room.inviteUserById(botUserId).fold(
                onSuccess = { RoomReminderBotState.Waiting(isChecking = false) },
                onFailure = {
                    Timber.w(it, "Could not invite the reminder bot")
                    current.copy(isInviting = false, hasFailed = true)
                },
            )
        }

        fun checkBotAgain() = coroutineScope.launch {
            val current = botState as? RoomReminderBotState.Waiting ?: return@launch
            if (current.isChecking) return@launch
            botState = current.copy(isChecking = true)
            botState = checkBot()
        }

        fun handleEvent(event: RoomReminderEvent) {
            when (event) {
                is RoomReminderEvent.UpdateMessage -> {
                    message = event.message
                    showEmptyMessageError = false
                }
                is RoomReminderEvent.Send -> send(event.option)
                RoomReminderEvent.OpenCustom -> {
                    now = clock.now()
                    step = RoomReminderStep.Custom
                }
                RoomReminderEvent.CloseCustom -> step = RoomReminderStep.Options
                is RoomReminderEvent.SetDate -> customDate = event.date
                is RoomReminderEvent.SetTime -> customTime = event.time
                RoomReminderEvent.InviteBot -> inviteBot()
                RoomReminderEvent.CheckBotAgain -> checkBotAgain()
            }
        }

        return RoomReminderState(
            message = message,
            link = when {
                linkUrl == null -> null
                target.eventId != null -> RoomReminderLink.Message
                else -> RoomReminderLink.Thread
            },
            maxMessageLength = maxMessageLength,
            messageError = messageError,
            botState = botState,
            step = step,
            now = now,
            quickOptions = quickOptions,
            sending = sending,
            hasSendFailed = hasSendFailed,
            customDate = customDate,
            customTime = customTime,
            customError = customError,
            isRequested = isRequested,
            eventSink = ::handleEvent,
        )
    }

    private suspend fun resolveLink(): String? {
        val eventId = target.eventId ?: target.threadId?.asEventId() ?: return null
        return room.getPermalinkFor(eventId)
            .onFailure { Timber.w(it, "Could not build the link for a reminder") }
            .getOrNull()
    }

    private suspend fun checkBot(): RoomReminderBotState {
        val permissions = room.roomPermissions()
            .onFailure { Timber.w(it, "Could not read the room permissions") }
            .getOrNull()
            ?.use { it.canOwnUserSendMessage(MessageEventType.RoomMessage) to it.canOwnUserInvite() }
        val (canSend, canInvite) = permissions ?: Pair(false, false)
        if (room.info().currentUserMembership != CurrentUserMembership.JOINED || !canSend) return RoomReminderBotState.CannotSend
        return when (room.getUpdatedMember(botUserId).getOrNull()?.membership) {
            RoomMembershipState.JOIN -> RoomReminderBotState.Joined
            RoomMembershipState.INVITE -> RoomReminderBotState.Waiting(isChecking = false)
            // An invite would fail until someone lifts the ban.
            RoomMembershipState.BAN -> RoomReminderBotState.CannotInvite
            else -> if (canInvite) {
                RoomReminderBotState.CanInvite(isDm = room.isDm(), isInviting = false, hasFailed = false)
            } else {
                RoomReminderBotState.CannotInvite
            }
        }
    }

    /** Records the request on the task, as a complete revision of the card as the board shows it, like the web client does. */
    private suspend fun recordRequest(cardId: String, eventId: EventId, at: Long) {
        val snapshot = store.snapshot.value
        val board = snapshot.board
        val card = board?.cards?.firstOrNull { it.id == cardId }
        if (board == null || card == null || !snapshot.permissions.canWriteCards) {
            Timber.w("Reminder requested, but card %s could not be updated", cardId)
            return
        }
        val revision = CardRevision(
            cardId = card.id,
            title = card.title,
            description = card.description,
            status = card.status,
            archived = card.archived,
            threadId = card.threadId,
            dueAt = card.dueAt,
            reminderRequested = ReminderRequestRef(eventId = eventId.value, at = at),
            showToRoom = card.showToRoom,
        )
        runCatchingExceptions { KanbanProtocol.cardContent(revision, board.rootId) }
            .onFailure { Timber.w(it, "Card %s cannot be written", cardId) }
            .getOrNull()
            ?.let { store.send(MatchaConfig.EVENT_KANBAN_CARD, it) }
            ?.onFailure { Timber.w(it, "Reminder requested, but card %s could not be updated", cardId) }
    }

    companion object {
        const val EVENT_ROOM_MESSAGE = "m.room.message"
        val QUICK_OPTION_HOURS = listOf(2, 8, 24)
        val DEFAULT_CUSTOM_TIME: LocalTime = LocalTime.of(9, 0)

        /** A message reminder is prefilled with the start of the message. */
        const val PREFILL_MAX_LENGTH = 200
    }
}

private const val MAX_DELAY_MILLIS = MatchaConfig.REMINDER_MAX_DELAY_DAYS * 86_400_000L

private fun isWithinBounds(dueAt: Long, nowMillis: Long) = dueAt > nowMillis && dueAt - nowMillis <= MAX_DELAY_MILLIS

private fun textMessageContent(body: String): String = buildJsonObject {
    put("msgtype", "m.text")
    put("body", body)
}.toString()

/** The first reason a reminder cannot be requested, checking the same bounds as the bot, or null when it can. */
internal fun validateReminder(dueAt: Long?, message: String, maxMessageLength: Int, nowMillis: Long): RoomReminderError? = when {
    dueAt == null -> RoomReminderError.NonexistentTime
    dueAt <= nowMillis -> RoomReminderError.NotInFuture
    !isWithinBounds(dueAt, nowMillis) -> RoomReminderError.TooFar
    message.isBlank() -> RoomReminderError.EmptyMessage
    message.trim().length > maxMessageLength -> RoomReminderError.MessageTooLong
    else -> null
}

/** The instant of a wall-clock date and time, or null when a daylight saving change skips that time in [zone]. */
internal fun localInstant(date: LocalDate, time: LocalTime, zone: ZoneId): Long? {
    val local = LocalDateTime.of(date, time)
    if (zone.rules.getValidOffsets(local).isEmpty()) return null
    return local.atZone(zone).toInstant().toEpochMilli()
}
