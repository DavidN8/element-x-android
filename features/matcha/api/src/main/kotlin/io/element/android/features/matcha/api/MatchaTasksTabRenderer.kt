/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.api

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.ThreadId

/** The Tasks tab on Home: tasks from all the user's chats. Session scoped. Renders nothing when the feature is off. */
interface MatchaTasksTabRenderer {
    @Composable
    fun Render(
        /** Padding from the Home scaffold (top bar and bottom navigation). */
        contentPadding: PaddingValues,
        onOpenRoom: (RoomId) -> Unit,
        onOpenThread: (RoomId, ThreadId) -> Unit,
        modifier: Modifier,
    )
}
