/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.tasks

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.features.matcha.api.MatchaChatBarActions
import io.element.android.features.matcha.api.MatchaChatBarRenderer
import io.element.android.features.matcha.api.MatchaChatBarSelection
import io.element.android.features.matcha.api.MatchaReminderRenderer
import io.element.android.features.matcha.api.MatchaRoomEntryPoint
import io.element.android.libraries.architecture.NodeInputs
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.architecture.inputs
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.matrix.api.core.ThreadId

/** A chat's task board with its own room top bar and the chip bar; Archived tasks and the sheets are internal to it. */
@ContributesNode(RoomScope::class)
@AssistedInject
class TasksNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: TaskBoardPresenter.Factory,
    private val chatBarRenderer: MatchaChatBarRenderer,
    private val reminderRenderer: MatchaReminderRenderer,
) : Node(buildContext = buildContext, plugins = plugins) {
    data class Inputs(val initialThreadId: ThreadId?) : NodeInputs

    private val inputs: Inputs = inputs()
    private val callback: MatchaRoomEntryPoint.Callback = callback()
    private val presenter = presenterFactory.create(initialThreadId = inputs.initialThreadId, openThread = callback::openThread)

    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        if (!state.isVisible) return
        val eventSink by rememberUpdatedState(state.eventSink)
        val actions = remember(state.hasSpaceDocs) {
            object : MatchaChatBarActions {
                override fun openGeneral() = callback.openGeneral()

                // Already on the board.
                override fun openTasks() = Unit

                override fun openThreadTask(threadId: ThreadId) = eventSink(TaskBoardEvent.EditThreadTask(threadId))

                override fun openThread(threadId: ThreadId) = callback.openThread(threadId)

                // ponytail: the board has no All threads entry; the chat's top bar has it.
                override fun openAllThreads() = Unit

                override val openSpaceDocs: (() -> Unit)? = if (state.hasSpaceDocs) callback::openSpaceDocs else null
            }
        }
        TasksView(
            state = state,
            onBack = { navigateUp() },
            onOpenRoomDetails = callback::openRoomDetails,
            modifier = modifier,
        ) {
            chatBarRenderer.Render(selection = MatchaChatBarSelection.Tasks, actions = actions, modifier = Modifier)
        }
        reminderRenderer.Render(target = state.reminderTarget, onDismiss = { eventSink(TaskBoardEvent.DismissReminder) })
    }
}
