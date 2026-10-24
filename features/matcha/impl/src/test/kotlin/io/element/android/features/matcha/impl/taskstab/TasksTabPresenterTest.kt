/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.taskstab

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.element.android.appconfig.MatchaConfig
import io.element.android.features.matcha.impl.protocol.KanbanProtocol
import io.element.android.features.matcha.impl.protocol.TaskStatus
import io.element.android.features.matcha.impl.store.A_BOARD_ROOT
import io.element.android.features.matcha.impl.store.PointerRoom
import io.element.android.features.matcha.impl.store.aCardItem
import io.element.android.features.matcha.impl.store.aMatchaJoinedRoom
import io.element.android.features.matcha.impl.store.aMatchaTimeline
import io.element.android.features.matcha.impl.store.aThreadListItem
import io.element.android.features.matcha.impl.store.settle
import io.element.android.features.matcha.impl.tasks.A_NOW
import io.element.android.features.matcha.impl.tasks.TaskEditorEvent
import io.element.android.features.matcha.impl.tasks.TaskItem
import io.element.android.features.matcha.impl.tasks.aDeadlineFormatter
import io.element.android.features.matcha.impl.tasks.aFixedClock
import io.element.android.features.matcha.impl.tasks.aTaskEditorPresenterFactory
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.featureflag.test.FakeFeatureFlagService
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.ThreadId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.BaseRoom
import io.element.android.libraries.matrix.api.room.CurrentUserMembership
import io.element.android.libraries.matrix.api.room.MessageEventType
import io.element.android.libraries.matrix.api.room.RoomInfo
import io.element.android.libraries.matrix.api.room.StateEventType
import io.element.android.libraries.matrix.api.room.threads.ThreadListPaginationStatus
import io.element.android.libraries.matrix.api.roomlist.RoomList
import io.element.android.libraries.matrix.api.roomlist.RoomSummary
import io.element.android.libraries.matrix.api.spaces.SpaceServiceFilter
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.room.aRoomInfo
import io.element.android.libraries.matrix.test.room.aRoomSummary
import io.element.android.libraries.matrix.test.roomlist.FakeDynamicRoomList
import io.element.android.libraries.matrix.test.roomlist.FakeRoomListService
import io.element.android.libraries.matrix.test.spaces.FakeSpaceService
import io.element.android.libraries.previewutils.room.aSpaceRoom
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.services.appnavstate.test.FakeAppNavigationStateService
import io.element.android.services.appnavstate.test.aNavigationState
import io.element.android.services.appnavstate.test.anAppNavigationState
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.consumeItemsUntilTimeout
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TasksTabPresenterTest {
    private val me = A_SESSION_ID.value
    private val bob = "@bob:matrix.org"
    private val carol = "@carol:matrix.org"
    private val names = mapOf(bob to "Bob", carol to "Carol")
    private val design = RoomId("!design:matrix.org")
    private val launch = RoomId("!launch:matrix.org")
    private val chat = RoomId("!chat:matrix.org")
    private val dm = RoomId("!dm:matrix.org")
    private val acme = RoomId("!acme:matrix.org")
    private val acmeSub = RoomId("!acme-sub:matrix.org")
    private val boardRoot = A_BOARD_ROOT

    private val zone = ZoneId.systemDefault()
    private val today = Instant.ofEpochMilli(A_NOW).atZone(zone).toLocalDate()

    private fun at(date: LocalDate, hour: Int, minute: Int = 0) = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    @Test
    fun `nothing is shown and no chat is read when the feature is off`() = runTest {
        var pointerReads = 0
        val room = aMatchaJoinedRoom(design, backgroundScope, boardPointerResult = {
            pointerReads++
            Result.success(emptyMap())
        })
        aPresenter(aClient(listOf(design to room)), isEnabled = false).test {
            assertThat(awaitItem().isVisible).isFalse()
            settle()
            cancelAndIgnoreRemainingEvents()
        }
        assertThat(pointerReads).isEqualTo(0)
    }

    @Test
    fun `the tasks of every chat with a board are grouped by deadline and name their chat`() = runTest {
        val noBoard = aMatchaJoinedRoom(chat, backgroundScope)
        val client = aClient(
            listOf(
                design to aBoardRoom(
                    design,
                    linkedCard(1, "\$t1", "Book the venue", dueAt = A_NOW - 3_600_000),
                    linkedCard(2, "\$t2", "Draft the press release", dueAt = at(today.plusDays(5), 12)),
                    linkedCard(3, "\$t3", "Pick the date", status = TaskStatus.Done),
                ),
                launch to aBoardRoom(
                    launch,
                    linkedCard(1, "\$t4", "Order the banners", status = TaskStatus.Doing, dueAt = at(today, 23, 59)),
                    linkedCard(2, "\$t5", "Print the flyers", dueAt = at(today.plusDays(1), 12)),
                    legacyCard(3, "Call the caterer"),
                    linkedCard(4, "\$t6", "Old idea", archived = true),
                ),
                chat to noBoard,
            )
        )
        aPresenter(client).test {
            val content = awaitUntil { it.checkingCount == 0 && it.content is TasksTabContent.Tasks }.content as TasksTabContent.Tasks
            assertThat(content.groups.map { group -> group.section to group.tasks.map { it.task.title } }).containsExactly(
                TaskSection.Overdue to listOf("Book the venue"),
                TaskSection.Today to listOf("Order the banners"),
                TaskSection.Tomorrow to listOf("Print the flyers"),
                TaskSection.Upcoming to listOf("Draft the press release"),
                TaskSection.NoDeadline to listOf("Call the caterer"),
            ).inOrder()
            assertThat(content.groups.first().tasks.single().chat.name).isEqualTo("design")
            assertThat(content.done.map { it.task.title }).containsExactly("Pick the date")
            assertThat(content.isDoneExpanded).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
        // A chat without a board is let go as soon as it has been checked.
        noBoard.baseRoom.assertDestroyed()
    }

    @Test
    fun `the chips select a status, the done tasks or the archived ones`() = runTest {
        val client = aClient(
            listOf(
                design to aBoardRoom(
                    design,
                    linkedCard(1, "\$t1", "Book the venue"),
                    linkedCard(2, "\$t2", "Order the banners", status = TaskStatus.Doing),
                    linkedCard(3, "\$t3", "Pick the date", status = TaskStatus.Done),
                    linkedCard(4, "\$t4", "Old idea", archived = true),
                ),
            )
        )
        aPresenter(client).test {
            val all = awaitUntil { it.content is TasksTabContent.Tasks }
            assertThat(all.titles()).containsExactly("Order the banners", "Book the venue").inOrder()
            all.eventSink(TasksTabEvent.ToggleDone)
            assertThat(
                awaitUntil { it.tasksContent()?.isDoneExpanded == true }.titles()
            ).containsExactly("Order the banners", "Book the venue", "Pick the date")
            all.eventSink(TasksTabEvent.SetFilter(TasksTabFilter.Doing))
            assertThat(awaitUntil { it.filter == TasksTabFilter.Doing }.titles()).containsExactly("Order the banners")
            all.eventSink(TasksTabEvent.SetFilter(TasksTabFilter.Done))
            val done = awaitUntil { it.filter == TasksTabFilter.Done }
            assertThat(done.titles()).containsExactly("Pick the date")
            assertThat(done.tasksContent()!!.groups.single().section).isNull()
            all.eventSink(TasksTabEvent.SetFilter(TasksTabFilter.Archived))
            assertThat(awaitUntil { it.filter == TasksTabFilter.Archived }.titles()).containsExactly("Old idea")
        }
    }

    @Test
    fun `Chat narrows the list to a chosen chat, and tapping it again shows every chat`() = runTest {
        val client = aClient(
            listOf(
                design to aBoardRoom(design, linkedCard(1, "\$t1", "Book the venue")),
                launch to aBoardRoom(launch, linkedCard(1, "\$t2", "Print the flyers")),
                chat to aMatchaJoinedRoom(chat, backgroundScope),
            )
        )
        aPresenter(client).test {
            val state = awaitUntil { it.titles().size == 2 && it.checkingCount == 0 }
            state.eventSink(TasksTabEvent.ToggleChatFilter)
            val sheet = awaitUntil { it.sheet != null }.sheet as TasksTabSheet.ChooseChat
            assertThat(sheet.purpose).isEqualTo(ChooseChatPurpose.Filter)
            // Only the chats with a board are offered, and the search narrows them.
            assertThat(sheet.chats.map { it.roomId }).containsExactly(design, launch)
            sheet.searchQuery.edit { replace(0, length, "LAUN") }
            assertThat(sheet.visibleChats.map { it.roomId }).containsExactly(launch)
            state.eventSink(TasksTabEvent.ChooseChat(launch))
            val narrowed = awaitUntil { it.chatFilter != null }
            assertThat(narrowed.sheet).isNull()
            assertThat(narrowed.chatFilter!!.roomId).isEqualTo(launch)
            assertThat(narrowed.titles()).containsExactly("Print the flyers")
            narrowed.eventSink(TasksTabEvent.ToggleChatFilter)
            assertThat(awaitUntil { it.chatFilter == null }.titles()).hasSize(2)
        }
    }

    @Test
    fun `leaving a chat drops its tasks at once, and every chat goes a while after leaving the tab`() = runTest {
        val designRoom = aBoardRoom(design, linkedCard(1, "\$t1", "Book the venue"))
        val launchRoom = aBoardRoom(launch, linkedCard(1, "\$t2", "Print the flyers"))
        val summaries = MutableStateFlow(listOf(aSummary(design), aSummary(launch)))
        val client = aClient(listOf(design to designRoom, launch to launchRoom), summaries)
        aPresenter(client).test {
            awaitUntil { it.titles().size == 2 }
            summaries.value = listOf(aSummary(design), aSummary(launch, membership = CurrentUserMembership.LEFT))
            assertThat(awaitUntil { it.titles().size == 1 }.titles()).containsExactly("Book the venue")
            settle()
            launchRoom.baseRoom.assertDestroyed()
            cancelAndIgnoreRemainingEvents()
        }
        settle()
        assertThat(designRoom.baseRoom.isDestroyed).isFalse()
        testScheduler.advanceTimeBy(TasksTabRooms.KEEP_ALIVE)
        settle()
        designRoom.baseRoom.assertDestroyed()
    }

    @Test
    fun `tapping a task opens its thread, and an older card without one opens its chat`() = runTest {
        val openedThreads = mutableListOf<Pair<RoomId, ThreadId>>()
        val openedRooms = mutableListOf<RoomId>()
        val client = aClient(listOf(design to aBoardRoom(design, linkedCard(1, "\$t1", "Book the venue"), legacyCard(2, "Call the caterer"))))
        aPresenter(client, onOpenRoom = { openedRooms += it }, onOpenThread = { room, thread -> openedThreads += room to thread }).test {
            val state = awaitUntil { it.titles().size == 2 }
            val tasks = state.tasksContent()!!.groups.flatMap { it.tasks }
            state.eventSink(TasksTabEvent.OpenTask(design, tasks.first { it.task.threadId != null }.task.id))
            state.eventSink(TasksTabEvent.OpenTask(design, "legacy"))
            assertThat(openedThreads).containsExactly(design to ThreadId("\$t1"))
            assertThat(openedRooms).containsExactly(design)
        }
    }

    @Test
    fun `the task of the open thread is selected`() = runTest {
        val navigation = FakeAppNavigationStateService()
        val client = aClient(listOf(design to aBoardRoom(design, linkedCard(1, "\$t1", "Book the venue"), linkedCard(2, "\$t2", "Print the flyers"))))
        aPresenter(client, navigation = navigation).test {
            awaitUntil { it.titles().size == 2 }
            navigation.emitNavigationState(anAppNavigationState(aNavigationState(A_SESSION_ID, design, ThreadId("\$t2"))))
            val selected = awaitUntil { state -> state.tasks().any { it.isSelected } }.tasks().filter { it.isSelected }
            assertThat(selected.map { it.task.title }).containsExactly("Print the flyers")
        }
    }

    @Test
    fun `the actions sheet acts on the task in its own chat and offers its chat`() = runTest {
        val sent = mutableListOf<Pair<String, String>>()
        val openedRooms = mutableListOf<RoomId>()
        val client = aClient(
            listOf(
                design to aBoardRoom(design, linkedCard(1, "\$t1", "Book the venue")) { type, content ->
                    sent += type to content
                    Result.success(EventId("\$sent"))
                },
                launch to aBoardRoom(launch, linkedCard(1, "\$t2", "Print the flyers")) { _, _ -> error("Nothing must be sent here") },
            )
        )
        val taskId = KanbanProtocol.threadCardId("\$t1")
        aPresenter(client, onOpenRoom = { openedRooms += it }).test {
            val state = awaitUntil { it.titles().size == 2 }
            state.eventSink(TasksTabEvent.ShowActions(design, taskId))
            val actions = awaitUntil { it.sheet is TasksTabSheet.Actions }.sheet as TasksTabSheet.Actions
            assertThat(actions.task.chat.roomId).isEqualTo(design)
            assertThat(actions.canEdit).isTrue()
            assertThat(actions.canArchive).isTrue()
            state.eventSink(TasksTabEvent.Move(design, taskId, TaskStatus.Doing))
            awaitUntil { it.sheet == null }
            settle()
            assertThat(sent.single().first).isEqualTo(MatchaConfig.EVENT_KANBAN_CARD)
            assertThat(sent.single().second).contains("\"column\":\"doing\"")
            state.eventSink(TasksTabEvent.OpenChat(design))
            assertThat(openedRooms).containsExactly(design)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a new task asks for a chat where tasks can be added, then saves there with its thread`() = runTest {
        val sent = mutableListOf<String>()
        val client = aClient(
            listOf(
                design to aBoardRoom(design, linkedCard(1, "\$t1", "Book the venue")) { type, _ ->
                    sent += type
                    Result.success(EventId("\$new"))
                },
                launch to aBoardRoom(launch, linkedCard(1, "\$t2", "Print the flyers"), canSendMessage = { it != MessageEventType.RoomMessage }),
            )
        )
        aPresenter(client).test {
            val state = awaitUntil { it.titles().size == 2 && it.checkingCount == 0 }
            state.eventSink(TasksTabEvent.SetFilter(TasksTabFilter.Doing))
            state.eventSink(TasksTabEvent.NewTask)
            val sheet = awaitUntil { it.sheet != null }.sheet as TasksTabSheet.ChooseChat
            assertThat(sheet.purpose).isEqualTo(ChooseChatPurpose.NewTask)
            // Adding a task creates its thread, so a chat where the user can't post isn't offered.
            assertThat(sheet.chats.map { it.roomId }).containsExactly(design)
            state.eventSink(TasksTabEvent.ChooseChat(design))
            val editor = awaitUntil { it.editor != null }.editor!!
            assertThat(editor.isNew).isTrue()
            // The new task starts in the status of the chosen chip.
            assertThat(editor.status).isEqualTo(TaskStatus.Doing)
            assertThat(editor.canRemind).isFalse()
            editor.eventSink(TaskEditorEvent.SetTitle("Buy snacks"))
            awaitUntil { it.editor?.title == "Buy snacks" }.editor!!.eventSink(TaskEditorEvent.Save)
            awaitUntil { it.editor == null }
            assertThat(sent).containsExactly("m.room.message", MatchaConfig.EVENT_THREAD_METADATA, MatchaConfig.EVENT_KANBAN_CARD).inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `with a chat chosen, a new task goes there without asking`() = runTest {
        val client = aClient(listOf(design to aBoardRoom(design, linkedCard(1, "\$t1", "Book the venue"))))
        aPresenter(client).test {
            val state = awaitUntil { it.titles().isNotEmpty() }
            state.eventSink(TasksTabEvent.ToggleChatFilter)
            awaitUntil { it.sheet != null }
            state.eventSink(TasksTabEvent.ChooseChat(design))
            awaitUntil { it.chatFilter != null }.eventSink(TasksTabEvent.NewTask)
            val opened = awaitUntil { it.editor != null }
            assertThat(opened.sheet).isNull()
            assertThat(opened.editor!!.isNew).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `chats that can't be checked are counted, and Retry checks them again`() = runTest {
        var timeline: Result<Timeline> = Result.failure(IllegalStateException("offline"))
        val room = aMatchaJoinedRoom(design, backgroundScope, createTimelineResult = { timeline })
        aPresenter(aClient(listOf(design to room))).test {
            val failed = awaitUntil { it.failedCount == 1 }
            assertThat(failed.content).isEqualTo(TasksTabContent.Empty)
            timeline = Result.success(aMatchaTimeline())
            failed.eventSink(TasksTabEvent.Retry)
            assertThat(awaitUntil { it.failedCount == 0 && it.checkingCount == 0 }.content).isEqualTo(TasksTabContent.Empty)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a chat whose board isn't in the stored state is found from its history`() = runTest {
        val client = aClient(listOf(design to aBoardRoom(design, linkedCard(1, "\$t1", "Book the venue"), isPointerStored = false)))
        aPresenter(client).test {
            assertThat(awaitUntil { it.titles().isNotEmpty() }.titles()).containsExactly("Book the venue")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `Undo still works after leaving the tab, and the chat goes once its snackbar is gone and the tab has been away a while`() = runTest {
        val sent = mutableListOf<String>()
        val snackbarDispatcher = SnackbarDispatcher()
        val designRoom = aBoardRoom(design, linkedCard(1, "\$t1", "Book the venue")) { _, content ->
            sent += content
            Result.success(EventId("\$e${sent.size}"))
        }
        val taskId = KanbanProtocol.threadCardId("\$t1")
        aPresenter(aClient(listOf(design to designRoom)), snackbarDispatcher = snackbarDispatcher).test {
            awaitUntil { it.titles().isNotEmpty() }.eventSink(TasksTabEvent.SetArchived(design, taskId, archived = true))
            settle()
            cancelAndIgnoreRemainingEvents()
        }
        val snackbar = snackbarDispatcher.snackbarMessage.first { it != null }!!
        assertThat(snackbar.actionResId).isEqualTo(CommonStrings.action_matcha_undo)
        snackbar.action()
        settle()
        assertThat(sent).hasSize(4)
        assertThat(sent.drop(2).all { it.contains("\"archived\":false") }).isTrue()
        snackbarDispatcher.clear()
        testScheduler.advanceTimeBy(TasksTabRooms.KEEP_ALIVE)
        settle()
        designRoom.baseRoom.assertDestroyed()
    }

    @Test
    fun `only a few chats are read at once, while the rest wait their turn`() = runTest {
        val timelines = mutableListOf<RoomId>()
        val rooms = (1..6).map { index ->
            val roomId = RoomId("!room$index:matrix.org")
            // The thread list never finishes loading, so each chat holds its turn until the tab stops waiting for it.
            roomId to aMatchaJoinedRoom(
                roomId = roomId,
                roomCoroutineScope = backgroundScope,
                boardRoot = boardRoot,
                threadsPaginationStatus = ThreadListPaginationStatus.Loading,
                onCreateTimeline = { timelines += roomId },
            )
        }
        aPresenter(aClient(rooms)).test {
            testScheduler.advanceTimeBy(10_000)
            testScheduler.runCurrent()
            assertThat(timelines).hasSize(TasksTabRooms.MAX_CONCURRENT_CHECKS)
            val checking = expectMostRecentItem()
            assertThat(checking.content).isEqualTo(TasksTabContent.Loading)
            assertThat(checking.checkingCount).isEqualTo(6)
            testScheduler.advanceTimeBy(30_000)
            testScheduler.runCurrent()
            assertThat(timelines).hasSize(6)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `only the tasks that involve the user are listed, from their threads and their direct chats`() = runTest {
        val client = aClient(
            listOf(
                design to aBoardRoom(
                    design,
                    linkedCard(1, "\$mine", "My thread"),
                    linkedCard(2, "\$replied", "Bob's thread I replied to", startedBy = bob),
                    // Shared with the chat, but not the user's: it stays on the chat's board.
                    linkedCard(3, "\$others", "Bob's thread", startedBy = bob),
                    legacyCard(4, "My older card", id = "mine"),
                    legacyCard(5, "Bob's older card", id = "bobs", writtenBy = bob),
                    threadReplies = mapOf("\$replied" to listOf(reply(1, "\$replied", carol), reply(2, "\$replied", me))),
                ),
                launch to aBoardRoom(
                    launch,
                    linkedCard(1, "\$dm", "Bob's task in our chat", startedBy = bob, showToRoom = false),
                    roomInfo = aRoomInfo(id = launch, isDirect = true, heroes = listOf(MatrixUser(UserId(bob)))),
                ),
            )
        )
        aPresenter(client).test {
            val state = awaitUntil {
                it.checkingCount == 0 && it.titles().size == 4 &&
                it.tasks().all { task -> task.people.none { name -> name.startsWith("@") } }
            }
            assertThat(state.titles()).containsExactly("My thread", "Bob's thread I replied to", "My older card", "Bob's task in our chat")
            val people = state.tasks().associate { it.task.title to it.people }
            assertThat(people["My thread"]).isEmpty()
            assertThat(people["Bob's thread I replied to"]).containsExactly("Bob", "Carol").inOrder()
            assertThat(people["Bob's task in our chat"]).containsExactly("Bob")
            // A direct chat's members are in every task anyway, so its tasks aren't marked as shared.
            assertThat(
                state.tasks().filter { it.task.isSharedWithChat }.map { it.task.title }
            ).containsExactly("My thread", "Bob's thread I replied to", "My older card")
            assertThat(state.hasPeople).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `People narrows the list to one person's tasks, and tapping it again shows everyone's`() = runTest {
        val client = aClient(
            listOf(
                design to aBoardRoom(
                    design,
                    linkedCard(1, "\$t1", "With Bob", startedBy = bob),
                    linkedCard(2, "\$t2", "With Carol"),
                    linkedCard(3, "\$t3", "Alone"),
                    threadReplies = mapOf("\$t1" to listOf(reply(1, "\$t1", me)), "\$t2" to listOf(reply(2, "\$t2", carol))),
                ),
            )
        )
        aPresenter(client).test {
            val state = awaitUntil { it.titles().size == 3 && it.tasks().flatMap { task -> task.people }.containsAll(listOf("Bob", "Carol")) }
            state.eventSink(TasksTabEvent.TogglePersonFilter)
            val sheet = awaitUntil { it.sheet != null }.sheet as TasksTabSheet.ChoosePerson
            // Everyone else the user's tasks involve, by name.
            assertThat(sheet.people).containsExactly(TasksTabPerson(bob, "Bob"), TasksTabPerson(carol, "Carol")).inOrder()
            assertThat(sheet.selectedUserId).isNull()
            state.eventSink(TasksTabEvent.ChoosePerson(carol))
            val narrowed = awaitUntil { it.personFilter != null }
            assertThat(narrowed.sheet).isNull()
            assertThat(narrowed.personFilter).isEqualTo(TasksTabPerson(carol, "Carol"))
            assertThat(narrowed.titles()).containsExactly("With Carol")
            narrowed.eventSink(TasksTabEvent.TogglePersonFilter)
            assertThat(awaitUntil { it.personFilter == null }.titles()).hasSize(3)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the table holds the tasks the chips select and sorts by the tapped column`() = runTest {
        val client = aClient(
            listOf(
                design to aBoardRoom(
                    design,
                    linkedCard(1, "\$t1", "Book the venue", dueAt = A_NOW + 2_000),
                    linkedCard(2, "\$t2", "Archive me", archived = true),
                    linkedCard(3, "\$t3", "Call the caterer"),
                    linkedCard(4, "\$t4", "Draft the release", status = TaskStatus.Done, dueAt = A_NOW + 1_000),
                ),
                launch to aBoardRoom(launch, linkedCard(1, "\$t5", "Order the banners", status = TaskStatus.Doing, dueAt = A_NOW + 3_000)),
            )
        )
        aPresenter(client).test {
            awaitUntil { it.checkingCount == 0 && it.titles().size == 3 }.eventSink(TasksTabEvent.ToggleTable)
            val table = awaitUntil { it.content is TasksTabContent.Table }
            assertThat(table.isTable).isTrue()
            // By deadline, soonest first and none last, done tasks included under All.
            assertThat(table.rowTitles()).containsExactly("Draft the release", "Book the venue", "Order the banners", "Call the caterer").inOrder()
            assertThat(table.tableSort()).isEqualTo(TaskTableSort(TaskTableColumn.Due, isAscending = true))
            table.eventSink(TasksTabEvent.SortTable(TaskTableColumn.Due))
            // The other way round, still with the tasks without a deadline last.
            assertThat(awaitUntil { it.tableSort()?.isAscending == false }.rowTitles())
                .containsExactly("Order the banners", "Book the venue", "Draft the release", "Call the caterer")
                .inOrder()
            table.eventSink(TasksTabEvent.SortTable(TaskTableColumn.Status))
            assertThat(awaitUntil { it.tableSort()?.column == TaskTableColumn.Status }.rowTitles())
                .containsExactly("Book the venue", "Call the caterer", "Order the banners", "Draft the release")
                .inOrder()
            table.eventSink(TasksTabEvent.SortTable(TaskTableColumn.Chat))
            assertThat(awaitUntil { it.tableSort()?.column == TaskTableColumn.Chat }.rowTitles().first()).isNotEqualTo("Order the banners")
            table.eventSink(TasksTabEvent.SortTable(TaskTableColumn.Chat))
            assertThat(awaitUntil { it.tableSort()?.let { sort -> sort.column == TaskTableColumn.Chat && !sort.isAscending } == true }.rowTitles().first())
                .isEqualTo("Order the banners")
            table.eventSink(TasksTabEvent.SetFilter(TasksTabFilter.Archived))
            assertThat(awaitUntil { it.filter == TasksTabFilter.Archived }.rowTitles()).containsExactly("Archive me")
            table.eventSink(TasksTabEvent.SetFilter(TasksTabFilter.Done))
            assertThat(awaitUntil { it.filter == TasksTabFilter.Done }.rowTitles()).containsExactly("Draft the release")
            table.eventSink(TasksTabEvent.ToggleTable)
            assertThat(awaitUntil { !it.isTable }.content).isInstanceOf(TasksTabContent.Tasks::class.java)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the table puts rows without a value in the sorted column last either way`() {
        fun row(title: String?, people: List<String>, chat: String?) = TabTask(
            chat = TasksTabChat(design, chat, AvatarData(design.value, chat, size = AvatarSize.RoomListItem)),
            task = TaskItem(
                id = title ?: "untitled",
                title = title,
                summary = "",
                status = TaskStatus.Todo,
                isArchived = false,
                threadId = null,
                dueAt = null,
                deadline = null,
                isOverdue = false,
                replyCount = null,
                hasReminder = false,
                isSharedWithChat = false,
                participants = emptySet(),
            ),
            createdAt = 0,
            isSelected = false,
            people = people.toImmutableList(),
        )
        val rows = listOf(row("b", emptyList(), null), row(null, listOf("Carol"), "Alpha"), row("A", listOf("bob"), "beta"))
        fun titles(column: TaskTableColumn, isAscending: Boolean) = rows.sortedFor(TaskTableSort(column, isAscending)).map { it.task.title }
        assertThat(titles(TaskTableColumn.Task, isAscending = true)).containsExactly("A", "b", null).inOrder()
        assertThat(titles(TaskTableColumn.Task, isAscending = false)).containsExactly("b", "A", null).inOrder()
        assertThat(titles(TaskTableColumn.People, isAscending = true)).containsExactly("A", null, "b").inOrder()
        assertThat(titles(TaskTableColumn.People, isAscending = false)).containsExactly(null, "A", "b").inOrder()
        assertThat(titles(TaskTableColumn.Chat, isAscending = false)).containsExactly("A", null, "b").inOrder()
    }

    @Test
    fun `with a space, the boards of its channels are listed with what each board shows`() = runTest {
        val spaceService = FakeSpaceService()
        val client = aClient(
            listOf(
                design to aBoardRoom(
                    design,
                    linkedCard(1, "\$shared", "Bob's shared task", startedBy = bob),
                    linkedCard(2, "\$private", "Bob's private task", startedBy = bob, showToRoom = false),
                ),
                launch to aBoardRoom(launch, linkedCard(1, "\$launch", "My launch task")),
                chat to aBoardRoom(chat, linkedCard(1, "\$chat", "My task elsewhere")),
                dm to aBoardRoom(dm, linkedCard(1, "\$dm", "My task in a direct chat"), roomInfo = aDmInfo(dm)),
            ),
            summaries = MutableStateFlow(listOf(aSummary(design), aSummary(launch), aSummary(chat), aRoomSummary(aDmInfo(dm)))),
            spaceService = spaceService,
        )
        aPresenter(client, spaceId = acme).test {
            assertThat(awaitItem().content).isEqualTo(TasksTabContent.Loading)
            settle()
            spaceService.emitSpaceFilters(acmeFilters(listOf(design, dm), subSpaceChats = listOf(launch)))
            val state = awaitUntil { it.checkingCount == 0 && it.titles().size == 2 }
            // Shared with the chat, so on its board, though it doesn't involve the user; a direct chat is no channel.
            assertThat(state.titles()).containsExactly("Bob's shared task", "My launch task")
            assertThat(state.spaceName).isEqualTo("Acme")
            state.eventSink(TasksTabEvent.ToggleChatFilter)
            val sheet = awaitUntil { it.sheet != null }.sheet as TasksTabSheet.ChooseChat
            assertThat(sheet.chats.map { it.roomId }).containsExactly(design, launch)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `with a space, Loading and Checking only wait for the space's chats`() = runTest {
        val spaceService = FakeSpaceService()
        val chatRoom = aMatchaJoinedRoom(chat, backgroundScope)
        // The space's only chat has no board, and the chat outside the space is never done being checked.
        val client = aClient(listOf(design to aMatchaJoinedRoom(design, backgroundScope), chat to chatRoom), spaceService = spaceService)
        client.givenGetBaseRoomResult(chat, SilentPointerRoom(chatRoom))
        aPresenter(client, spaceId = acme).test {
            awaitItem()
            settle()
            spaceService.emitSpaceFilters(acmeFilters(listOf(design)))
            assertThat(awaitUntil { it.content == TasksTabContent.Empty }.checkingCount).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `with a space, a direct chat in it is neither waited for nor counted as failed`() = runTest {
        val spaceService = FakeSpaceService()
        val slowDm = RoomId("!slow-dm:matrix.org")
        val slowDmRoom = aMatchaJoinedRoom(slowDm, backgroundScope, roomInfo = aDmInfo(slowDm))
        // One direct chat can't be opened, the other is never done being checked.
        val client = aClient(
            listOf(design to aMatchaJoinedRoom(design, backgroundScope), slowDm to slowDmRoom),
            summaries = MutableStateFlow(listOf(aSummary(design), aRoomSummary(aDmInfo(dm)), aRoomSummary(aDmInfo(slowDm)))),
            spaceService = spaceService,
        )
        client.givenGetBaseRoomResult(slowDm, SilentPointerRoom(slowDmRoom))
        aPresenter(client, spaceId = acme).test {
            awaitItem()
            settle()
            spaceService.emitSpaceFilters(acmeFilters(listOf(design, dm, slowDm)))
            val state = awaitUntil { it.content == TasksTabContent.Empty }
            assertThat(state.checkingCount).isEqualTo(0)
            assertThat(state.failedCount).isEqualTo(0)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `with a space, the list loads until the space's filters arrive`() = runTest {
        val spaceService = FakeSpaceService()
        var timelines = 0
        val room = aBoardRoom(design, linkedCard(1, "\$t1", "Book the venue"), onCreateTimeline = { timelines++ })
        val client = aClient(listOf(design to room), spaceService = spaceService)
        aPresenter(client, spaceId = acme).test {
            val first = awaitItem()
            settle()
            // The boards wait for the space's chats, so no other chat's board shows meanwhile, and no chat is read yet.
            assertThat((listOf(first) + consumeItemsUntilTimeout()).map { it.content }.toSet()).containsExactly(TasksTabContent.Loading)
            assertThat(timelines).isEqualTo(0)
            spaceService.emitSpaceFilters(acmeFilters(listOf(design)))
            assertThat(awaitUntil { it.content is TasksTabContent.Tasks }.titles()).containsExactly("Book the venue")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `with a space the user is in no channel of, the boards are empty`() = runTest {
        val spaceService = FakeSpaceService()
        val client = aClient(listOf(design to aBoardRoom(design, linkedCard(1, "\$t1", "Book the venue"))), spaceService = spaceService)
        aPresenter(client, spaceId = acme).test {
            awaitItem()
            settle()
            spaceService.emitSpaceFilters(acmeFilters(listOf(RoomId("!not-joined:matrix.org"))))
            val state = awaitUntil { it.content == TasksTabContent.Empty }
            assertThat(state.spaceName).isEqualTo("Acme")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `with a space, its chats are checked before the others`() = runTest {
        val timelines = mutableListOf<RoomId>()
        val rooms = (1..6).map { index ->
            val roomId = RoomId("!room$index:matrix.org")
            // The thread list never finishes loading, so each chat holds its turn until the screen stops waiting for it.
            roomId to aMatchaJoinedRoom(
                roomId = roomId,
                roomCoroutineScope = backgroundScope,
                boardRoot = boardRoot,
                threadsPaginationStatus = ThreadListPaginationStatus.Loading,
                onCreateTimeline = { timelines += roomId },
            )
        }
        val spaceService = FakeSpaceService()
        // Last in the room list, so they would be checked last without the space.
        val spaceChats = rooms.takeLast(2).map { it.first }
        aPresenter(aClient(rooms, spaceService = spaceService), spaceId = acme).test {
            awaitItem()
            settle()
            spaceService.emitSpaceFilters(acmeFilters(spaceChats))
            testScheduler.advanceTimeBy(10_000)
            testScheduler.runCurrent()
            assertThat(timelines).hasSize(TasksTabRooms.MAX_CONCURRENT_CHECKS)
            assertThat(timelines).containsAtLeastElementsIn(spaceChats)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private data class TestCard(
        val index: Int,
        val id: String,
        val title: String,
        val status: TaskStatus,
        val threadId: String?,
        val dueAt: Long?,
        val archived: Boolean,
        /** Who started the card's thread, or wrote the older card without one. */
        val author: String,
        val showToRoom: Boolean,
    )

    /** A chat with a board holding [cards], each linked card with its thread. */
    private fun TestScope.aBoardRoom(
        roomId: RoomId,
        vararg cards: TestCard,
        canSendMessage: (MessageEventType) -> Boolean = { true },
        /** False when the sync didn't store the board pointer, so only the cards tell the board. */
        isPointerStored: Boolean = true,
        roomInfo: RoomInfo = aRoomInfo(id = roomId),
        threadReplies: Map<String, List<String>> = emptyMap(),
        onCreateTimeline: () -> Unit = {},
        sendRawEventResult: (String, String) -> Result<EventId> = { _, _ -> Result.success(EventId("\$sent")) },
    ) = aMatchaJoinedRoom(
        roomId = roomId,
        roomCoroutineScope = backgroundScope,
        boardRoot = boardRoot.takeIf { isPointerStored },
        items = cards.map { it.item() },
        threads = cards.mapNotNull { card ->
            card.threadId?.let { aThreadListItem(it, body = card.title, timestamp = card.index.toLong(), senderId = UserId(card.author)) }
        },
        canSendMessage = canSendMessage,
        sendRawEventResult = sendRawEventResult,
        roomInfo = roomInfo,
        threadReplies = threadReplies,
        userDisplayNameResult = { userId -> Result.success(names[userId.value]) },
        onCreateTimeline = onCreateTimeline,
    )

    private fun linkedCard(
        index: Int,
        threadId: String,
        title: String,
        status: TaskStatus = TaskStatus.Todo,
        dueAt: Long? = null,
        archived: Boolean = false,
        startedBy: String = me,
        showToRoom: Boolean = true,
    ) = TestCard(index, KanbanProtocol.threadCardId(threadId), title, status, threadId, dueAt, archived, startedBy, showToRoom)

    private fun legacyCard(index: Int, title: String, id: String = "legacy", writtenBy: String = me) =
        TestCard(index, id, title, TaskStatus.Todo, threadId = null, dueAt = null, archived = false, author = writtenBy, showToRoom = true)

    private fun TestCard.item() = aCardItem(index, id, title, status, threadId, dueAt = dueAt, archived = archived, showToRoom = showToRoom, sender = author)

    /** A reply in [threadId] by [sender], as the homeserver returns it. */
    private fun reply(index: Int, threadId: String, sender: String) =
        """{"type": "m.room.message", "event_id": "${'$'}reply$index", "sender": "$sender", "origin_server_ts": $index, """ +
            """"content": {"msgtype": "m.text", "body": "Reply", "m.relates_to": {"rel_type": "m.thread", "event_id": "$threadId"}}}"""

    private fun aDmInfo(roomId: RoomId) = aRoomInfo(id = roomId, name = "dm", isDirect = true, isDm = true, activeMembersCount = 2)

    /** Acme holds [chats], and its sub-space the [subSpaceChats], as the SDK lists a top level space's tree. */
    private fun acmeFilters(chats: List<RoomId>, subSpaceChats: List<RoomId> = emptyList()) = listOf(
        SpaceServiceFilter(aSpaceRoom(roomId = acme, displayName = "Acme"), level = 0, descendants = chats + acmeSub),
        SpaceServiceFilter(aSpaceRoom(roomId = acmeSub, displayName = "Launch team"), level = 1, descendants = subSpaceChats),
    )

    private fun aSummary(roomId: RoomId, membership: CurrentUserMembership = CurrentUserMembership.JOINED): RoomSummary =
        aRoomSummary(roomId = roomId, name = roomId.value.removePrefix("!").substringBefore(":"), currentUserMembership = membership)

    private fun TestScope.aClient(
        rooms: List<Pair<RoomId, FakeJoinedRoom>>,
        summaries: MutableStateFlow<List<RoomSummary>> = MutableStateFlow(rooms.map { aSummary(it.first) }),
        spaceService: FakeSpaceService = FakeSpaceService(),
    ): FakeMatrixClient = FakeMatrixClient(
        sessionCoroutineScope = backgroundScope,
        spaceService = spaceService,
        roomListService = FakeRoomListService(
            allRooms = FakeDynamicRoomList(summaries = summaries, loadingState = MutableStateFlow(RoomList.LoadingState.Loaded(rooms.size))),
        ),
    ).apply {
        rooms.forEach { (roomId, room) ->
            givenGetRoomResult(roomId, room)
            // The board pointer is read from another room instance, so a chat's own room is only destroyed when the tab lets it go.
            givenGetBaseRoomResult(roomId, PointerRoom(room))
        }
    }

    private fun TestScope.aPresenter(
        client: FakeMatrixClient,
        isEnabled: Boolean = true,
        navigation: FakeAppNavigationStateService = FakeAppNavigationStateService(),
        onOpenRoom: (RoomId) -> Unit = {},
        onOpenThread: (RoomId, ThreadId) -> Unit = { _, _ -> },
        snackbarDispatcher: SnackbarDispatcher = SnackbarDispatcher(),
        spaceId: RoomId? = null,
    ): PresenterUnderTest {
        val presenter = TasksTabPresenter(
            client = client,
            rooms = TasksTabRooms(client),
            featureFlagService = FakeFeatureFlagService(mapOf(FeatureFlags.MatchaWorkspaces.key to isEnabled)),
            appNavigationStateService = navigation,
            snackbarDispatcher = snackbarDispatcher,
            deadlineFormatter = aDeadlineFormatter,
            clock = aFixedClock,
            editorPresenterFactory = aTaskEditorPresenterFactory(),
        )
        return PresenterUnderTest(presenter, onOpenRoom, onOpenThread, spaceId)
    }

    private class PresenterUnderTest(
        val presenter: TasksTabPresenter,
        val onOpenRoom: (RoomId) -> Unit,
        val onOpenThread: (RoomId, ThreadId) -> Unit,
        val spaceId: RoomId?,
    ) {
        suspend fun test(block: suspend TurbineTestContext<TasksTabState>.() -> Unit) =
            moleculeFlow(RecompositionMode.Immediate) { presenter.present(onOpenRoom, onOpenThread, spaceId) }.test(validate = block)
    }

    /** The room [client.getRoom] gives for [room], whose board pointer read never answers. */
    private class SilentPointerRoom(room: FakeJoinedRoom) : BaseRoom by room {
        override suspend fun getStateEventsJson(eventType: StateEventType): Result<Map<String, String>> = awaitCancellation()
    }

    private fun TasksTabState.tasksContent() = content as? TasksTabContent.Tasks

    private fun TasksTabState.tasks(): List<TabTask> = tasksContent()?.let { content ->
        content.groups.flatMap { it.tasks } + content.done.takeIf { content.isDoneExpanded }.orEmpty()
    }.orEmpty()

    private fun TasksTabState.titles() = tasks().map { it.task.title }

    private fun TasksTabState.tableSort() = (content as? TasksTabContent.Table)?.sort

    private fun TasksTabState.rowTitles() = (content as? TasksTabContent.Table)?.rows.orEmpty().map { it.task.title }

    private suspend fun TurbineTestContext<TasksTabState>.awaitUntil(predicate: (TasksTabState) -> Boolean): TasksTabState =
        consumeItemsUntilPredicate(predicate = predicate).last()
}
