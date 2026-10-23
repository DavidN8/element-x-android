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

/** The documents of a space, shared with the Matcha web client. Session scoped. */
interface MatchaSpaceDocsEntryPoint : FeatureEntryPoint {
    data class Params(
        val spaceId: RoomId,
        /** Opens this document directly instead of the list. */
        val documentId: String? = null,
        /** Opens the chat's own earlier document, read-only, instead of the space's documents. */
        val legacyChatId: RoomId? = null,
    )

    interface Callback : Plugin {
        fun close()
    }

    /** The docs list, editor and one-time setup for [Params.spaceId]. */
    fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        params: Params,
        callback: Callback,
    ): Node
}
