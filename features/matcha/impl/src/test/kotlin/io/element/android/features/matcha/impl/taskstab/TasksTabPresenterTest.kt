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
import io.element.android.features.matcha.impl.protocol.CardRevision
import io.element.android.features.matcha.impl.protocol.KanbanProtocol
import io.element.android.features.matcha.impl.protocol.TaskStatus
import io.element.android.features.matcha.impl.store.aMatchaEventItem
import io.element.android.features.matcha.impl.store.aMatchaJoinedRoom
import io.element.android.features.matcha.impl.store.aThreadListItem
import io.element.android.features.matcha.impl.tasks.A_NOW
import io.element.android.features.matcha.impl.tasks.TaskEditorEvent
import io.element.android.features.matcha.impl.tasks.aDeadlineFormatter
import io.element.android.features.matcha.impl.tasks.aFixedClock
import io.element.android.features.matcha.impl.tasks.aTaskEditorPresenterFactory
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.featureflag.test.FakeFeatureFlagService
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.ThreadId
import io.element.android.libraries.matrix.api.room.CurrentUserMembership
import io.element.android.libraries.matrix.api.room.MessageEventType
import io.element.android.libraries.matrix.api.room.threads.ThreadListPaginationStatus
import io.element.android.libraries.matrix.api.roomlist.RoomList
import io.element.android.libraries.matrix.api.roomlist.RoomSummary
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.room.aRoomSummary
import io.element.android.libraries.matrix.test.roomlist.FakeDynamicRoomList
import io.element.android.libraries.matrix.test.roomlist.FakeRoomListService
import io.element.android.services.appnavstate.test.FakeAppNavigationStateService
import io.element.android.services.appnavstate.test.aNavigationState
import io.element.android.services.appnavstate.test.anAppNavigationState
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TasksTabPresenterTest {
    private val design = RoomId("!design:matrix.org")
    private val launch = RoomId("!launch:matrix.org")
    private val chat = RoomId("!chat:matrix.org")
    private val boardRoot = "\$board"

    private val zone = ZoneId.systemDefault()
    private val today = Instant.ofEpochMilli(A_NOW).atZone(zone).toLocalDate()

    private fun at(date: LocalDate, hour: Int, minute: Int = 0) = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

    /** Runs the work launched in the session scope, which is the test's background scope. */
    private fun TestScope.settle() {
        testScheduler.advanceTimeBy(1_000)
        testScheduler.runCurrent()
    }

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
    fun `leaving a chat drops its tasks at once, and leaving the tab lets every chat go`() = runTest {
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
        var pointer = Result.failure<Map<String, String>>(IllegalStateException("offline"))
        val room = aMatchaJoinedRoom(design, backgroundScope, boardPointerResult = { pointer })
        aPresenter(aClient(listOf(design to room))).test {
            val failed = awaitUntil { it.failedCount == 1 }
            assertThat(failed.content).isEqualTo(TasksTabContent.Empty)
            pointer = Result.success(mapOf("" to """{"version": 1, "event_id": "$boardRoot"}"""))
            failed.eventSink(TasksTabEvent.Retry)
            assertThat(awaitUntil { it.failedCount == 0 && it.checkingCount == 0 }.content).isEqualTo(TasksTabContent.Empty)
            cancelAndIgnoreRemainingEvents()
        }
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

    private data class TestCard(
        val index: Int,
        val id: String,
        val title: String,
        val status: TaskStatus,
        val threadId: String?,
        val dueAt: Long?,
        val archived: Boolean,
    )

    /** A chat with a board holding [cards], each linked card with its thread. */
    private fun TestScope.aBoardRoom(
        roomId: RoomId,
        vararg cards: TestCard,
        canSendMessage: (MessageEventType) -> Boolean = { true },
        sendRawEventResult: (String, String) -> Result<EventId> = { _, _ -> Result.success(EventId("\$sent")) },
    ) = aMatchaJoinedRoom(
        roomId = roomId,
        roomCoroutineScope = backgroundScope,
        boardRoot = boardRoot,
        items = cards.map { it.item() },
        threads = cards.mapNotNull { card -> card.threadId?.let { aThreadListItem(it, body = card.title, timestamp = card.index.toLong()) } },
        canSendMessage = canSendMessage,
        sendRawEventResult = sendRawEventResult,
    )

    private fun linkedCard(
        index: Int,
        threadId: String,
        title: String,
        status: TaskStatus = TaskStatus.Todo,
        dueAt: Long? = null,
        archived: Boolean = false,
    ) = TestCard(index, KanbanProtocol.threadCardId(threadId), title, status, threadId, dueAt, archived)

    private fun legacyCard(index: Int, title: String) = TestCard(index, "legacy", title, TaskStatus.Todo, threadId = null, dueAt = null, archived = false)

    private fun TestCard.item() = aMatchaEventItem(
        index,
        MatchaConfig.EVENT_KANBAN_CARD,
        KanbanProtocol.cardContent(
            CardRevision(
                cardId = id,
                title = title,
                description = "",
                status = status,
                archived = archived,
                threadId = threadId,
                dueAt = dueAt,
                reminderRequested = null,
                showToRoom = true,
            ),
            boardRoot,
        ),
    )

    private fun aSummary(roomId: RoomId, membership: CurrentUserMembership = CurrentUserMembership.JOINED): RoomSummary =
        aRoomSummary(roomId = roomId, name = roomId.value.removePrefix("!").substringBefore(":"), currentUserMembership = membership)

    private fun TestScope.aClient(
        rooms: List<Pair<RoomId, FakeJoinedRoom>>,
        summaries: MutableStateFlow<List<RoomSummary>> = MutableStateFlow(rooms.map { aSummary(it.first) }),
    ): FakeMatrixClient = FakeMatrixClient(
        sessionCoroutineScope = backgroundScope,
        roomListService = FakeRoomListService(
            allRooms = FakeDynamicRoomList(summaries = summaries, loadingState = MutableStateFlow(RoomList.LoadingState.Loaded(rooms.size))),
        ),
    ).apply { rooms.forEach { (roomId, room) -> givenGetRoomResult(roomId, room) } }

    private fun TestScope.aPresenter(
        client: FakeMatrixClient,
        isEnabled: Boolean = true,
        navigation: FakeAppNavigationStateService = FakeAppNavigationStateService(),
        onOpenRoom: (RoomId) -> Unit = {},
        onOpenThread: (RoomId, ThreadId) -> Unit = { _, _ -> },
    ): PresenterUnderTest {
        val snackbarDispatcher = SnackbarDispatcher()
        val presenter = TasksTabPresenter(
            client = client,
            featureFlagService = FakeFeatureFlagService(mapOf(FeatureFlags.MatchaWorkspaces.key to isEnabled)),
            appNavigationStateService = navigation,
            snackbarDispatcher = snackbarDispatcher,
            deadlineFormatter = aDeadlineFormatter,
            clock = aFixedClock,
            editorPresenterFactory = aTaskEditorPresenterFactory(),
        )
        return PresenterUnderTest(presenter, onOpenRoom, onOpenThread)
    }

    private class PresenterUnderTest(
        val presenter: TasksTabPresenter,
        val onOpenRoom: (RoomId) -> Unit,
        val onOpenThread: (RoomId, ThreadId) -> Unit,
    ) {
        suspend fun test(block: suspend TurbineTestContext<TasksTabState>.() -> Unit) =
            moleculeFlow(RecompositionMode.Immediate) { presenter.present(onOpenRoom, onOpenThread) }.test(validate = block)
    }

    private fun TasksTabState.tasksContent() = content as? TasksTabContent.Tasks

    private fun TasksTabState.tasks(): List<TabTask> = tasksContent()?.let { content ->
        content.groups.flatMap { it.tasks } + content.done.takeIf { content.isDoneExpanded }.orEmpty()
    }.orEmpty()

    private fun TasksTabState.titles() = tasks().map { it.task.title }

    private suspend fun TurbineTestContext<TasksTabState>.awaitUntil(predicate: (TasksTabState) -> Boolean): TasksTabState =
        consumeItemsUntilPredicate(predicate = predicate).last()
}
