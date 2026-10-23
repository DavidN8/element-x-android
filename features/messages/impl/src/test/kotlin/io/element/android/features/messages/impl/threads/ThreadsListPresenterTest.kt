/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.threads

import com.google.common.truth.Truth.assertThat
import io.element.android.features.matcha.api.MatchaRoomController
import io.element.android.features.matcha.api.MatchaThreadInfo
import io.element.android.features.matcha.test.FakeMatchaRoomController
import io.element.android.features.messages.impl.fixtures.aTimelineItemContentFactory
import io.element.android.features.messages.impl.messagesummary.FakeMessageSummaryFormatter
import io.element.android.features.messages.impl.threads.list.ThreadArchiveFilter
import io.element.android.features.messages.impl.threads.list.ThreadsListEvent
import io.element.android.features.messages.impl.threads.list.ThreadsListPresenter
import io.element.android.features.messages.impl.threads.list.aThreadListItem
import io.element.android.libraries.dateformatter.test.FakeDateFormatter
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.featureflag.test.FakeFeatureFlagService
import io.element.android.libraries.matrix.api.room.threads.ThreadListPaginationStatus
import io.element.android.libraries.matrix.test.AN_AVATAR_URL
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_ROOM_NAME
import io.element.android.libraries.matrix.test.A_THREAD_ID
import io.element.android.libraries.matrix.test.A_THREAD_ID_2
import io.element.android.libraries.matrix.test.room.FakeJoinedRoom
import io.element.android.libraries.matrix.test.room.threads.FakeThreadsListService
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.lambda.lambdaRecorder
import io.element.android.tests.testutils.test
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ThreadsListPresenterTest {
    @Test
    fun `present - initial state`() = runTest {
        createThreadsListPresenter().test {
            awaitItem().run {
                assertThat(threads).isEmpty()
                assertThat(roomId).isEqualTo(A_ROOM_ID)
                assertThat(roomName).isEqualTo(A_ROOM_NAME)
                assertThat(roomAvatarUrl).isEqualTo(AN_AVATAR_URL)
            }
        }
    }

    @Test
    fun `present - paginate`() = runTest {
        val paginateRecorder = lambdaRecorder<Result<Unit>> { Result.success(Unit) }
        val threadsListService = FakeThreadsListService(paginate = paginateRecorder)
        val room = FakeJoinedRoom(threadsListService = threadsListService)
        createThreadsListPresenter(room).test {
            val initialItem = awaitItem()

            // Pagination is automatically triggered on start, so we should have one call to paginate already
            paginateRecorder.assertions().isCalledOnce()

            initialItem.eventSink(ThreadsListEvent.Paginate)

            // Simulate a pagination result
            threadsListService.emit(listOf(aThreadListItem()))

            // We should have a second call to paginate after the event is sent
            paginateRecorder.assertions().isCalledExactly(2)

            // And we receive the new items
            assertThat(awaitItem().threads).isNotEmpty()
        }
    }

    @Test
    fun `present - a failed first page shows the error, and retry loads again`() = runTest {
        var result = Result.failure<Unit>(IllegalStateException("Network error"))
        val paginateRecorder = lambdaRecorder<Result<Unit>> { result }
        val threadsListService = FakeThreadsListService(paginate = paginateRecorder)
        createThreadsListPresenter(FakeJoinedRoom(threadsListService = threadsListService)).test {
            val failedState = awaitItem().let { state -> if (state.loadFailed) state else awaitItem() }
            assertThat(failedState.loadFailed).isTrue()
            assertThat(failedState.isLoadingFirstPage).isFalse()

            result = Result.success(Unit)
            failedState.eventSink(ThreadsListEvent.Retry)
            val retryingState = awaitItem()
            assertThat(retryingState.loadFailed).isFalse()
            assertThat(retryingState.isLoadingFirstPage).isTrue()
            paginateRecorder.assertions().isCalledExactly(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - hasMoreToLoad follows the pagination status`() = runTest {
        val paginationStatus = MutableStateFlow<ThreadListPaginationStatus>(ThreadListPaginationStatus.Idle(hasMoreToLoad = true))
        val threadsListService = FakeThreadsListService(paginationStatus = paginationStatus)
        createThreadsListPresenter(FakeJoinedRoom(threadsListService = threadsListService)).test {
            assertThat(awaitItem().hasMoreToLoad).isTrue()
            paginationStatus.value = ThreadListPaginationStatus.Idle(hasMoreToLoad = false)
            val endState = awaitItem()
            assertThat(endState.hasMoreToLoad).isFalse()
            // Nothing to load and nothing loaded: the empty state, not the loading one
            assertThat(endState.isLoadingFirstPage).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - without the Matcha workspaces, every thread shows and there are no chips`() = runTest {
        val threadsListService = FakeThreadsListService(items = MutableStateFlow(listOf(aThreadListItem(threadId = A_THREAD_ID))))
        val controller = FakeMatchaRoomController(threads = MutableStateFlow(persistentMapOf(A_THREAD_ID to MatchaThreadInfo("Planning", isArchived = true))))
        createThreadsListPresenter(FakeJoinedRoom(threadsListService = threadsListService), matchaRoomController = controller).test {
            val state = awaitItem().let { if (it.threads.isEmpty()) awaitItem() else it }
            assertThat(state.archiveFilter).isNull()
            assertThat(state.matchaThreads).isEmpty()
            assertThat(state.visibleThreads.map { it.item.threadId }).containsExactly(A_THREAD_ID)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - with the Matcha workspaces, threads are named and split into active and archived`() = runTest {
        val threadsListService = FakeThreadsListService(
            items = MutableStateFlow(listOf(aThreadListItem(threadId = A_THREAD_ID), aThreadListItem(threadId = A_THREAD_ID_2))),
        )
        val controller = FakeMatchaRoomController(
            threads = MutableStateFlow(
                persistentMapOf(
                    A_THREAD_ID to MatchaThreadInfo(name = "Planning", isArchived = false),
                    A_THREAD_ID_2 to MatchaThreadInfo(name = "Venue", isArchived = true),
                )
            ),
        )
        createThreadsListPresenter(
            room = FakeJoinedRoom(threadsListService = threadsListService),
            featureFlagService = FakeFeatureFlagService(mapOf(FeatureFlags.MatchaWorkspaces.key to true)),
            matchaRoomController = controller,
        ).test {
            val active = consumeItemsUntilPredicate { it.archiveFilter != null && it.threads.size == 2 }.last()
            assertThat(active.archiveFilter).isEqualTo(ThreadArchiveFilter.Active)
            assertThat(active.visibleThreads.map { it.item.threadId }).containsExactly(A_THREAD_ID)
            assertThat(active.nameOf(active.visibleThreads.single())).isEqualTo("Planning")

            active.eventSink(ThreadsListEvent.SelectArchiveFilter(ThreadArchiveFilter.Archived))
            val archived = awaitItem()
            assertThat(archived.visibleThreads.map { it.item.threadId }).containsExactly(A_THREAD_ID_2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun createThreadsListPresenter(
        room: FakeJoinedRoom = FakeJoinedRoom(),
        featureFlagService: FakeFeatureFlagService = FakeFeatureFlagService(mapOf(FeatureFlags.MatchaWorkspaces.key to false)),
        matchaRoomController: MatchaRoomController = FakeMatchaRoomController(),
    ): ThreadsListPresenter {
        return ThreadsListPresenter(
            room = room,
            timelineItemContentFactory = aTimelineItemContentFactory(),
            messageSummaryFormatter = FakeMessageSummaryFormatter(),
            dateFormatter = FakeDateFormatter(),
            featureFlagService = featureFlagService,
            matchaRoomController = lazy { matchaRoomController },
        )
    }
}
