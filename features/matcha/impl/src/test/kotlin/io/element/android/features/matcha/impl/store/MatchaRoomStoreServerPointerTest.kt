/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.store

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MatchaRoomStoreServerPointerTest {
    private val root = "\$board-root"
    private val pointer = """{"version": 1, "event_id": "$root"}"""

    /** More history than the store reads up front, so only the homeserver can settle whether the room has a board. */
    private val longHistory = List(MatchaRoomStore.INITIAL_HISTORY_PAGES + 5) { emptyList<MatrixTimelineItem>() }

    /** The store runs in the background scope, which advanceUntilIdle() does not drive. */
    private fun TestScope.settle() {
        testScheduler.advanceTimeBy(1_000)
        testScheduler.runCurrent()
    }

    private fun TestScope.aStore(serverPointerResult: () -> Result<String?>): MatchaRoomStore {
        val room = aMatchaJoinedRoom(roomCoroutineScope = backgroundScope, olderPages = longHistory, serverPointerResult = serverPointerResult)
        return MatchaRoomStore(room, backgroundScope)
    }

    @Test
    fun `a board pointer the sync didn't store is read from the homeserver`() = runTest {
        val store = aStore { Result.success(pointer) }
        settle()
        assertThat(store.snapshot.value.board?.rootId).isEqualTo(root)
        assertThat(store.snapshot.value.isBoardResolved).isTrue()
    }

    @Test
    fun `the homeserver saying there is no pointer settles that the room has no board`() = runTest {
        val store = aStore { Result.success(null) }
        settle()
        assertThat(store.snapshot.value.board).isNull()
        assertThat(store.snapshot.value.isBoardResolved).isTrue()
    }

    @Test
    fun `without the SDK patch a missing board stays unresolved until the history is read`() = runTest {
        val store = aStore { Result.failure(UnsupportedOperationException()) }
        settle()
        assertThat(store.snapshot.value.board).isNull()
        assertThat(store.snapshot.value.isBoardResolved).isFalse()
    }

    @Test
    fun `a failed homeserver read is tried again on retry`() = runTest {
        var answer: Result<String?> = Result.failure(IllegalStateException("network"))
        val store = aStore { answer }
        settle()
        assertThat(store.snapshot.value.isBoardResolved).isFalse()
        answer = Result.success(pointer)
        store.retryLoad()
        settle()
        assertThat(store.snapshot.value.board?.rootId).isEqualTo(root)
    }

    @Test
    fun `creating a board first asks the homeserver, so a board made elsewhere is never replaced`() = runTest {
        var asked = 0
        val store = aStore {
            asked++
            Result.success(pointer)
        }
        settle()
        assertThat(store.readBoardPointer().getOrNull()).isEqualTo(root)
        assertThat(asked).isEqualTo(2)
    }

    @Test
    fun `creating a board fails when the homeserver can't be asked`() = runTest {
        val store = aStore { Result.failure(IllegalStateException("network")) }
        settle()
        assertThat(store.readBoardPointer().isFailure).isTrue()
    }
}
