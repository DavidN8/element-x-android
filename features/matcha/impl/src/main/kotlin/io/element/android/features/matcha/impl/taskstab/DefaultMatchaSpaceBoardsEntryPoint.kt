/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.taskstab

import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.matcha.api.MatchaSpaceBoardsEntryPoint
import io.element.android.libraries.architecture.createNode
import io.element.android.libraries.matrix.api.core.RoomId

@ContributesBinding(AppScope::class)
class DefaultMatchaSpaceBoardsEntryPoint : MatchaSpaceBoardsEntryPoint {
    override fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        spaceId: RoomId,
        callback: MatchaSpaceBoardsEntryPoint.Callback,
    ): Node = parentNode.createNode<MatchaSpaceBoardsNode>(buildContext, plugins = listOf(MatchaSpaceBoardsNode.Inputs(spaceId), callback))
}
