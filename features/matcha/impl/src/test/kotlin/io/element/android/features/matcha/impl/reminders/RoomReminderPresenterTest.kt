/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.reminders

import app.cash.turbine.TurbineTestContext
import com.google.common.truth.Truth.assertThat
import io.element.android.appconfig.MatchaConfig
import io.element.android.features.matcha.api.MatchaReminderTarget
import io.element.android.features.matcha.impl.R
import io.element.android.features.matcha.impl.protocol.CardRevision
import io.element.android.features.matcha.impl.protocol.KanbanProtocol
import io.element.android.features.matcha.impl.protocol.RawEvent
import io.element.android.features.matcha.impl.protocol.ReminderRequestRef
import io.element.android.features.matcha.impl.protocol.TaskStatus
import io.element.android.features.matcha.impl.store.MatchaRoomStore
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.ThreadId
import io.element.android.libraries.matrix.api.core.UniqueId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.RoomMembershipState
import io.element.android.libraries.matrix.api.room.StateEventType
import io.element.android.libraries.matrix.api.room.threads.ThreadListPaginationStatus
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.api.timeline.item.event.TimelineItemDebugInfoProvider
import io.element.android.libraries.matrix.test.room.FakeBaseRoom
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.room.aRoomInfo
import io.element.android.libraries.matrix.test.room.aRoomMember
import io.element.android.libraries.matrix.test.room.powerlevels.FakeRoomPermissions
import io.element.android.libraries.matrix.test.room.threads.FakeThreadsListService
import io.element.android.libraries.matrix.test.timeline.FakeTimeline
import io.element.android.libraries.matrix.test.timeline.aTimelineItemDebugInfo
import io.element.android.libraries.matrix.test.timeline.anEventTimelineItem
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.awaitLastSequentialItem
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.test
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class RoomReminderPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    private val zone = ZoneId.of("Europe/Amsterdam")

    /** Saturday; clocks go forward at 02:00 on Sunday 29 March 2026 in Amsterdam. */
    private val now = ZonedDateTime.of(2026, 3, 28, 12, 0, 0, 0, zone)
    private val nowMillis = now.toInstant().toEpochMilli()
    private val messageId = EventId("\$message")
    private val threadId = ThreadId("\$thread")
    private val link = "https://matrix.to/#/!room:x/\$message"
    private val botId = UserId(MatchaConfig.REMINDER_BOT_USER_ID)
    private val boardRoot = "\$board"
    private val cardId = "card-1"

    private data class SentEvent(val type: String, val content: String) {
        val body: String? get() = Json.parseToJsonElement(content).jsonObject["body"]?.jsonPrimitive?.content
    }

    private val sent = mutableListOf<SentEvent>()
    private val invited = mutableListOf<UserId>()
    private var botMembership: RoomMembershipState? = RoomMembershipState.JOIN
    private var memberLookups = 0

    private fun aRoom(
        canSend: Boolean = true,
        canInvite: Boolean = false,
        isDm: Boolean = false,
        sendResult: (String) -> Result<EventId> = { type -> Result.success(if (type == "m.room.message") EventId("\$command") else EventId("\$revision")) },
        inviteResult: Result<Unit> = Result.success(Unit),
        items: List<MatrixTimelineItem> = emptyList(),
    ) = FakeJoinedRoom(
        baseRoom = FakeBaseRoom(
            initialRoomInfo = aRoomInfo(isDm = isDm, joinedMembersCount = 2),
            roomPermissions = FakeRoomPermissions(canInvite = canInvite, canSendMessage = { canSend }, canSendState = { canSend }),
            eventPermalinkResult = { Result.success("https://matrix.to/#/!room:x/${it.value}") },
            getUpdatedMemberResult = { userId ->
                memberLookups++
                botMembership?.let { Result.success(aRoomMember(userId = userId, membership = it)) } ?: Result.failure(IllegalStateException("Not a member"))
            },
        ),
        threadsListService = FakeThreadsListService(paginationStatus = MutableStateFlow(ThreadListPaginationStatus.Idle(hasMoreToLoad = false))),
        createTimelineResult = {
            Result.success(
                FakeTimeline(
                    timelineItems = MutableStateFlow(items),
                    backwardPaginationStatus = MutableStateFlow(Timeline.PaginationStatus(isPaginating = false, hasMoreToLoad = false)),
                )
            )
        },
        sendRawEventResult = { type, content ->
            sent += SentEvent(type, content)
            sendResult(type)
        },
        inviteUserResult = { userId ->
            invited += userId
            inviteResult
        },
        getStateEventsJsonResult = { type ->
            if (type == StateEventType.Custom(MatchaConfig.STATE_KANBAN)) {
                Result.success(mapOf("" to """{"version": 1, "event_id": "$boardRoot"}"""))
            } else {
                Result.success(emptyMap())
            }
        },
    )

    private fun TestScope.createRoomReminderPresenter(
        target: MatchaReminderTarget = MatchaReminderTarget(message = "Call the venue", eventId = messageId),
        room: FakeJoinedRoom = aRoom(),
        clock: ReminderClock = ReminderClock { now },
        snackbarDispatcher: SnackbarDispatcher = SnackbarDispatcher(),
    ): RoomReminderPresenter {
        val store = MatchaRoomStore(room, backgroundScope)
        // The store reads in the background scope, which the presenter's test does not drive.
        testScheduler.advanceTimeBy(1_000)
        testScheduler.runCurrent()
        return RoomReminderPresenter(target, room, store, clock, snackbarDispatcher)
    }

    private suspend fun TurbineTestContext<RoomReminderState>.awaitBotState(predicate: (RoomReminderBotState) -> Boolean): RoomReminderState =
        consumeItemsUntilPredicate { predicate(it.botState) }.last()

    private suspend fun TurbineTestContext<RoomReminderState>.awaitJoined(): RoomReminderState =
        consumeItemsUntilPredicate { it.botState == RoomReminderBotState.Joined && it.link != null }.last()

    @Test
    fun `present - the bot is in the chat, so the options are offered with the message link`() = runTest {
        createRoomReminderPresenter().test {
            assertThat(awaitItem().botState).isEqualTo(RoomReminderBotState.Checking)
            val state = awaitJoined()
            assertThat(state.message).isEqualTo("Call the venue")
            assertThat(state.link).isEqualTo(RoomReminderLink.Message)
            assertThat(state.maxMessageLength).isEqualTo(MatchaConfig.REMINDER_MESSAGE_MAX_LENGTH - link.length - 1)
            assertThat(state.quickOptions).containsExactly(
                RoomReminderOption.InHours(2),
                RoomReminderOption.InHours(8),
                RoomReminderOption.InHours(24),
            ).inOrder()
            assertThat(state.step).isEqualTo(RoomReminderStep.Options)
            assertThat(state.customDate).isEqualTo(LocalDate.of(2026, 3, 29))
            assertThat(state.customTime).isEqualTo(LocalTime.of(9, 0))
        }
    }

    @Test
    fun `present - a thread reminder links the thread, and a long message is prefilled with its start`() = runTest {
        createRoomReminderPresenter(target = MatchaReminderTarget(message = "A".repeat(500), threadId = threadId)).test {
            val state = consumeItemsUntilPredicate { it.link != null }.last()
            assertThat(state.link).isEqualTo(RoomReminderLink.Thread)
            assertThat(state.message).hasLength(RoomReminderPresenter.PREFILL_MAX_LENGTH)
        }
    }

    @Test
    fun `present - a deadline is offered only when it is ahead and within a year`() = runTest {
        val deadline = now.plusDays(3).toInstant().toEpochMilli()
        createRoomReminderPresenter(target = MatchaReminderTarget(message = "Task", dueAt = deadline)).test {
            assertThat(awaitBotState { it == RoomReminderBotState.Joined }.quickOptions.last()).isEqualTo(RoomReminderOption.AtDeadline(deadline))
        }
        listOf(nowMillis - 1, now.plusDays(366).toInstant().toEpochMilli()).forEach { dueAt ->
            createRoomReminderPresenter(target = MatchaReminderTarget(message = "Task", dueAt = dueAt)).test {
                assertThat(awaitBotState { it == RoomReminderBotState.Joined }.quickOptions.filterIsInstance<RoomReminderOption.AtDeadline>()).isEmpty()
            }
        }
    }

    @Test
    fun `present - a quick option sends the relative command once, with the link appended once, and closes`() = runTest {
        val snackbarDispatcher = SnackbarDispatcher()
        createRoomReminderPresenter(
            target = MatchaReminderTarget(message = "Call the venue\n$link", eventId = messageId),
            snackbarDispatcher = snackbarDispatcher,
        ).test {
            awaitJoined().eventSink(RoomReminderEvent.Send(RoomReminderOption.InHours(2)))
            val state = consumeItemsUntilPredicate { it.isRequested }.last()
            assertThat(state.sending).isEqualTo(RoomReminderOption.InHours(2))
            assertThat(sent.map { it.type }).containsExactly("m.room.message")
            assertThat(sent.single().body).isEqualTo("!remind 7200s Call the venue\n$link")
            assertThat(snackbarDispatcher.snackbarMessage.first()?.messageResId).isEqualTo(R.string.screen_matcha_reminder_requested)
        }
    }

    @Test
    fun `present - the deadline option sends the absolute command`() = runTest {
        val deadline = ZonedDateTime.of(2026, 4, 1, 17, 0, 0, 0, zone).toInstant().toEpochMilli()
        createRoomReminderPresenter(target = MatchaReminderTarget(message = "Task", dueAt = deadline)).test {
            awaitBotState { it == RoomReminderBotState.Joined }.eventSink(RoomReminderEvent.Send(RoomReminderOption.AtDeadline(deadline)))
            consumeItemsUntilPredicate { it.isRequested }
            assertThat(sent.single().body).isEqualTo("!remind at 2026-04-01T15:00:00.000Z Task")
        }
    }

    @Test
    fun `present - the custom date and time are sent in the absolute mode`() = runTest {
        createRoomReminderPresenter(target = MatchaReminderTarget(message = "Call the venue")).test {
            val joined = awaitBotState { it == RoomReminderBotState.Joined }
            joined.eventSink(RoomReminderEvent.OpenCustom)
            joined.eventSink(RoomReminderEvent.SetDate(LocalDate.of(2026, 3, 30)))
            joined.eventSink(RoomReminderEvent.SetTime(LocalTime.of(9, 30)))
            val custom = consumeItemsUntilPredicate { it.customTime == LocalTime.of(9, 30) }.last()
            assertThat(custom.step).isEqualTo(RoomReminderStep.Custom)
            assertThat(custom.customError).isNull()
            custom.eventSink(RoomReminderEvent.Send(RoomReminderOption.Custom))
            consumeItemsUntilPredicate { it.isRequested }
            // Summer time has started: 09:30 in Amsterdam is 07:30 UTC.
            assertThat(sent.single().body).isEqualTo("!remind at 2026-03-30T07:30:00.000Z Call the venue")
        }
    }

    @Test
    fun `present - a custom time that is not ahead, more than a year ahead or skipped by summer time is refused`() = runTest {
        val cases = listOf(
            Triple(LocalDate.of(2026, 3, 28), LocalTime.of(11, 59), RoomReminderError.NotInFuture),
            Triple(LocalDate.of(2027, 3, 29), LocalTime.of(12, 0), RoomReminderError.TooFar),
            Triple(LocalDate.of(2026, 3, 29), LocalTime.of(2, 30), RoomReminderError.NonexistentTime),
        )
        createRoomReminderPresenter(target = MatchaReminderTarget(message = "Call the venue")).test {
            val joined = awaitBotState { it == RoomReminderBotState.Joined }
            joined.eventSink(RoomReminderEvent.OpenCustom)
            for ((date, time, error) in cases) {
                joined.eventSink(RoomReminderEvent.SetDate(date))
                joined.eventSink(RoomReminderEvent.SetTime(time))
                val state = consumeItemsUntilPredicate { it.customDate == date && it.customTime == time }.last()
                assertThat(state.customError).isEqualTo(error)
                state.eventSink(RoomReminderEvent.Send(RoomReminderOption.Custom))
            }
            // Exactly one year ahead is still accepted.
            joined.eventSink(RoomReminderEvent.SetDate(LocalDate.of(2027, 3, 28)))
            joined.eventSink(RoomReminderEvent.SetTime(LocalTime.of(12, 0)))
            assertThat(consumeItemsUntilPredicate { it.customDate == LocalDate.of(2027, 3, 28) && it.customTime == LocalTime.NOON }.last().customError).isNull()
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(sent).isEmpty()
    }

    @Test
    fun `present - the custom time is checked again when sent, as the sheet may stay open past it`() = runTest {
        var clockNow = now
        createRoomReminderPresenter(target = MatchaReminderTarget(message = "Call the venue"), clock = { clockNow }).test {
            val joined = awaitBotState { it == RoomReminderBotState.Joined }
            joined.eventSink(RoomReminderEvent.OpenCustom)
            joined.eventSink(RoomReminderEvent.SetDate(LocalDate.of(2026, 3, 28)))
            joined.eventSink(RoomReminderEvent.SetTime(LocalTime.of(12, 30)))
            val valid = consumeItemsUntilPredicate { it.customTime == LocalTime.of(12, 30) }.last()
            assertThat(valid.customError).isNull()
            clockNow = now.plusHours(1)
            valid.eventSink(RoomReminderEvent.Send(RoomReminderOption.Custom))
            assertThat(consumeItemsUntilPredicate { it.customError != null }.last().customError).isEqualTo(RoomReminderError.NotInFuture)
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(sent).isEmpty()
    }

    @Test
    fun `present - an empty or too long message is not sent`() = runTest {
        createRoomReminderPresenter(target = MatchaReminderTarget(message = "  ")).test {
            val joined = awaitBotState { it == RoomReminderBotState.Joined }
            assertThat(joined.messageError).isNull()
            joined.eventSink(RoomReminderEvent.Send(RoomReminderOption.InHours(8)))
            assertThat(consumeItemsUntilPredicate { it.messageError != null }.last().messageError).isEqualTo(RoomReminderError.EmptyMessage)
            joined.eventSink(RoomReminderEvent.UpdateMessage("A".repeat(MatchaConfig.REMINDER_MESSAGE_MAX_LENGTH + 1)))
            val tooLong = consumeItemsUntilPredicate { it.messageError == RoomReminderError.MessageTooLong }.last()
            tooLong.eventSink(RoomReminderEvent.Send(RoomReminderOption.InHours(8)))
            tooLong.eventSink(RoomReminderEvent.UpdateMessage("A".repeat(MatchaConfig.REMINDER_MESSAGE_MAX_LENGTH)))
            assertThat(consumeItemsUntilPredicate { it.message.length == MatchaConfig.REMINDER_MESSAGE_MAX_LENGTH }.last().messageError).isNull()
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(sent).isEmpty()
    }

    @Test
    fun `present - a failed send says so and keeps the options`() = runTest {
        createRoomReminderPresenter(room = aRoom(sendResult = { Result.failure(IllegalStateException("offline")) })).test {
            awaitJoined().eventSink(RoomReminderEvent.Send(RoomReminderOption.InHours(24)))
            val state = consumeItemsUntilPredicate { it.hasSendFailed }.last()
            assertThat(state.sending).isNull()
            assertThat(state.isRequested).isFalse()
            assertThat(state.quickOptions).hasSize(3)
        }
    }

    @Test
    fun `present - a task reminder records the request on its card`() = runTest {
        val card = CardRevision(
            cardId = cardId,
            title = "Book the venue",
            description = "Ask for the big room",
            status = TaskStatus.Doing,
            archived = false,
            threadId = null,
            dueAt = null,
            reminderRequested = null,
            showToRoom = true,
        )
        val cardEvent = """{"type": "${MatchaConfig.EVENT_KANBAN_CARD}", "event_id": "${'$'}card", "sender": "@bob:x", "origin_server_ts": 1,
            |"content": ${KanbanProtocol.cardContent(card, boardRoot)}}""".trimMargin()
        val room = aRoom(items = listOf(aTimelineItem(cardEvent)))
        createRoomReminderPresenter(target = MatchaReminderTarget(message = "Book the venue", cardId = cardId), room = room).test {
            awaitBotState { it == RoomReminderBotState.Joined }.eventSink(RoomReminderEvent.Send(RoomReminderOption.InHours(2)))
            consumeItemsUntilPredicate { it.isRequested }
        }
        assertThat(sent.map { it.type }).containsExactly("m.room.message", MatchaConfig.EVENT_KANBAN_CARD).inOrder()
        val revision = KanbanProtocol.parseCard(
            RawEvent.parse("""{"type": "${MatchaConfig.EVENT_KANBAN_CARD}", "event_id": "${'$'}revision", "content": ${sent[1].content}}""")!!,
            boardRoot,
        )
        assertThat(revision).isEqualTo(card.copy(reminderRequested = ReminderRequestRef(eventId = "\$command", at = nowMillis + 2 * 3_600_000)))
    }

    @Test
    fun `present - the bot is invited, so the user waits, and the options appear once it joins`() = runTest {
        botMembership = RoomMembershipState.INVITE
        val room = aRoom()
        createRoomReminderPresenter(room = room).test {
            val waiting = awaitBotState { it is RoomReminderBotState.Waiting }
            assertThat(waiting.botState).isEqualTo(RoomReminderBotState.Waiting(isChecking = false))
            // Retrying while the bot has not joined looks again and keeps waiting.
            val lookups = memberLookups
            waiting.eventSink(RoomReminderEvent.CheckBotAgain)
            testScheduler.runCurrent()
            assertThat(memberLookups).isEqualTo(lookups + 1)
            assertThat(awaitLastSequentialItem().botState).isEqualTo(RoomReminderBotState.Waiting(isChecking = false))
            // The bot joins: the member count changes and the sheet moves on by itself.
            botMembership = RoomMembershipState.JOIN
            room.givenRoomInfo(aRoomInfo(joinedMembersCount = 3))
            awaitBotState { it == RoomReminderBotState.Joined }
        }
        assertThat(sent).isEmpty()
    }

    @Test
    fun `present - retry finds the bot joined`() = runTest {
        botMembership = RoomMembershipState.INVITE
        createRoomReminderPresenter().test {
            val waiting = awaitBotState { it is RoomReminderBotState.Waiting }
            botMembership = RoomMembershipState.JOIN
            waiting.eventSink(RoomReminderEvent.CheckBotAgain)
            awaitBotState { it == RoomReminderBotState.Joined }
        }
    }

    @Test
    fun `present - the bot is not in the chat, so the user confirms, the bot is invited and the user waits`() = runTest {
        botMembership = null
        createRoomReminderPresenter(room = aRoom(canInvite = true)).test {
            val invite = awaitBotState { it is RoomReminderBotState.CanInvite }
            assertThat(invite.botState).isEqualTo(RoomReminderBotState.CanInvite(isDm = false, isInviting = false, hasFailed = false))
            assertThat(invited).isEmpty()
            invite.eventSink(RoomReminderEvent.InviteBot)
            awaitBotState { it == RoomReminderBotState.CanInvite(isDm = false, isInviting = true, hasFailed = false) }
            awaitBotState { it == RoomReminderBotState.Waiting(isChecking = false) }
        }
        assertThat(invited).containsExactly(botId)
        assertThat(sent).isEmpty()
    }

    @Test
    fun `present - in a DM the invite step warns that a third member joins, and a failed invite says so`() = runTest {
        botMembership = RoomMembershipState.LEAVE
        createRoomReminderPresenter(room = aRoom(canInvite = true, isDm = true, inviteResult = Result.failure(IllegalStateException("offline")))).test {
            val invite = awaitBotState { it is RoomReminderBotState.CanInvite }
            assertThat((invite.botState as RoomReminderBotState.CanInvite).isDm).isTrue()
            invite.eventSink(RoomReminderEvent.InviteBot)
            awaitBotState { it == RoomReminderBotState.CanInvite(isDm = true, isInviting = false, hasFailed = true) }
        }
    }

    @Test
    fun `present - the bot is not in the chat and the user cannot invite it, or it is banned`() = runTest {
        botMembership = null
        createRoomReminderPresenter(room = aRoom(canInvite = false)).test {
            awaitBotState { it == RoomReminderBotState.CannotInvite }
        }
        botMembership = RoomMembershipState.BAN
        createRoomReminderPresenter(room = aRoom(canInvite = true)).test {
            awaitBotState { it == RoomReminderBotState.CannotInvite }
        }
    }

    @Test
    fun `present - the user cannot post in the chat`() = runTest {
        createRoomReminderPresenter(room = aRoom(canSend = false)).test {
            awaitBotState { it == RoomReminderBotState.CannotSend }
        }
    }

    @Test
    fun `present - the bot left while the sheet was open, so nothing is sent`() = runTest {
        createRoomReminderPresenter(room = aRoom(canInvite = true)).test {
            val joined = awaitJoined()
            botMembership = RoomMembershipState.LEAVE
            joined.eventSink(RoomReminderEvent.Send(RoomReminderOption.InHours(2)))
            val state = awaitBotState { it is RoomReminderBotState.CanInvite }
            assertThat(state.sending).isNull()
        }
        assertThat(sent).isEmpty()
    }

    @Test
    fun `localInstant - rejects times skipped by summer time and keeps the earlier of repeated times`() {
        assertThat(localInstant(LocalDate.of(2026, 3, 29), LocalTime.of(2, 30), zone)).isNull()
        assertThat(localInstant(LocalDate.of(2026, 3, 29), LocalTime.of(3, 0), zone))
            .isEqualTo(ZonedDateTime.of(2026, 3, 29, 1, 0, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli())
        // 02:30 happens twice on 25 October 2026; the first one, still in summer time, is used.
        assertThat(localInstant(LocalDate.of(2026, 10, 25), LocalTime.of(2, 30), zone))
            .isEqualTo(ZonedDateTime.of(2026, 10, 25, 0, 30, 0, 0, ZoneId.of("UTC")).toInstant().toEpochMilli())
    }

    @Test
    fun `validateReminder - checks the bounds of the bot`() {
        val max = MatchaConfig.REMINDER_MESSAGE_MAX_LENGTH
        val year = MatchaConfig.REMINDER_MAX_DELAY_DAYS * 86_400_000L
        assertThat(validateReminder(null, "Hi", max, nowMillis)).isEqualTo(RoomReminderError.NonexistentTime)
        assertThat(validateReminder(nowMillis, "Hi", max, nowMillis)).isEqualTo(RoomReminderError.NotInFuture)
        assertThat(validateReminder(nowMillis + year + 1, "Hi", max, nowMillis)).isEqualTo(RoomReminderError.TooFar)
        assertThat(validateReminder(nowMillis + year, "Hi", max, nowMillis)).isNull()
        assertThat(validateReminder(nowMillis + 1, " \n", max, nowMillis)).isEqualTo(RoomReminderError.EmptyMessage)
        assertThat(validateReminder(nowMillis + 1, " ${"A".repeat(max)} ", max, nowMillis)).isNull()
        assertThat(validateReminder(nowMillis + 1, "A".repeat(max + 1), max, nowMillis)).isEqualTo(RoomReminderError.MessageTooLong)
    }

    private fun aTimelineItem(json: String) = MatrixTimelineItem.Event(
        uniqueId = UniqueId("item-1"),
        event = anEventTimelineItem(
            eventId = EventId("\$card"),
            debugInfoProvider = TimelineItemDebugInfoProvider { aTimelineItemDebugInfo(originalJson = json) },
        ),
    )
}
