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
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.ThreadId

/** The boards of a space's channels: the Tasks tab narrowed to the chats the Channels tab lists under the space. Session scoped. */
interface MatchaSpaceBoardsEntryPoint : FeatureEntryPoint {
    interface Callback : Plugin {
        /** Shows a chat, for Open chat and for an older standalone card. */
        fun navigateToRoom(roomId: RoomId)

        /** Shows a thread of a chat, for a task. */
        fun navigateToThread(roomId: RoomId, threadId: ThreadId)

        fun close()
    }

    fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        spaceId: RoomId,
        callback: Callback,
    ): Node
}
