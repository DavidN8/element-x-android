/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.api

import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import io.element.android.libraries.architecture.FeatureEntryPoint
import io.element.android.libraries.matrix.api.core.ThreadId

/** Room-scoped Matcha screens, created from the chat's flow. */
interface MatchaRoomEntryPoint : FeatureEntryPoint {
    data class Params(
        /** The board opens the editor for this thread's task when set, e.g. from a thread chip's "View task". */
        val initialThreadId: ThreadId? = null,
    )

    interface Callback : Plugin {
        /** The board's chip bar or a task asked to show a thread of this chat. */
        fun openThread(threadId: ThreadId)

        /** The board's chip bar asked for the main chat. */
        fun openGeneral()

        /** The board's chip bar asked for the list of all threads. */
        fun openAllThreads()

        /** The board's chip bar asked for the space documents, when the chat has a space. */
        fun openSpaceDocs()

        /** The board's top bar name block was tapped. */
        fun openRoomDetails()
    }

    /** The chat's task board, with its own room top bar and the chip bar. */
    fun createTasksNode(
        parentNode: Node,
        buildContext: BuildContext,
        params: Params,
        callback: Callback,
    ): Node
}
