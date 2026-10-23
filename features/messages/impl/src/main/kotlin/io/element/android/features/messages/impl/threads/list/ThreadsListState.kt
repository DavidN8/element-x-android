/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.threads.list

import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.matrix.api.core.RoomId
import kotlinx.collections.immutable.ImmutableList

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
    val eventSink: (ThreadsListEvent) -> Unit,
) {
    /** Nothing is shown yet, and the first page is on its way. */
    val isLoadingFirstPage = threads.isEmpty() && hasMoreToLoad && !loadFailed
}
