/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.api

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import io.element.android.libraries.matrix.api.core.ThreadId

/**
 * The chip bar shown directly under the top bar of a chat, of a thread and of the chat's task board:
 * General, Tasks, the active named threads, New thread, All threads and More. Room scoped.
 * Renders nothing when the Matcha workspaces feature is off. Compose it whenever the feature is on, even when the chat has no
 * named thread and no board: it then draws no bar but still hosts the sheets [MatchaRoomController] opens.
 */
interface MatchaChatBarRenderer {
    @Composable
    fun Render(
        selection: MatchaChatBarSelection,
        actions: MatchaChatBarActions,
        modifier: Modifier,
    )
}

/** What the screen under the bar is showing. */
@Immutable
sealed interface MatchaChatBarSelection {
    /** The main chat timeline. */
    data object General : MatchaChatBarSelection

    /** The chat's task board. */
    data object Tasks : MatchaChatBarSelection

    /** A thread timeline. */
    data class Thread(val threadId: ThreadId) : MatchaChatBarSelection
}

/** Navigation requested by the chip bar; the hosting flow decides how to switch screens. */
interface MatchaChatBarActions {
    fun openGeneral()
    fun openTasks()

    /** Opens the board on the task of [threadId], from a thread chip's "View task"; the board offers to create one if there is none. */
    fun openThreadTask(threadId: ThreadId) = openTasks()

    fun openThread(threadId: ThreadId)

    /** Pushes the list of all threads; it is a page, not a selection of the bar. */
    fun openAllThreads()

    /** Opens the documents of the chat's space, or null when the chat has no joined parent space. */
    val openSpaceDocs: (() -> Unit)?
}
