/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.chatbar

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.matcha.api.MatchaChatBarActions
import io.element.android.features.matcha.api.MatchaChatBarRenderer
import io.element.android.features.matcha.api.MatchaChatBarSelection
import io.element.android.features.matcha.api.MatchaReminderRenderer
import io.element.android.libraries.di.RoomScope

@ContributesBinding(RoomScope::class)
class DefaultMatchaChatBarRenderer(
    private val presenter: ChatBarPresenter,
    private val reminderRenderer: MatchaReminderRenderer,
) : MatchaChatBarRenderer {
    @Composable
    override fun Render(
        selection: MatchaChatBarSelection,
        actions: MatchaChatBarActions,
        modifier: Modifier,
    ) {
        val state = presenter.present(selection, actions)
        if (!state.isVisible) return
        ChatBarView(state = state, modifier = modifier)
        reminderRenderer.Render(
            target = state.reminderTarget,
            onDismiss = { state.eventSink(ChatBarEvent.DismissReminder) },
        )
    }
}
