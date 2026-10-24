/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.store

import com.google.common.truth.Truth.assertThat
import io.element.android.appconfig.MatchaConfig
import io.element.android.features.matcha.impl.docs.ANCHOR
import io.element.android.features.matcha.impl.docs.BOB
import io.element.android.features.matcha.impl.docs.DocsRoomFixture
import io.element.android.features.matcha.impl.docs.aCreateItem
import io.element.android.libraries.matrix.api.room.RawEventsPage
import io.element.android.libraries.matrix.api.timeline.TimelineException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MatchaRoomStoreLoadMoreTest {
    @Test
    fun `only a documents' store reads the events related to the create event`() = runTest {
        listOf(true, false).forEach { readsDocumentAnchor ->
            val anchors = mutableListOf<String>()
            val fixture = DocsRoomFixture(
                listOf(aCreateItem()),
                fetchRelatedEventsJsonResult = { eventId, _, _, _ ->
                    anchors += eventId.value
                    Result.success(RawEventsPage(emptyList(), null))
                },
            )
            val store = MatchaRoomStore(fixture.room, backgroundScope, readsDocumentAnchor = readsDocumentAnchor)
            settle()
            assertThat(anchors).isEqualTo(if (readsDocumentAnchor) listOf(ANCHOR) else emptyList())
            assertThat(store.snapshot.value.events.single().type).isEqualTo("m.room.create")
        }
    }

    @Test
    fun `awaiting the relations returns once the snapshot holds those of an anchor the room state gained after the store loaded`() = runTest {
        val frame = """{"type": "${MatchaConfig.EVENT_DOCS_UPDATE}", "event_id": "${'$'}f1", "sender": "$BOB", "origin_server_ts": 2, """ +
            """"content": {"m.relates_to": {"rel_type": "m.reference", "event_id": "$ANCHOR"}}}"""
        val fixture = DocsRoomFixture(
            listOf(aCreateItem()),
            createEventId = null,
            fetchRelatedEventsJsonResult = { eventId, _, _, _ -> Result.success(RawEventsPage(listOfNotNull(frame.takeIf { eventId.value == ANCHOR }), null)) },
        )
        val store = fixture.store(backgroundScope)
        settle()
        assertThat(store.snapshot.value.isLoaded).isTrue()
        fixture.createEventId = ANCHOR
        // The read ends before the snapshot is built again, so the loaded snapshot from before it is still the latest one for a moment.
        store.awaitRelations()
        assertThat(store.snapshot.value.events.mapNotNull { it.eventId }).contains("\$f1")
    }

    @Test
    fun `the timeline is closed when the store's scope ends`() = runTest {
        val fixture = DocsRoomFixture(emptyList())
        val scope = CoroutineScope(backgroundScope.coroutineContext + Job(backgroundScope.coroutineContext.job))
        fixture.store(scope)
        settle()
        assertThat(fixture.timeline.closeCounter).isEqualTo(0)
        scope.cancel()
        settle()
        assertThat(fixture.timeline.closeCounter).isEqualTo(1)
    }

    @Test
    fun `loadMore reports whether history remains and fails when a page can't be read`() = runTest {
        var result: Result<Boolean> = Result.success(false)
        val fixture = DocsRoomFixture(emptyList(), hasMoreHistory = true, paginateResult = { result })
        val store = fixture.store(backgroundScope)
        settle()
        assertThat(store.loadMore().getOrNull()).isTrue()
        result = Result.failure(IllegalStateException("network"))
        assertThat(store.loadMore().isFailure).isTrue()
        // A page refused because another read is running is tried again, a bounded number of times.
        result = Result.failure(TimelineException.CannotPaginate)
        assertThat(store.loadMore().exceptionOrNull()).isEqualTo(TimelineException.CannotPaginate)
        fixture.timeline.backwardPaginationStatus.value = fixture.timeline.backwardPaginationStatus.value.copy(hasMoreToLoad = false)
        assertThat(store.loadMore().getOrNull()).isFalse()
    }
}
