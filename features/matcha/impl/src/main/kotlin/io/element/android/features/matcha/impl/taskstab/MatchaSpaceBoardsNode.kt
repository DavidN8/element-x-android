/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.taskstab

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.features.matcha.api.MatchaSpaceBoardsEntryPoint
import io.element.android.libraries.architecture.NodeInputs
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.architecture.inputs
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import io.element.android.libraries.designsystem.utils.snackbar.collectSnackbarMessageAsState
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.core.RoomId

/**
 * All boards of a space: the Tasks tab's presenter and view narrowed to the space. Only the space's id is kept, its name and chats
 * are read live, so it is shown again as it was after process death.
 */
@ContributesNode(SessionScope::class)
@AssistedInject
class MatchaSpaceBoardsNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val presenter: TasksTabPresenter,
    private val snackbarDispatcher: SnackbarDispatcher,
) : Node(buildContext, plugins = plugins) {
    data class Inputs(val spaceId: RoomId) : NodeInputs

    private val spaceId = inputs<Inputs>().spaceId
    private val callback: MatchaSpaceBoardsEntryPoint.Callback = callback()

    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present(onOpenRoom = callback::navigateToRoom, onOpenThread = callback::navigateToThread, spaceId = spaceId)
        val snackbarMessage by snackbarDispatcher.collectSnackbarMessageAsState()
        // Nothing is drawn while the Matcha workspaces are off, as for the docs.
        if (state.isVisible) SpaceBoardsView(state = state, snackbarMessage = snackbarMessage, onBackClick = callback::close, modifier = modifier)
    }
}
