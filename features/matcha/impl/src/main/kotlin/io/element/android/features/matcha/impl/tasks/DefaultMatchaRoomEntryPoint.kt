/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.tasks

import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.matcha.api.MatchaRoomEntryPoint
import io.element.android.libraries.architecture.createNode

@ContributesBinding(AppScope::class)
class DefaultMatchaRoomEntryPoint : MatchaRoomEntryPoint {
    override fun createTasksNode(
        parentNode: Node,
        buildContext: BuildContext,
        params: MatchaRoomEntryPoint.Params,
        callback: MatchaRoomEntryPoint.Callback,
    ): Node = parentNode.createNode<TasksNode>(
        buildContext = buildContext,
        plugins = listOf(TasksNode.Inputs(initialThreadId = params.initialThreadId), callback),
    )
}
