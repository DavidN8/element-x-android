/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.threads.list

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.features.matcha.api.MatchaThreadInfo
import io.element.android.libraries.designsystem.preview.ROOM_NAME
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.ThreadId
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap

open class ThreadsListStatePreviewParam : PreviewParameterProvider<ThreadsListState> {
    override val values: Sequence<ThreadsListState>
        get() = sequenceOf(
            aThreadsListState(),
            aThreadsListState(threads = emptyList(), hasMoreToLoad = true),
            aThreadsListState(threads = emptyList(), hasMoreToLoad = false),
            aThreadsListState(threads = emptyList(), loadFailed = true),
            aThreadsListState(
                threads = listOf(
                    aThreadListRowItem(threadId = ThreadId("\$thread-untitled"), rootEventText = null),
                    aThreadListRowItem(threadId = ThreadId("\$thread-no-reply"), latestEvent = null, numberOfReplies = 0),
                ),
                hasMoreToLoad = false,
            ),
            aThreadsListState(
                archiveFilter = ThreadArchiveFilter.Active,
                matchaThreads = mapOf(
                    ThreadId("\$thread-0") to MatchaThreadInfo(name = "Release planning", isArchived = false),
                    ThreadId("\$thread-1") to MatchaThreadInfo(name = null, isArchived = false),
                    ThreadId("\$thread-2") to MatchaThreadInfo(name = "Venue", isArchived = true),
                ),
            ),
            aThreadsListState(archiveFilter = ThreadArchiveFilter.Archived, hasMoreToLoad = false),
            aThreadsListState(threads = emptyList(), hasMoreToLoad = false, archiveFilter = ThreadArchiveFilter.Active),
        )
}

internal fun aThreadsListState(
    threads: List<ThreadListRowItem> = List(10) { aThreadListRowItem(threadId = ThreadId("\$thread-$it")) },
    hasMoreToLoad: Boolean = true,
    loadFailed: Boolean = false,
    archiveFilter: ThreadArchiveFilter? = null,
    matchaThreads: Map<ThreadId, MatchaThreadInfo> = emptyMap(),
    eventSink: (ThreadsListEvent) -> Unit = {},
) = ThreadsListState(
    roomId = RoomId("!room-id:server"),
    roomName = ROOM_NAME,
    roomAvatarUrl = null,
    isRoomTombstoned = false,
    heroes = persistentListOf(),
    threads = threads.toImmutableList(),
    hasMoreToLoad = hasMoreToLoad,
    loadFailed = loadFailed,
    archiveFilter = archiveFilter,
    matchaThreads = matchaThreads.toImmutableMap(),
    eventSink = eventSink,
)
