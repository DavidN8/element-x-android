/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.threads.list

import io.element.android.features.matcha.api.MatchaThreadInfo
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.ThreadId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableList

data class ThreadsListState(
    val roomId: RoomId,
    val roomName: String,
    val roomAvatarUrl: String?,
    val isRoomTombstoned: Boolean,
    val heroes: ImmutableList<AvatarData>,
    val threads: ImmutableList<ThreadListRowItem>,
    /** Whether older threads can still be loaded. */
    val hasMoreToLoad: Boolean,
    /** Whether the last attempt to load threads failed. */
    val loadFailed: Boolean,
    /** The selected Active or Archived chip while the Matcha workspaces are on; null shows every thread, without chips. */
    val archiveFilter: ThreadArchiveFilter?,
    /** The Matcha names and archive flags of the threads; empty while the Matcha workspaces are off. */
    val matchaThreads: ImmutableMap<ThreadId, MatchaThreadInfo>,
    val eventSink: (ThreadsListEvent) -> Unit,
) {
    /** Nothing is shown yet, and the first page is on its way. */
    val isLoadingFirstPage = threads.isEmpty() && hasMoreToLoad && !loadFailed

    /** The loaded threads that the selected chip shows. */
    val visibleThreads: ImmutableList<ThreadListRowItem> = when (archiveFilter) {
        null -> threads
        else -> threads.filter { row ->
            val isArchived = matchaThreads[row.item.threadId]?.isArchived == true
            isArchived == (archiveFilter == ThreadArchiveFilter.Archived)
        }.toImmutableList()
    }

    /** The name a row shows: the Matcha name, else the first line of the root message, else none ("Untitled thread"). */
    fun nameOf(row: ThreadListRowItem): String? =
        matchaThreads[row.item.threadId]?.name ?: row.rootEventText?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()
}

/** The chips of the thread list while the Matcha workspaces are on. */
enum class ThreadArchiveFilter {
    Active,
    Archived,
}
