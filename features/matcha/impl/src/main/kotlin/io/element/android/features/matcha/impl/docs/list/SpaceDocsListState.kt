/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs.list

import io.element.android.features.matcha.impl.docs.DocNameSheetState
import kotlinx.collections.immutable.ImmutableList

data class SpaceDocsListState(
    val spaceName: String?,
    val content: SpaceDocsListContent,
    /** Shows the New doc button: the user may write docs and the space is encrypted. */
    val canCreate: Boolean,
    /** Some names can't be read yet: the rows show what is known. */
    val hasUnreadableNames: Boolean,
    val nameSheet: DocNameSheetState?,
    val eventSink: (SpaceDocsListEvent) -> Unit,
)

sealed interface SpaceDocsListContent {
    data object Loading : SpaceDocsListContent

    data object LoadError : SpaceDocsListContent

    /** Docs are not turned on for the space; an admin can turn them on. */
    data class NotSetUp(val canSetUp: Boolean) : SpaceDocsListContent

    /** Space notes first, then the other docs, most recently edited first. */
    data class Documents(val documents: ImmutableList<DocRow>) : SpaceDocsListContent
}

data class DocRow(
    val id: String,
    /** Null for Space notes before anyone names it. */
    val title: String?,
    /** When the doc was last edited, already formatted, if known. */
    val editedAt: String?,
)
