/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.api

import androidx.compose.runtime.Immutable
import io.element.android.libraries.matrix.api.core.ThreadId
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.coroutines.flow.StateFlow

/** How the chat screens reach a room's Matcha state without depending on the implementation. Room scoped. */
interface MatchaRoomController {
    /** True when this room has something for the chip bar: an active thread someone named. */
    val hasChatBar: StateFlow<Boolean>

    /** To do and In progress tasks on the room's board; 0 without a board and until the board has loaded. */
    val openTaskCount: StateFlow<Int>

    /** The user can name a new thread: they can post and change thread names. */
    val canCreateThread: StateFlow<Boolean>

    /** Matcha names and archive flags of the room's threads. */
    val threads: StateFlow<ImmutableMap<ThreadId, MatchaThreadInfo>>

    /** Shows the New thread sheet (hosted by the chip bar renderer). */
    fun openNewThread()

    /** Shows the task editor for a new task in this chat (hosted by the chip bar renderer). */
    fun openNewTask()
}

@Immutable
data class MatchaThreadInfo(
    /** The shared name, else the root message text; null when only the event id is known. */
    val name: String?,
    val isArchived: Boolean,
    /** Replies the server counts but nobody sees, like the hidden first reply of a named thread; reply counts leave them out. */
    val hiddenReplies: Int = 0,
)
