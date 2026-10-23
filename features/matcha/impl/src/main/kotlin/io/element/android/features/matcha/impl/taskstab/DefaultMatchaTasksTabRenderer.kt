/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.taskstab

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.matcha.api.MatchaTasksTabRenderer
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.ThreadId

@ContributesBinding(SessionScope::class)
class DefaultMatchaTasksTabRenderer(
    private val presenter: TasksTabPresenter,
) : MatchaTasksTabRenderer {
    @Composable
    override fun Render(
        contentPadding: PaddingValues,
        onOpenRoom: (RoomId) -> Unit,
        onOpenThread: (RoomId, ThreadId) -> Unit,
        modifier: Modifier,
    ) {
        val state = presenter.present(onOpenRoom = onOpenRoom, onOpenThread = onOpenThread)
        if (!state.isVisible) return
        TasksTabView(state = state, contentPadding = contentPadding, modifier = modifier)
    }
}
