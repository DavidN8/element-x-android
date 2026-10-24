/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.chatbar

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.element.android.appconfig.MatchaConfig
import io.element.android.features.matcha.api.MatchaChatBarActions
import io.element.android.features.matcha.api.MatchaChatBarSelection
import io.element.android.features.matcha.impl.R
import io.element.android.features.matcha.impl.store.MatchaRoomStore
import io.element.android.features.matcha.impl.store.MatchaWarning
import io.element.android.features.matcha.impl.store.aMatchaEventItem
import io.element.android.features.matcha.impl.store.aMatchaRoomStore
import io.element.android.features.matcha.impl.store.aThreadListItem
import io.element.android.features.matcha.impl.tasks.TaskEditorEvent
import io.element.android.features.matcha.impl.tasks.aTaskEditorPresenterFactory
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.featureflag.test.FakeFeatureFlagService
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.ThreadId
import io.element.android.libraries.matrix.api.room.MessageEventType
import io.element.android.libraries.matrix.api.room.threads.ThreadListPaginationStatus
import io.element.android.libraries.matrix.api.timeline.item.event.UnableToDecryptContent
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.consumeItemsUntilTimeout
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ChatBarPresenterTest {
    private val boardRoot = "\$board"

    /** Runs work launched in the room scope, which is the test's background scope. */
    private fun TestScope.settle() {
        testScheduler.advanceTimeBy(1_000)
        testScheduler.runCurrent()
    }

    @Test
    fun `nothing is shown when the feature is off`() = runTest {
        val presenter = aPresenter(store = aMatchaRoomStore(backgroundScope), isEnabled = false)
        presenter.test {
            assertThat(awaitItem().isVisible).isFalse()
        }
    }

    @Test
    fun `thread chips wait for the metadata to load`() = runTest {
        val store = aMatchaRoomStore(
            scope = backgroundScope,
            threads = listOf(aThreadListItem("\$t1")),
            threadsPaginationStatus = ThreadListPaginationStatus.Loading,
        )
        aPresenter(store).test {
            val state = awaitUntil { it.isVisible }
            assertThat(state.threads).isEmpty()
        }
    }

    @Test
    fun `up to four active threads are shown, newest activity first`() = runTest {
        val store = aMatchaRoomStore(
            scope = backgroundScope,
            threads = listOf(
                aThreadListItem("\$t1", timestamp = 1),
                aThreadListItem("\$t2", timestamp = 2),
                aThreadListItem("\$t3", timestamp = 3),
                aThreadListItem("\$t4", timestamp = 4),
                aThreadListItem("\$t5", timestamp = 5),
                aThreadListItem("\$t6", timestamp = 6),
            ),
            items = listOf(archivePatch(1, "\$t6")),
        )
        aPresenter(store).test {
            val state = awaitUntil { it.threads.isNotEmpty() }
            assertThat(state.threads.map { it.id.value }).containsExactly("\$t5", "\$t4", "\$t3", "\$t2").inOrder()
            assertThat(state.threads.first().name).isEqualTo("Root of \$t5")
            assertThat(state.canCreateThread).isTrue()
            assertThat(state.showMore).isTrue()
        }
    }

    @Test
    fun `the open thread gets its own chip when it is not among the active ones`() = runTest {
        val store = aMatchaRoomStore(
            scope = backgroundScope,
            threads = listOf(aThreadListItem("\$t1"), aThreadListItem("\$old", timestamp = 1)),
            items = listOf(archivePatch(1, "\$old")),
        )
        aPresenter(store, selection = MatchaChatBarSelection.Thread(ThreadId("\$old"))).test {
            val state = awaitUntil { it.threads.size == 2 }
            assertThat(state.threads.last().id).isEqualTo(ThreadId("\$old"))
            assertThat(state.threads.last().isArchived).isTrue()
        }
    }

    @Test
    fun `an unreadable name shows the fallback and the warning`() = runTest {
        val store = aMatchaRoomStore(
            scope = backgroundScope,
            threads = listOf(aThreadListItem("\$t1", content = UnableToDecryptContent(UnableToDecryptContent.Data.Unknown, threadInfo = null))),
        )
        aPresenter(store).test {
            val state = awaitUntil { it.threads.isNotEmpty() }
            assertThat(state.threads.single().name).isNull()
            assertThat(state.warning).isEqualTo(MatchaWarning.Undecryptable)
        }
    }

    @Test
    fun `tapping a chip navigates, tapping the selected one does nothing`() = runTest {
        val actions = FakeMatchaChatBarActions()
        aPresenter(aMatchaRoomStore(backgroundScope), actions = actions).test {
            val state = awaitUntil { it.isVisible }
            state.eventSink(ChatBarEvent.Select(MatchaChatBarSelection.General))
            state.eventSink(ChatBarEvent.Select(MatchaChatBarSelection.Tasks))
            state.eventSink(ChatBarEvent.Select(MatchaChatBarSelection.Thread(ThreadId("\$t1"))))
            state.eventSink(ChatBarEvent.OpenSpaceDocs)
            assertThat(actions.calls).containsExactly("tasks", "thread \$t1", "docs").inOrder()
        }
    }

    @Test
    fun `space docs are offered only when the chat has a space`() = runTest {
        aPresenter(aMatchaRoomStore(backgroundScope), actions = FakeMatchaChatBarActions(hasSpaceDocs = false)).test {
            assertThat(awaitUntil { it.isVisible }.canOpenSpaceDocs).isFalse()
        }
    }

    @Test
    fun `creating a thread sends the root then names it, and opens it`() = runTest {
        val sent = mutableListOf<Pair<String, String>>()
        val actions = FakeMatchaChatBarActions()
        val store = aMatchaRoomStore(backgroundScope) { type, content ->
            sent += type to content
            Result.success(EventId(if (sent.size == 1) "\$root" else "\$reply"))
        }
        aPresenter(store, actions = actions).test {
            awaitUntil { it.canCreateThread }.eventSink(ChatBarEvent.NewThread)
            awaitUntil { it.sheet is ChatBarSheet.ThreadName }.eventSink(ChatBarEvent.UpdateThreadName("  Launch plan "))
            awaitUntil { (it.sheet as? ChatBarSheet.ThreadName)?.name == "  Launch plan " }.eventSink(ChatBarEvent.SubmitThreadName)
            awaitUntil { it.sheet == null }
            assertThat(sent.map { it.first }).containsExactly("m.room.message", MatchaConfig.EVENT_THREAD_METADATA).inOrder()
            assertThat(sent[0].second).isEqualTo("""{"msgtype":"m.text","body":"Launch plan"}""")
            assertThat(sent[1].second).contains("\"rel_type\":\"m.thread\"")
            assertThat(sent[1].second).contains("\"name\":\"Launch plan\"")
            assertThat(actions.calls).containsExactly("thread \$root")
        }
    }

    @Test
    fun `a thread that could not be set up opens anyway and offers to finish setting it up`() = runTest {
        val actions = FakeMatchaChatBarActions()
        val snackbarDispatcher = SnackbarDispatcher()
        val sent = mutableListOf<String>()
        val store = aMatchaRoomStore(backgroundScope) { type, content ->
            sent += content
            when (sent.size) {
                1 -> Result.success(EventId("\$root"))
                2 -> Result.failure(IllegalStateException("offline"))
                else -> Result.success(EventId("\$reply"))
            }
        }
        aPresenter(store, actions = actions, snackbarDispatcher = snackbarDispatcher).test {
            awaitUntil { it.canCreateThread }.eventSink(ChatBarEvent.NewThread)
            awaitUntil { it.sheet is ChatBarSheet.ThreadName }.eventSink(ChatBarEvent.UpdateThreadName("Plan"))
            awaitUntil { (it.sheet as? ChatBarSheet.ThreadName)?.name == "Plan" }.eventSink(ChatBarEvent.SubmitThreadName)
            awaitUntil { it.sheet == null }
            assertThat(actions.calls).containsExactly("thread \$root")
            val snackbar = snackbarDispatcher.snackbarMessage.first()!!
            assertThat(snackbar.messageResId).isEqualTo(R.string.screen_matcha_threads_setup_failed)
            assertThat(snackbar.actionResId).isEqualTo(CommonStrings.action_retry)

            snackbar.action()
            settle()
            assertThat(sent).hasSize(3)
            assertThat(sent.last()).isEqualTo(sent[1])
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed create keeps the sheet open with an error`() = runTest {
        val store = aMatchaRoomStore(backgroundScope) { _, _ -> Result.failure(IllegalStateException("offline")) }
        aPresenter(store).test {
            awaitUntil { it.canCreateThread }.eventSink(ChatBarEvent.NewThread)
            awaitUntil { it.sheet is ChatBarSheet.ThreadName }.eventSink(ChatBarEvent.UpdateThreadName("Plan"))
            awaitUntil { (it.sheet as? ChatBarSheet.ThreadName)?.name == "Plan" }.eventSink(ChatBarEvent.SubmitThreadName)
            val sheet = awaitUntil { (it.sheet as? ChatBarSheet.ThreadName)?.error != null }.sheet as ChatBarSheet.ThreadName
            assertThat(sheet.error).isEqualTo(ThreadNameError.CreateFailed)
            assertThat(sheet.isSending).isFalse()
        }
    }

    @Test
    fun `thread names are validated`() = runTest {
        aPresenter(aMatchaRoomStore(backgroundScope) { _, _ -> error("Nothing must be sent") }).test {
            awaitUntil { it.canCreateThread }.eventSink(ChatBarEvent.NewThread)
            awaitUntil { it.sheet is ChatBarSheet.ThreadName }.eventSink(ChatBarEvent.UpdateThreadName("   "))
            val blank = awaitUntil { (it.sheet as? ChatBarSheet.ThreadName)?.name == "   " }
            assertThat((blank.sheet as ChatBarSheet.ThreadName).error).isNull()
            blank.eventSink(ChatBarEvent.SubmitThreadName)
            assertThat((awaitUntil { (it.sheet as? ChatBarSheet.ThreadName)?.error != null }.sheet as ChatBarSheet.ThreadName).error)
                .isEqualTo(ThreadNameError.Empty)
            blank.eventSink(ChatBarEvent.UpdateThreadName("a".repeat(MatchaConfig.THREAD_NAME_MAX_LENGTH + 1)))
            val tooLong = awaitUntil { (it.sheet as? ChatBarSheet.ThreadName)?.error == ThreadNameError.TooLong }
            tooLong.eventSink(ChatBarEvent.SubmitThreadName)
            tooLong.eventSink(ChatBarEvent.DismissSheet)
            awaitUntil { it.sheet == null }
        }
    }

    @Test
    fun `renaming sends a name patch, and an unchanged name sends nothing`() = runTest {
        val sent = mutableListOf<String>()
        val store = aMatchaRoomStore(backgroundScope, threads = listOf(aThreadListItem("\$t1", body = "Plan"))) { _, content ->
            sent += content
            Result.success(EventId("\$patch"))
        }
        aPresenter(store).test {
            awaitUntil { it.threads.isNotEmpty() }.eventSink(ChatBarEvent.RenameThread(ThreadId("\$t1")))
            val sheet = awaitUntil { it.sheet is ChatBarSheet.ThreadName }
            assertThat((sheet.sheet as ChatBarSheet.ThreadName).name).isEqualTo("Plan")
            sheet.eventSink(ChatBarEvent.SubmitThreadName)
            awaitUntil { it.sheet == null }
            assertThat(sent).isEmpty()

            sheet.eventSink(ChatBarEvent.RenameThread(ThreadId("\$t1")))
            awaitUntil { it.sheet is ChatBarSheet.ThreadName }.eventSink(ChatBarEvent.UpdateThreadName("Launch plan"))
            awaitUntil { (it.sheet as? ChatBarSheet.ThreadName)?.name == "Launch plan" }.eventSink(ChatBarEvent.SubmitThreadName)
            awaitUntil { it.threads.firstOrNull()?.name == "Launch plan" && it.sheet == null }
            assertThat(sent.single()).contains("\"name\":\"Launch plan\"")
            assertThat(sent.single()).contains("\"rel_type\":\"m.reference\"")
        }
    }

    @Test
    fun `archiving the open thread returns to General and offers Undo`() = runTest {
        val sent = mutableListOf<String>()
        val actions = FakeMatchaChatBarActions()
        val snackbarDispatcher = SnackbarDispatcher()
        val store = aMatchaRoomStore(backgroundScope, threads = listOf(aThreadListItem("\$t1"))) { _, content ->
            sent += content
            Result.success(EventId("\$patch${sent.size}"))
        }
        aPresenter(
            store = store,
            selection = MatchaChatBarSelection.Thread(ThreadId("\$t1")),
            actions = actions,
            snackbarDispatcher = snackbarDispatcher,
        ).test {
            awaitUntil { it.threads.isNotEmpty() }.eventSink(ChatBarEvent.SetArchived(ThreadId("\$t1"), archived = true))
            awaitUntil { it.threads.singleOrNull()?.isArchived == true }
            assertThat(sent.single()).contains("\"archived\":true")
            assertThat(actions.calls).containsExactly("general")
            val snackbar = snackbarDispatcher.snackbarMessage.first()!!
            assertThat(snackbar.messageResId).isEqualTo(R.string.screen_matcha_threads_archived_snackbar)
            assertThat(snackbar.actionResId).isEqualTo(CommonStrings.action_matcha_undo)

            snackbar.action()
            awaitUntil { it.threads.singleOrNull()?.isArchived == false }
            assertThat(sent.last()).contains("\"archived\":false")
        }
    }

    @Test
    fun `a failed archive offers to retry, and unarchiving offers Undo`() = runTest {
        val snackbarDispatcher = SnackbarDispatcher()
        var fail = true
        val sent = mutableListOf<String>()
        val store = aMatchaRoomStore(backgroundScope, threads = listOf(aThreadListItem("\$t1"))) { _, content ->
            sent += content
            if (fail) Result.failure(IllegalStateException("offline")) else Result.success(EventId("\$patch${sent.size}"))
        }
        aPresenter(store, snackbarDispatcher = snackbarDispatcher).test {
            awaitUntil { it.threads.isNotEmpty() }.eventSink(ChatBarEvent.SetArchived(ThreadId("\$t1"), archived = true))
            val failure = snackbarDispatcher.snackbarMessage.first { it != null }!!
            assertThat(failure.messageResId).isEqualTo(CommonStrings.common_matcha_write_failed)
            assertThat(failure.actionResId).isEqualTo(CommonStrings.action_retry)

            fail = false
            snackbarDispatcher.clear()
            failure.action()
            // An archived thread leaves the bar.
            val archived = awaitUntil { it.threads.isEmpty() }
            assertThat(sent.last()).contains("\"archived\":true")
            snackbarDispatcher.clear()

            archived.eventSink(ChatBarEvent.SetArchived(ThreadId("\$t1"), archived = false))
            awaitUntil { it.threads.singleOrNull()?.isArchived == false }
            val unarchived = snackbarDispatcher.snackbarMessage.first { it != null }!!
            assertThat(unarchived.messageResId).isEqualTo(R.string.screen_matcha_threads_unarchived_snackbar)
            assertThat(unarchived.actionResId).isEqualTo(CommonStrings.action_matcha_undo)
        }
    }

    @Test
    fun `the bar shows once the chat has a named thread, and always on the board`() = runTest {
        val named = aMatchaRoomStore(
            scope = backgroundScope,
            threads = listOf(aThreadListItem("\$t1")),
            items = listOf(namePatch(1, "\$t1", "Launch plan")),
        )
        aPresenter(named).test {
            assertThat(awaitUntil { it.threads.isNotEmpty() }.showBar).isTrue()
        }
        val unnamed = aMatchaRoomStore(scope = backgroundScope, threads = listOf(aThreadListItem("\$t1")))
        aPresenter(unnamed).test {
            val state = awaitUntil { it.threads.isNotEmpty() }
            assertThat(state.isVisible).isTrue()
            assertThat(state.showBar).isFalse()
        }
        aPresenter(unnamed, selection = MatchaChatBarSelection.Tasks).test {
            assertThat(awaitUntil { it.isVisible }.showBar).isTrue()
        }
        // The top bar opens the board, so a board alone would only give a bar with General.
        val boardOnly = aMatchaRoomStore(scope = backgroundScope, boardRoot = boardRoot)
        aPresenter(boardOnly).test {
            val visible = awaitUntil { it.isVisible }
            boardOnly.snapshot.first { it.isLoaded && it.board != null }
            assertThat(latest(visible).showBar).isFalse()
        }
    }

    @Test
    fun `the room controller opens the New thread sheet`() = runTest {
        val store = aMatchaRoomStore(backgroundScope)
        val controller = DefaultMatchaRoomController(store, backgroundScope)
        aPresenter(store, controller = controller).test {
            awaitUntil { it.canCreateThread }
            controller.openNewThread()
            assertThat(awaitUntil { it.sheet != null }.sheet).isEqualTo(ChatBarSheet.ThreadName(threadId = null, name = "", error = null, isSending = false))
            assertThat(controller.requests.value).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the room controller opens the task editor, or the board when the chat has none`() = runTest {
        val withBoard = aMatchaRoomStore(backgroundScope, boardRoot = boardRoot)
        val controller = DefaultMatchaRoomController(withBoard, backgroundScope)
        aPresenter(withBoard, controller = controller).test {
            awaitUntil { it.canCreateThread }
            withBoard.snapshot.first { it.isLoaded && it.board != null }
            controller.openNewTask()
            val editor = awaitUntil { it.editor != null }.editor!!
            assertThat(editor.isNew).isTrue()
            editor.eventSink(TaskEditorEvent.Close)
            awaitUntil { it.editor == null }
            cancelAndIgnoreRemainingEvents()
        }
        val actions = FakeMatchaChatBarActions()
        val withoutBoard = aMatchaRoomStore(backgroundScope)
        val otherController = DefaultMatchaRoomController(withoutBoard, backgroundScope)
        aPresenter(withoutBoard, controller = otherController, actions = actions).test {
            awaitUntil { it.isVisible }
            otherController.openNewTask()
            awaitUntil { otherController.requests.value == null }
            assertThat(actions.calls).containsExactly("tasks")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a new task in a chat whose board the user can't add to opens the board, which says why`() = runTest {
        val actions = FakeMatchaChatBarActions()
        val readOnly = aMatchaRoomStore(
            scope = backgroundScope,
            boardRoot = boardRoot,
            canSendMessage = { it != MessageEventType.Other(MatchaConfig.EVENT_KANBAN_CARD) },
        )
        val controller = DefaultMatchaRoomController(readOnly, backgroundScope)
        aPresenter(readOnly, controller = controller, actions = actions).test {
            val ready = awaitUntil { it.canCreateThread }
            readOnly.snapshot.first { it.isLoaded && it.board != null }
            controller.openNewTask()
            awaitUntil { controller.requests.value == null }
            assertThat(actions.calls).containsExactly("tasks")
            assertThat(latest(ready).editor).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `without permission threads cannot be created or managed`() = runTest {
        val store = aMatchaRoomStore(
            scope = backgroundScope,
            threads = listOf(aThreadListItem("\$t1")),
            canSendMessage = { it != MessageEventType.Other(MatchaConfig.EVENT_THREAD_METADATA) },
        ) { _, _ -> error("Nothing must be sent") }
        aPresenter(store).test {
            val state = awaitUntil { it.threads.isNotEmpty() }
            assertThat(state.canCreateThread).isFalse()
            assertThat(state.canManageThreads).isFalse()
            state.eventSink(ChatBarEvent.NewThread)
            state.eventSink(ChatBarEvent.RenameThread(ThreadId("\$t1")))
            state.eventSink(ChatBarEvent.SetArchived(ThreadId("\$t1"), archived = true))
            state.eventSink(ChatBarEvent.ShowThreadActions(ThreadId("\$t1")))
            assertThat(awaitUntil { it.sheet != null }.sheet).isEqualTo(ChatBarSheet.ThreadActions(state.threads.single()))
        }
    }

    @Test
    fun `remind me is about the open thread, and view task opens its task`() = runTest {
        val actions = FakeMatchaChatBarActions()
        aPresenter(aMatchaRoomStore(backgroundScope), selection = MatchaChatBarSelection.Thread(ThreadId("\$t1")), actions = actions).test {
            awaitUntil { it.canRemind }.eventSink(ChatBarEvent.RemindMe(threadId = null))
            val reminding = awaitUntil { it.reminderTarget != null }
            assertThat(reminding.reminderTarget?.threadId).isEqualTo(ThreadId("\$t1"))
            reminding.eventSink(ChatBarEvent.DismissReminder)
            awaitUntil { it.reminderTarget == null }.eventSink(ChatBarEvent.ViewTask(ThreadId("\$t2")))
            assertThat(actions.calls).containsExactly("task \$t2")
        }
    }

    private fun namePatch(index: Int, threadId: String, name: String) = aMatchaEventItem(
        index,
        MatchaConfig.EVENT_THREAD_METADATA,
        """{"version": 1, "name": "$name", "m.relates_to": {"rel_type": "m.reference", "event_id": "$threadId"}}""",
    )

    private fun archivePatch(index: Int, threadId: String) = aMatchaEventItem(
        index,
        MatchaConfig.EVENT_THREAD_METADATA,
        """{"version": 1, "archived": true, "m.relates_to": {"rel_type": "m.reference", "event_id": "$threadId"}}""",
    )

    private fun TestScope.aPresenter(
        store: MatchaRoomStore,
        selection: MatchaChatBarSelection = MatchaChatBarSelection.General,
        actions: MatchaChatBarActions = FakeMatchaChatBarActions(),
        isEnabled: Boolean = true,
        snackbarDispatcher: SnackbarDispatcher = SnackbarDispatcher(),
        controller: DefaultMatchaRoomController = DefaultMatchaRoomController(store, backgroundScope),
    ) = PresenterUnderTest(
        presenter = ChatBarPresenter(
            room = FakeJoinedRoom(),
            store = store,
            controller = controller,
            featureFlagService = FakeFeatureFlagService(mapOf(FeatureFlags.MatchaWorkspaces.key to isEnabled)),
            snackbarDispatcher = snackbarDispatcher,
            roomCoroutineScope = backgroundScope,
            editorPresenterFactory = aTaskEditorPresenterFactory(),
        ),
        selection = selection,
        actions = actions,
    )

    private class PresenterUnderTest(
        val presenter: ChatBarPresenter,
        val selection: MatchaChatBarSelection,
        val actions: MatchaChatBarActions,
    ) {
        suspend fun test(block: suspend TurbineTestContext<ChatBarState>.() -> Unit) =
            moleculeFlow(RecompositionMode.Immediate) { presenter.present(selection, actions) }.test(validate = block)
    }

    private suspend fun TurbineTestContext<ChatBarState>.awaitUntil(predicate: (ChatBarState) -> Boolean): ChatBarState =
        consumeItemsUntilPredicate(predicate = predicate).last()

    /** The newest state once the emissions settle, or [previous] when nothing changed since. */
    private suspend fun TurbineTestContext<ChatBarState>.latest(previous: ChatBarState): ChatBarState =
        consumeItemsUntilTimeout().lastOrNull() ?: previous

    private class FakeMatchaChatBarActions(hasSpaceDocs: Boolean = true) : MatchaChatBarActions {
        val calls = mutableListOf<String>()
        override fun openGeneral() {
            calls += "general"
        }

        override fun openTasks() {
            calls += "tasks"
        }

        override fun openThreadTask(threadId: ThreadId) {
            calls += "task ${threadId.value}"
        }

        override fun openThread(threadId: ThreadId) {
            calls += "thread ${threadId.value}"
        }

        override fun openAllThreads() {
            calls += "all"
        }

        override val openSpaceDocs: (() -> Unit)? = if (hasSpaceDocs) {
            { calls += "docs" }
        } else {
            null
        }
    }
}
