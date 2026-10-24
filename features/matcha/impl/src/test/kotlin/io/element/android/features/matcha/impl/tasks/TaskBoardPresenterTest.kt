/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.tasks

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.element.android.appconfig.MatchaConfig
import io.element.android.features.matcha.impl.R
import io.element.android.features.matcha.impl.protocol.KanbanProtocol
import io.element.android.features.matcha.impl.protocol.TaskStatus
import io.element.android.features.matcha.impl.store.A_BOARD_ROOT
import io.element.android.features.matcha.impl.store.MatchaRoomStore
import io.element.android.features.matcha.impl.store.aBoardPointerItem
import io.element.android.features.matcha.impl.store.aCardItem
import io.element.android.features.matcha.impl.store.aMatchaEventItem
import io.element.android.features.matcha.impl.store.aMatchaRoomStore
import io.element.android.features.matcha.impl.store.aThreadListItem
import io.element.android.features.matcha.impl.store.settle
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarMessage
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.featureflag.test.FakeFeatureFlagService
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.ThreadId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.MessageEventType
import io.element.android.libraries.matrix.api.room.threads.ThreadListPaginationStatus
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.room.aRoomInfo
import io.element.android.libraries.matrix.test.spaces.FakeSpaceService
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class TaskBoardPresenterTest {
    private val boardRoot = A_BOARD_ROOT
    private val thread = "\$t1"
    private val threadCardId = KanbanProtocol.threadCardId(thread)

    @Test
    fun `nothing is shown when the feature is off`() = runTest {
        aPresenter(aMatchaRoomStore(backgroundScope), isEnabled = false).test {
            assertThat(awaitItem().isVisible).isFalse()
        }
    }

    @Test
    fun `the board is looked for until the threads and the history are read`() = runTest {
        val store = aMatchaRoomStore(backgroundScope, threadsPaginationStatus = ThreadListPaginationStatus.Loading)
        aPresenter(store).test {
            assertThat(awaitUntil { it.isVisible }.content).isEqualTo(TaskBoardContent.LookingForBoard)
        }
    }

    @Test
    fun `a board further back than the first pages is found before setup is offered`() = runTest {
        // Ten pages are read up front; the board's pointer and a card are in the twelfth.
        val olderPages = List(11) { emptyList<MatrixTimelineItem>() } +
            listOf(listOf(aBoardPointerItem(1, boardRoot), aCardItem(2, "c-old", "Old task", TaskStatus.Todo)))
        aPresenter(aMatchaRoomStore(backgroundScope, olderPages = olderPages)).test {
            val states = consumeItemsUntilPredicate { it.board()?.todo?.isNotEmpty() == true }
            assertThat(states.none { it.content is TaskBoardContent.Setup }).isTrue()
            assertThat(states.last().board()!!.todo.single().title).isEqualTo("Old task")
        }
    }

    @Test
    fun `history that can't be read to look for the board offers to try again`() = runTest {
        var fail = false
        val store = aMatchaRoomStore(backgroundScope, olderPages = List(12) { emptyList() }, paginateResult = {
            if (fail) Result.failure(IllegalStateException("offline")) else Result.success(Unit)
        })
        settle()
        fail = true
        aPresenter(store).test {
            val failed = awaitUntil { it.content == TaskBoardContent.LoadFailed }
            fail = false
            failed.eventSink(TaskBoardEvent.RetryLoad)
            assertThat(awaitUntil { it.content is TaskBoardContent.Setup }.content)
                .isEqualTo(TaskBoardContent.Setup(canCreate = true, isCreating = false, createFailed = false))
        }
    }

    @Test
    fun `creating the board sends its root then points the room at it`() = runTest {
        val sent = mutableListOf<String>()
        val state = mutableListOf<Pair<String, String>>()
        val store = aMatchaRoomStore(
            scope = backgroundScope,
            sendRawStateEventResult = { type, _, content ->
                state += type to content
                Result.success(EventId("\$pointer"))
            },
        ) { type, _ ->
            sent += type
            Result.success(EventId("\$root"))
        }
        aPresenter(store).test {
            val setup = awaitUntil { it.content is TaskBoardContent.Setup }
            assertThat(setup.content).isEqualTo(TaskBoardContent.Setup(canCreate = true, isCreating = false, createFailed = false))
            setup.eventSink(TaskBoardEvent.CreateBoard)
            // The board shows once the room points at its root.
            awaitUntil { it.content is TaskBoardContent.Board }
            settle()
            assertThat(sent).containsExactly(MatchaConfig.EVENT_KANBAN_BOARD)
            assertThat(state.single().first).isEqualTo(MatchaConfig.STATE_KANBAN)
            assertThat(state.single().second).contains("\"event_id\":\"\$root\"")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a board created elsewhere meanwhile is shown instead of creating another`() = runTest {
        var pointer: String? = null
        val snackbarDispatcher = SnackbarDispatcher()
        val store = aMatchaRoomStore(
            scope = backgroundScope,
            boardPointerResult = { Result.success(pointer?.let { mapOf("" to """{"version": 1, "event_id": "$it"}""") }.orEmpty()) },
        ) { _, _ -> error("Nothing must be sent") }
        aPresenter(store, snackbarDispatcher = snackbarDispatcher).test {
            val setup = awaitUntil { it.content is TaskBoardContent.Setup }
            pointer = boardRoot
            setup.eventSink(TaskBoardEvent.CreateBoard)
            awaitUntil { it.content is TaskBoardContent.Board }
            assertThat(snackbarDispatcher.snackbarMessage.first()?.messageResId).isEqualTo(R.string.screen_matcha_tasks_already_has_board)
        }
    }

    @Test
    fun `a failed create says so and offers to try again`() = runTest {
        val store = aMatchaRoomStore(backgroundScope) { _, _ -> Result.failure(IllegalStateException("offline")) }
        aPresenter(store).test {
            awaitUntil { it.content is TaskBoardContent.Setup }.eventSink(TaskBoardEvent.CreateBoard)
            val failed = awaitUntil { (it.content as? TaskBoardContent.Setup)?.createFailed == true }
            assertThat((failed.content as TaskBoardContent.Setup).isCreating).isFalse()
        }
    }

    @Test
    fun `a board whose pointer couldn't be set is pointed at again rather than sent twice`() = runTest {
        val sent = mutableListOf<String>()
        val pointers = mutableListOf<String>()
        var failPointer = true
        val store = aMatchaRoomStore(
            scope = backgroundScope,
            sendRawStateEventResult = { _, _, content ->
                pointers += content
                if (failPointer) Result.failure(IllegalStateException("offline")) else Result.success(EventId("\$pointer"))
            },
        ) { type, _ ->
            sent += type
            Result.success(EventId("\$root"))
        }
        aPresenter(store).test {
            awaitUntil { it.content is TaskBoardContent.Setup }.eventSink(TaskBoardEvent.CreateBoard)
            // The root alone is no board, for this client or any other.
            val failed = awaitUntil { (it.content as? TaskBoardContent.Setup)?.createFailed == true }
            assertThat(store.snapshot.value.board).isNull()
            failPointer = false
            failed.eventSink(TaskBoardEvent.CreateBoard)
            awaitUntil { it.content is TaskBoardContent.Board }
            assertThat(sent).containsExactly(MatchaConfig.EVENT_KANBAN_BOARD)
            assertThat(pointers).hasSize(2)
            assertThat(pointers.all { it.contains("\"event_id\":\"\$root\"") }).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `without permission the board can't be created`() = runTest {
        val store = aMatchaRoomStore(backgroundScope, canSendState = false) { _, _ -> error("Nothing must be sent") }
        aPresenter(store).test {
            val setup = awaitUntil { it.content is TaskBoardContent.Setup }
            assertThat((setup.content as TaskBoardContent.Setup).canCreate).isFalse()
            setup.eventSink(TaskBoardEvent.CreateBoard)
            settle()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tasks are split by status and ordered by deadline, with replies after the setup reply`() = runTest {
        val store = aMatchaRoomStore(
            scope = backgroundScope,
            boardRoot = boardRoot,
            threads = listOf(aThreadListItem(thread, body = "Plan"), aThreadListItem("\$t2", body = "")),
            items = listOf(
                aCardItem(1, "c-late", "Late", TaskStatus.Todo, dueAt = A_NOW + 2_000),
                aCardItem(2, "c-soon", "Soon", TaskStatus.Todo, dueAt = A_NOW - 1_000),
                aCardItem(3, "c-none", "No deadline", TaskStatus.Todo),
                aCardItem(4, "c-done", "Done", TaskStatus.Done, dueAt = A_NOW - 1_000),
                aCardItem(5, "c-archived", "Archived", TaskStatus.Doing, archived = true),
                bootstrap(6, thread),
            ),
        )
        aPresenter(store).test {
            val board = awaitUntil { (it.content as? TaskBoardContent.Board)?.archived?.isNotEmpty() == true }.content as TaskBoardContent.Board
            // Without a deadline, threads come newest first and older standalone cards last.
            assertThat(board.todo.map { it.title }).containsExactly("Soon", "Late", "Plan", null, "No deadline").inOrder()
            assertThat(board.todo.first().isOverdue).isTrue()
            assertThat(board.todo.first().deadline).isEqualTo("at ${A_NOW - 1_000}")
            assertThat(board.done.single().isOverdue).isFalse()
            assertThat(board.archived.single().title).isEqualTo("Archived")
            // The named thread's only reply is the hidden one that set it up.
            assertThat(board.todo.first { it.title == "Plan" }.replyCount).isEqualTo(0)
            assertThat(board.todo.first { it.title == null }.replyCount).isEqualTo(1)
            assertThat(board.todo.last().replyCount).isNull()
            assertThat(board.canAdd).isTrue()
        }
    }

    @Test
    fun `adding a task creates its named thread, then its card`() = runTest {
        val sent = mutableListOf<Pair<String, String>>()
        val store = aMatchaRoomStore(backgroundScope, boardRoot = boardRoot) { type, content ->
            sent += type to content
            Result.success(EventId(if (sent.size == 1) "\$root" else "\$e${sent.size}"))
        }
        aPresenter(store).test {
            awaitUntil { (it.content as? TaskBoardContent.Board)?.canAdd == true }.eventSink(TaskBoardEvent.NewTask(TaskStatus.Doing))
            val editor = awaitUntil { it.editor != null }.editor!!
            assertThat(editor.isNew).isTrue()
            assertThat(editor.status).isEqualTo(TaskStatus.Doing)
            editor.eventSink(TaskEditorEvent.SetTitle("  Book the venue "))
            editor.eventSink(TaskEditorEvent.SetDescription("Near the station"))
            awaitUntil { it.editor?.description == "Near the station" }.editor!!.eventSink(TaskEditorEvent.Save)
            awaitUntil { it.editor == null }
            assertThat(sent.map { it.first }).containsExactly("m.room.message", MatchaConfig.EVENT_THREAD_METADATA, MatchaConfig.EVENT_KANBAN_CARD).inOrder()
            assertThat(sent[0].second).isEqualTo("""{"msgtype":"m.text","body":"Book the venue"}""")
            assertThat(sent[1].second).contains("\"rel_type\":\"m.thread\"")
            val card = Json.parseToJsonElement(sent[2].second).jsonObject
            assertThat(card["card_id"]?.jsonPrimitive?.content).isEqualTo(KanbanProtocol.threadCardId("\$root"))
            assertThat(card["thread_id"]?.jsonPrimitive?.content).isEqualTo("\$root")
            assertThat(card["title"]?.jsonPrimitive?.content).isEqualTo("Book the venue")
            assertThat(card["description"]?.jsonPrimitive?.content).isEqualTo("Near the station")
            assertThat(card["column"]?.jsonPrimitive?.content).isEqualTo("doing")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a task name is validated before anything is sent`() = runTest {
        val store = aMatchaRoomStore(backgroundScope, boardRoot = boardRoot) { _, _ -> error("Nothing must be sent") }
        aPresenter(store).test {
            awaitUntil { (it.content as? TaskBoardContent.Board)?.canAdd == true }.eventSink(TaskBoardEvent.NewTask(TaskStatus.Todo))
            val editor = awaitUntil { it.editor != null }.editor!!
            assertThat(editor.titleError).isNull()
            editor.eventSink(TaskEditorEvent.Save)
            assertThat(awaitUntil { it.editor?.titleError != null }.editor!!.titleError).isEqualTo(TaskFieldError.Empty)
            editor.eventSink(TaskEditorEvent.SetTitle("a".repeat(MatchaConfig.THREAD_NAME_MAX_LENGTH + 1)))
            val tooLong = awaitUntil { it.editor?.titleError is TaskFieldError.TooLong }.editor!!
            assertThat(tooLong.titleError).isEqualTo(TaskFieldError.TooLong(MatchaConfig.THREAD_NAME_MAX_LENGTH))
            tooLong.eventSink(TaskEditorEvent.SetDescription("d".repeat(MatchaConfig.CARD_DESCRIPTION_MAX_LENGTH + 1)))
            assertThat(awaitUntil { it.editor?.descriptionError != null }.editor!!.descriptionError)
                .isEqualTo(TaskFieldError.TooLong(MatchaConfig.CARD_DESCRIPTION_MAX_LENGTH))
            tooLong.eventSink(TaskEditorEvent.Save)
            settle()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `editing a task writes a complete revision and renames its thread when the name changed`() = runTest {
        val sent = mutableListOf<Pair<String, String>>()
        val store = aLinkedTaskStore { type, content ->
            sent += type to content
            Result.success(EventId("\$e${sent.size + 10}"))
        }
        aPresenter(store).test {
            awaitUntil { it.board()?.todo?.isNotEmpty() == true }.eventSink(TaskBoardEvent.EditTask(threadCardId))
            val editor = awaitUntil { it.editor != null }.editor!!
            assertThat(editor.isNew).isFalse()
            assertThat(editor.title).isEqualTo("Plan")
            assertThat(editor.description).isEqualTo("Steps")
            editor.eventSink(TaskEditorEvent.SetDescription("More steps"))
            awaitUntil { it.editor?.description == "More steps" }.editor!!.eventSink(TaskEditorEvent.Save)
            awaitUntil { it.editor == null }
            assertThat(sent.map { it.first }).containsExactly(MatchaConfig.EVENT_KANBAN_CARD)
            val unchanged = Json.parseToJsonElement(sent.single().second).jsonObject
            assertThat(unchanged["title"]?.jsonPrimitive?.content).isEqualTo("Plan")
            assertThat(unchanged["due_at"]?.jsonPrimitive?.content).isEqualTo("${A_NOW + 5_000}")

            sent.clear()
            awaitItem().eventSink(TaskBoardEvent.EditTask(threadCardId))
            val renaming = awaitUntil { it.editor != null }.editor!!
            renaming.eventSink(TaskEditorEvent.SetTitle("Launch plan"))
            awaitUntil { it.editor?.title == "Launch plan" }.editor!!.eventSink(TaskEditorEvent.Save)
            awaitUntil { it.editor == null }
            assertThat(sent.map { it.first }).containsExactly(MatchaConfig.EVENT_THREAD_METADATA, MatchaConfig.EVENT_KANBAN_CARD).inOrder()
            assertThat(sent[0].second).contains("\"name\":\"Launch plan\"")
            assertThat(sent[0].second).contains("\"rel_type\":\"m.reference\"")
            assertThat(sent[1].second).contains("\"title\":\"Launch plan\"")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a new task waits for its thread to be set up before its card is written`() = runTest {
        val sent = mutableListOf<String>()
        var failSetup = true
        val store = aMatchaRoomStore(backgroundScope, boardRoot = boardRoot) { type, _ ->
            sent += type
            when {
                type == "m.room.message" -> Result.success(EventId("\$root"))
                type == MatchaConfig.EVENT_THREAD_METADATA && failSetup -> Result.failure(IllegalStateException("offline"))
                else -> Result.success(EventId("\$e${sent.size}"))
            }
        }
        aPresenter(store).test {
            awaitUntil { (it.content as? TaskBoardContent.Board)?.canAdd == true }.eventSink(TaskBoardEvent.NewTask(TaskStatus.Todo))
            awaitUntil { it.editor != null }.editor!!.eventSink(TaskEditorEvent.SetTitle("Book the venue"))
            awaitUntil { it.editor?.title == "Book the venue" }.editor!!.eventSink(TaskEditorEvent.Save)
            val failed = awaitUntil { it.editor?.saveError != null }.editor!!
            assertThat(failed.saveError).isEqualTo(TaskWriteError.ThreadSetupFailed)
            assertThat(sent).containsExactly("m.room.message", MatchaConfig.EVENT_THREAD_METADATA).inOrder()

            failSetup = false
            failed.eventSink(TaskEditorEvent.Save)
            awaitUntil { it.editor == null }
            // The root isn't sent again: the setup reply is, then the card.
            assertThat(sent.drop(2)).containsExactly(MatchaConfig.EVENT_THREAD_METADATA, MatchaConfig.EVENT_KANBAN_CARD).inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an untitled thread's task asks for a name, which also names the thread`() = runTest {
        val sent = mutableListOf<Pair<String, String>>()
        val store = aMatchaRoomStore(backgroundScope, boardRoot = boardRoot, threads = listOf(aThreadListItem(thread, body = ""))) { type, content ->
            sent += type to content
            Result.success(EventId("\$e${sent.size + 10}"))
        }
        aPresenter(store).test {
            awaitUntil { it.board()?.todo?.isNotEmpty() == true }.eventSink(TaskBoardEvent.EditTask(threadCardId))
            val editor = awaitUntil { it.editor != null }.editor!!
            assertThat(editor.title).isEmpty()
            assertThat(editor.hasChanges).isFalse()
            editor.eventSink(TaskEditorEvent.Save)
            assertThat(awaitUntil { it.editor?.titleError != null }.editor!!.titleError).isEqualTo(TaskFieldError.Empty)
            assertThat(sent).isEmpty()
            editor.eventSink(TaskEditorEvent.SetTitle("Plan"))
            awaitUntil { it.editor?.title == "Plan" }.editor!!.eventSink(TaskEditorEvent.Save)
            awaitUntil { it.editor == null }
            assertThat(sent.map { it.first }).containsExactly(MatchaConfig.EVENT_THREAD_METADATA, MatchaConfig.EVENT_KANBAN_CARD).inOrder()
            assertThat(sent[0].second).contains("\"name\":\"Plan\"")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `moving a task whose card is older than the history read keeps what the card says`() = runTest {
        val sent = mutableListOf<String>()
        // The thread started at 100 ms; its card was written at 150 ms, in the eleventh page, older than the ten read up front.
        val olderPages = List(10) { emptyList<MatrixTimelineItem>() } +
            listOf(listOf(aCardItem(150, threadCardId, "Plan", TaskStatus.Doing, threadId = thread, description = "Steps", dueAt = A_NOW + 5_000))) +
            listOf(listOf(aCardItem(50, "c-older", "Older", TaskStatus.Todo)))
        val store = aMatchaRoomStore(
            scope = backgroundScope,
            boardRoot = boardRoot,
            threads = listOf(aThreadListItem(thread, body = "Plan", timestamp = 100)),
            olderPages = olderPages,
        ) { _, content ->
            sent += content
            Result.success(EventId("\$e${sent.size + 1000}"))
        }
        aPresenter(store).test {
            // Until its card is read, the thread's task shows the defaults.
            awaitUntil { it.board()?.todo?.isNotEmpty() == true }.eventSink(TaskBoardEvent.Move(threadCardId, TaskStatus.Done))
            awaitUntil { it.board()?.done?.isNotEmpty() == true }
            val written = Json.parseToJsonElement(sent.single()).jsonObject
            assertThat(written["column"]?.jsonPrimitive?.content).isEqualTo("done")
            assertThat(written["description"]?.jsonPrimitive?.content).isEqualTo("Steps")
            assertThat(written["due_at"]?.jsonPrimitive?.content).isEqualTo("${A_NOW + 5_000}")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `history from before a thread started isn't read for its task`() = runTest {
        var pagesRead = 0
        val sent = mutableListOf<String>()
        val store = aMatchaRoomStore(
            scope = backgroundScope,
            boardRoot = boardRoot,
            threads = listOf(aThreadListItem(thread, body = "Plan", timestamp = 100)),
            // The history read so far already reaches back before the thread started.
            items = listOf(aCardItem(50, "c-older", "Older", TaskStatus.Todo)),
            olderPages = List(12) { emptyList() },
            paginateResult = {
                pagesRead++
                Result.success(Unit)
            },
        ) { _, content ->
            sent += content
            Result.success(EventId("\$e${sent.size + 1000}"))
        }
        aPresenter(store).test {
            awaitUntil { it.board()?.todo?.size == 2 }.eventSink(TaskBoardEvent.Move(threadCardId, TaskStatus.Done))
            awaitUntil { it.board()?.done?.isNotEmpty() == true }
            assertThat(sent.single()).contains("\"column\":\"done\"")
            assertThat(pagesRead).isEqualTo(MatchaRoomStore.INITIAL_HISTORY_PAGES)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a task whose history can't be read isn't written over`() = runTest {
        var fail = false
        val snackbarDispatcher = SnackbarDispatcher()
        val store = aMatchaRoomStore(
            scope = backgroundScope,
            boardRoot = boardRoot,
            threads = listOf(aThreadListItem(thread, body = "Plan", timestamp = 100)),
            olderPages = List(12) { emptyList() },
            paginateResult = { if (fail) Result.failure(IllegalStateException("offline")) else Result.success(Unit) },
        ) { _, _ -> error("Nothing must be sent") }
        aPresenter(store, snackbarDispatcher = snackbarDispatcher).test {
            val state = awaitUntil { it.board()?.todo?.isNotEmpty() == true }
            fail = true
            state.eventSink(TaskBoardEvent.Move(threadCardId, TaskStatus.Done))
            val failure = snackbarDispatcher.snackbarMessage.first { it != null }!!
            assertThat(failure.messageResId).isEqualTo(R.string.screen_matcha_tasks_history_incomplete)
            assertThat(failure.actionResId).isEqualTo(CommonStrings.action_retry)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `moving a task writes its new status`() = runTest {
        val sent = mutableListOf<String>()
        val store = aLinkedTaskStore { _, content ->
            sent += content
            Result.success(EventId("\$e${sent.size + 10}"))
        }
        aPresenter(store).test {
            awaitUntil { it.board()?.todo?.isNotEmpty() == true }.eventSink(TaskBoardEvent.Move(threadCardId, TaskStatus.Done))
            val moved = awaitUntil { it.board()?.done?.isNotEmpty() == true }
            assertThat(moved.board()!!.done.single().id).isEqualTo(threadCardId)
            assertThat(sent.single()).contains("\"column\":\"done\"")
            assertThat(sent.single()).contains("\"description\":\"Steps\"")
            // Moving to the current status sends nothing.
            moved.eventSink(TaskBoardEvent.Move(threadCardId, TaskStatus.Done))
            settle()
            assertThat(sent).hasSize(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `archiving a thread's task archives the thread too, and Undo restores both`() = runTest {
        val sent = mutableListOf<Pair<String, String>>()
        val snackbarDispatcher = SnackbarDispatcher()
        val store = aLinkedTaskStore { type, content ->
            sent += type to content
            Result.success(EventId("\$e${sent.size + 10}"))
        }
        aPresenter(store, snackbarDispatcher = snackbarDispatcher).test {
            awaitUntil { it.board()?.todo?.isNotEmpty() == true }.eventSink(TaskBoardEvent.SetArchived(threadCardId, archived = true))
            awaitUntil { it.board()?.archived?.isNotEmpty() == true }
            settle()
            assertThat(sent.map { it.first }).containsExactly(MatchaConfig.EVENT_THREAD_METADATA, MatchaConfig.EVENT_KANBAN_CARD).inOrder()
            assertThat(sent.map { it.second }.all { it.contains("\"archived\":true") }).isTrue()
            val snackbar = snackbarDispatcher.snackbarMessage.first { it != null }!!
            assertThat(snackbar.messageResId).isEqualTo(R.string.screen_matcha_tasks_archived_snackbar)
            assertThat(snackbar.actionResId).isEqualTo(CommonStrings.action_matcha_undo)

            snackbarDispatcher.clear()
            snackbar.action()
            awaitUntil { it.board()?.archived?.isEmpty() == true && it.board()?.todo?.isNotEmpty() == true }
            settle()
            assertThat(sent).hasSize(4)
            assertThat(sent.drop(2).map { it.second }.all { it.contains("\"archived\":false") }).isTrue()
            // Undo says nothing more.
            assertThat(snackbarDispatcher.snackbarMessage.first()).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `unarchiving shows the archived tasks again with Undo`() = runTest {
        val snackbarDispatcher = SnackbarDispatcher()
        val store = aMatchaRoomStore(
            scope = backgroundScope,
            boardRoot = boardRoot,
            items = listOf(aCardItem(1, "c-legacy", "Legacy", TaskStatus.Todo, archived = true)),
        )
        aPresenter(store, snackbarDispatcher = snackbarDispatcher).test {
            awaitUntil { it.board()?.archived?.isNotEmpty() == true }.eventSink(TaskBoardEvent.ShowArchived)
            val archived = awaitUntil { it.showArchived }
            archived.eventSink(TaskBoardEvent.SetArchived("c-legacy", archived = false))
            awaitUntil { it.board()?.todo?.isNotEmpty() == true }
            val snackbar = snackbarDispatcher.snackbarMessage.first { it != null }!!
            assertThat(snackbar.messageResId).isEqualTo(R.string.screen_matcha_tasks_unarchived_snackbar)
            assertThat(snackbar.actionResId).isEqualTo(CommonStrings.action_matcha_undo)
            archived.eventSink(TaskBoardEvent.HideArchived)
            assertThat(awaitUntil { !it.showArchived }.showArchived).isFalse()
        }
    }

    @Test
    fun `a failed write offers to retry the same write`() = runTest {
        var fail = true
        val sent = mutableListOf<String>()
        val snackbarDispatcher = SnackbarDispatcher()
        val store = aLinkedTaskStore { _, content ->
            sent += content
            if (fail) Result.failure(IllegalStateException("offline")) else Result.success(EventId("\$e${sent.size + 10}"))
        }
        aPresenter(store, snackbarDispatcher = snackbarDispatcher).test {
            awaitUntil { it.board()?.todo?.isNotEmpty() == true }.eventSink(TaskBoardEvent.Move(threadCardId, TaskStatus.Doing))
            val failure: SnackbarMessage = snackbarDispatcher.snackbarMessage.first { it != null }!!
            assertThat(failure.messageResId).isEqualTo(CommonStrings.common_matcha_write_failed)
            assertThat(failure.actionResId).isEqualTo(CommonStrings.action_retry)
            fail = false
            failure.action()
            awaitUntil { it.board()?.doing?.isNotEmpty() == true }
            assertThat(sent.last()).contains("\"column\":\"doing\"")
        }
    }

    @Test
    fun `a read-only board changes nothing`() = runTest {
        val store = aLinkedTaskStore(canSendMessage = { it != MessageEventType.Other(MatchaConfig.EVENT_KANBAN_CARD) }) { _, _ ->
            error("Nothing must be sent")
        }
        aPresenter(store).test {
            val state = awaitUntil { it.board()?.todo?.isNotEmpty() == true }
            val board = state.board()!!
            assertThat(board.canEdit).isFalse()
            assertThat(board.canAdd).isFalse()
            assertThat(board.canArchive(board.todo.single())).isFalse()
            state.eventSink(TaskBoardEvent.Move(threadCardId, TaskStatus.Done))
            state.eventSink(TaskBoardEvent.SetArchived(threadCardId, archived = true))
            state.eventSink(TaskBoardEvent.NewTask(TaskStatus.Todo))
            state.eventSink(TaskBoardEvent.EditTask(threadCardId))
            settle()
            state.eventSink(TaskBoardEvent.ShowActions(threadCardId))
            assertThat(awaitUntil { it.actionsSheet != null }.editor).isNull()
        }
    }

    @Test
    fun `the board opens the editor for the thread it was opened for`() = runTest {
        aPresenter(aLinkedTaskStore(), initialThreadId = ThreadId(thread)).test {
            val editor = awaitUntil { it.editor != null }.editor!!
            assertThat(editor.title).isEqualTo("Plan")
            editor.eventSink(TaskEditorEvent.Close)
            awaitUntil { it.editor == null }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `tapping a task opens its thread, and an older card without one opens the editor`() = runTest {
        val opened = mutableListOf<ThreadId>()
        val store = aLinkedTaskStore(extraItems = listOf(aCardItem(9, "c-legacy", "Legacy", TaskStatus.Todo)))
        aPresenter(store, openThread = { opened += it }).test {
            val state = awaitUntil { it.board()?.todo?.size == 2 }
            state.eventSink(TaskBoardEvent.OpenTask(threadCardId))
            assertThat(opened).containsExactly(ThreadId(thread))
            state.eventSink(TaskBoardEvent.OpenTask("c-legacy"))
            val editor = awaitUntil { it.editor != null }.editor!!
            assertThat(editor.isLinked).isFalse()
            assertThat(editor.titleMaxLength).isEqualTo(MatchaConfig.CARD_TITLE_MAX_LENGTH)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `remind me is about the task, its thread and its deadline`() = runTest {
        aPresenter(aLinkedTaskStore()).test {
            awaitUntil { it.board()?.todo?.isNotEmpty() == true }.eventSink(TaskBoardEvent.RemindMe(threadCardId))
            val target = awaitUntil { it.reminderTarget != null }.reminderTarget!!
            assertThat(target.message).isEqualTo("Plan")
            assertThat(target.threadId).isEqualTo(ThreadId(thread))
            assertThat(target.dueAt).isEqualTo(A_NOW + 5_000)
            assertThat(target.cardId).isEqualTo(threadCardId)
        }
    }

    @Test
    fun `the deadline is picked, warned about when past, and cleared`() = runTest {
        aPresenter(aLinkedTaskStore()).test {
            awaitUntil { it.board()?.todo?.isNotEmpty() == true }.eventSink(TaskBoardEvent.EditTask(threadCardId))
            val editor = awaitUntil { it.editor != null }.editor!!
            assertThat(editor.deadlineError).isNull()
            val past = LocalDateTime.of(2020, 1, 1, 9, 0)
            editor.eventSink(TaskEditorEvent.SetDeadline(past))
            val warned = awaitUntil { it.editor?.deadlineError != null }.editor!!
            assertThat(warned.dueAt).isEqualTo(past.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
            assertThat(warned.deadlineError).isEqualTo(TaskDeadlineError.Past)
            warned.eventSink(TaskEditorEvent.ClearDeadline)
            val cleared = awaitUntil { it.editor?.dueAt == null }.editor!!
            assertThat(cleared.deadline).isNull()
            assertThat(cleared.hasChanges).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `closing with changes asks before discarding them`() = runTest {
        aPresenter(aLinkedTaskStore()).test {
            awaitUntil { it.board()?.todo?.isNotEmpty() == true }.eventSink(TaskBoardEvent.EditTask(threadCardId))
            awaitUntil { it.editor != null }.editor!!.eventSink(TaskEditorEvent.SetDescription("Changed"))
            awaitUntil { it.editor?.hasChanges == true }.editor!!.eventSink(TaskEditorEvent.Close)
            val asking = awaitUntil { it.editor?.showDiscardDialog == true }.editor!!
            asking.eventSink(TaskEditorEvent.KeepEditing)
            awaitUntil { it.editor?.showDiscardDialog == false }.editor!!.eventSink(TaskEditorEvent.Close)
            awaitUntil { it.editor?.showDiscardDialog == true }.editor!!.eventSink(TaskEditorEvent.Discard)
            awaitUntil { it.editor == null }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a task that isn't shared with the chat is only on the boards of the people it involves`() = runTest {
        val store = aMatchaRoomStore(
            scope = backgroundScope,
            boardRoot = boardRoot,
            threads = listOf(
                aThreadListItem("\$bobs", body = "Bob's"),
                aThreadListItem("\$shared", body = "Shared"),
                aThreadListItem("\$mine", body = "Mine", senderId = A_SESSION_ID),
            ),
            items = listOf(
                aCardItem(1, KanbanProtocol.threadCardId("\$bobs"), "Bob's", TaskStatus.Todo, threadId = "\$bobs", showToRoom = false),
                aCardItem(2, KanbanProtocol.threadCardId("\$shared"), "Shared", TaskStatus.Todo, threadId = "\$shared"),
                aCardItem(3, KanbanProtocol.threadCardId("\$mine"), "Mine", TaskStatus.Doing, threadId = "\$mine", showToRoom = false),
                aCardItem(4, "c-bobs", "Bob's older card", TaskStatus.Todo, showToRoom = false),
                aCardItem(5, "c-mine", "My older card", TaskStatus.Todo, showToRoom = false, sender = A_SESSION_ID.value),
            ),
        )
        aPresenter(store).test {
            val board = awaitUntil { it.board()?.doing?.isNotEmpty() == true }.board()!!
            // Hidden tasks are left out of the counts too.
            assertThat(board.todo.map { it.title }).containsExactly("Shared", "My older card")
            assertThat(board.doing.map { it.title }).containsExactly("Mine")
            assertThat(board.todo.single { it.title == "Shared" }.isSharedWithChat).isTrue()
            assertThat(board.doing.single().isSharedWithChat).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a new task is only for the people it involves until shared, and an edit keeps what the card says`() = runTest {
        val sent = mutableListOf<Pair<String, String>>()
        val store = aLinkedTaskStore { type, content ->
            sent += type to content
            Result.success(EventId(if (sent.size == 1) "\$root" else "\$e${sent.size + 10}"))
        }
        aPresenter(store).test {
            awaitUntil { it.board()?.canAdd == true }.eventSink(TaskBoardEvent.NewTask(TaskStatus.Todo))
            val editor = awaitUntil { it.editor != null }.editor!!
            assertThat(editor.showToRoom).isFalse()
            editor.eventSink(TaskEditorEvent.SetTitle("Book the venue"))
            awaitUntil { it.editor?.title == "Book the venue" }.editor!!.eventSink(TaskEditorEvent.Save)
            awaitUntil { it.editor == null }
            assertThat(Json.parseToJsonElement(sent.last().second).jsonObject["show_to_room"]?.jsonPrimitive?.content).isEqualTo("false")
            // The user's new task stays on their board: they started its thread.
            val withTask = awaitUntil { it.board()?.todo?.any { task -> task.title == "Book the venue" } == true }
            assertThat(withTask.board()!!.todo).hasSize(2)

            sent.clear()
            withTask.eventSink(TaskBoardEvent.EditTask(threadCardId))
            val editing = awaitUntil { it.editor != null }.editor!!
            assertThat(editing.showToRoom).isTrue()
            editing.eventSink(TaskEditorEvent.SetShowToRoom(false))
            val changed = awaitUntil { it.editor?.showToRoom == false }.editor!!
            assertThat(changed.hasChanges).isTrue()
            changed.eventSink(TaskEditorEvent.Save)
            awaitUntil { it.editor == null }
            val written = Json.parseToJsonElement(sent.single().second).jsonObject
            assertThat(written["show_to_room"]?.jsonPrimitive?.content).isEqualTo("false")
            assertThat(written["description"]?.jsonPrimitive?.content).isEqualTo("Steps")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a task left off the board still opens from its thread, and its edit keeps what the card says`() = runTest {
        val sent = mutableListOf<String>()
        val store = aMatchaRoomStore(
            scope = backgroundScope,
            boardRoot = boardRoot,
            threads = listOf(aThreadListItem(thread, body = "Plan")),
            items = listOf(aCardItem(1, threadCardId, "Plan", TaskStatus.Todo, threadId = thread, description = "Steps", showToRoom = false)),
        ) { _, content ->
            sent += content
            Result.success(EventId("\$e${sent.size + 10}"))
        }
        aPresenter(store, initialThreadId = ThreadId(thread)).test {
            val opened = awaitUntil { it.editor != null }
            assertThat(opened.board()!!.todo).isEmpty()
            opened.editor!!.eventSink(TaskEditorEvent.SetDescription("More steps"))
            awaitUntil { it.editor?.description == "More steps" }.editor!!.eventSink(TaskEditorEvent.Save)
            awaitUntil { it.editor == null }.eventSink(TaskBoardEvent.EditThreadTask(ThreadId(thread)))
            assertThat(awaitUntil { it.editor != null }.editor!!.title).isEqualTo("Plan")
            val written = Json.parseToJsonElement(sent.single()).jsonObject
            assertThat(written["show_to_room"]?.jsonPrimitive?.content).isEqualTo("false")
            assertThat(written["description"]?.jsonPrimitive?.content).isEqualTo("More steps")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `in a direct chat every task is shown and the switch isn't offered`() = runTest {
        val store = aMatchaRoomStore(
            scope = backgroundScope,
            boardRoot = boardRoot,
            threads = listOf(aThreadListItem(thread, body = "Plan")),
            items = listOf(aCardItem(1, threadCardId, "Plan", TaskStatus.Todo, threadId = thread, showToRoom = false)),
            roomInfo = aRoomInfo(isDirect = true, heroes = listOf(MatrixUser(UserId("@bob:x")))),
        )
        aPresenter(store).test {
            val state = awaitUntil { it.board()?.todo?.isNotEmpty() == true }
            assertThat(state.board()!!.todo.single().isSharedWithChat).isFalse()
            state.eventSink(TaskBoardEvent.EditTask(threadCardId))
            val editor = awaitUntil { it.editor != null }.editor!!
            assertThat(editor.showToRoom).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    /** A board with one thread, "Plan", whose task has a description and a deadline. */
    private fun TestScope.aLinkedTaskStore(
        canSendMessage: (MessageEventType) -> Boolean = { true },
        extraItems: List<MatrixTimelineItem> = emptyList(),
        sendRawEventResult: (String, String) -> Result<EventId> = { _, _ -> Result.success(EventId("\$sent")) },
    ) = aMatchaRoomStore(
        scope = backgroundScope,
        boardRoot = boardRoot,
        canSendMessage = canSendMessage,
        threads = listOf(aThreadListItem(thread, body = "Plan")),
        items = listOf(aCardItem(1, threadCardId, "Plan", TaskStatus.Todo, threadId = thread, description = "Steps", dueAt = A_NOW + 5_000)) + extraItems,
        sendRawEventResult = sendRawEventResult,
    )

    private fun bootstrap(index: Int, threadId: String) = aMatchaEventItem(
        index,
        MatchaConfig.EVENT_THREAD_METADATA,
        """{"version": 1, "name": "Plan", "archived": false, "m.relates_to": {"rel_type": "m.thread", "event_id": "$threadId", "is_falling_back": true}}""",
    )

    private fun TaskBoardState.board() = content as? TaskBoardContent.Board

    private fun TestScope.aPresenter(
        store: MatchaRoomStore,
        isEnabled: Boolean = true,
        initialThreadId: ThreadId? = null,
        openThread: (ThreadId) -> Unit = {},
        snackbarDispatcher: SnackbarDispatcher = SnackbarDispatcher(),
    ) = TaskBoardPresenter(
        initialThreadId = initialThreadId,
        openThread = openThread,
        room = FakeJoinedRoom(),
        store = store,
        spaceService = FakeSpaceService(joinedParentsResult = { Result.success(emptyList()) }),
        featureFlagService = FakeFeatureFlagService(mapOf(FeatureFlags.MatchaWorkspaces.key to isEnabled)),
        snackbarDispatcher = snackbarDispatcher,
        deadlineFormatter = aDeadlineFormatter,
        clock = aFixedClock,
        editorPresenterFactory = aTaskEditorPresenterFactory(),
        roomCoroutineScope = backgroundScope,
    )

    private suspend fun TaskBoardPresenter.test(block: suspend TurbineTestContext<TaskBoardState>.() -> Unit) =
        moleculeFlow(RecompositionMode.Immediate) { present() }.test(validate = block)

    private suspend fun TurbineTestContext<TaskBoardState>.awaitUntil(predicate: (TaskBoardState) -> Boolean): TaskBoardState =
        consumeItemsUntilPredicate(predicate = predicate).last()
}
