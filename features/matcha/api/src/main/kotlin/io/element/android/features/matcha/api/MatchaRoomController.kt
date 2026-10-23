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
    /** True when this room has something for the chip bar: a named active thread or a board. */
    val hasChatBar: StateFlow<Boolean>

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
)
