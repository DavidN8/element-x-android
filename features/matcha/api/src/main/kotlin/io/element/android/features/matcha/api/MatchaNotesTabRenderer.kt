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

/** The Notes tab on Home: the notes (Space documents) of every space the user is in. Session scoped. Renders nothing when the feature is off. */
interface MatchaNotesTabRenderer {
    @Composable
    fun Render(
        /** Padding from the Home scaffold (top bar and bottom navigation). */
        contentPadding: PaddingValues,
        /** Opens a note of a space, or the space's notes list (for instance to turn notes on) when the document id is null. */
        onOpenNote: (spaceId: RoomId, documentId: String?) -> Unit,
        modifier: Modifier,
    )
}
