/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs.notestab

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.matcha.api.MatchaNotesTabRenderer
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.matrix.api.core.RoomId

@ContributesBinding(SessionScope::class)
class DefaultMatchaNotesTabRenderer(
    private val presenter: NotesTabPresenter,
) : MatchaNotesTabRenderer {
    @Composable
    override fun Render(
        contentPadding: PaddingValues,
        onOpenNote: (spaceId: RoomId, documentId: String?) -> Unit,
        modifier: Modifier,
    ) {
        val state = presenter.present(onOpenNote = onOpenNote)
        if (!state.isVisible) return
        NotesTabView(state = state, contentPadding = contentPadding, modifier = modifier)
    }
}
