/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs.list

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.features.matcha.impl.docs.DocNameSheetState
import kotlinx.collections.immutable.persistentListOf

class SpaceDocsListStatePreviewParam : PreviewParameterProvider<SpaceDocsListState> {
    override val values: Sequence<SpaceDocsListState>
        get() = sequenceOf(
            aSpaceDocsListState(),
            aSpaceDocsListState(content = SpaceDocsListContent.Loading, canCreate = false),
            aSpaceDocsListState(content = SpaceDocsListContent.Documents(persistentListOf(DocRow("main", null, null)))),
            aSpaceDocsListState(content = SpaceDocsListContent.LoadError, canCreate = false),
            aSpaceDocsListState(hasUnreadableNames = true, canCreate = false),
            aSpaceDocsListState(content = SpaceDocsListContent.NotSetUp(canSetUp = true), canCreate = false),
            aSpaceDocsListState(content = SpaceDocsListContent.NotSetUp(canSetUp = false), canCreate = false),
            aSpaceDocsListState(canCreate = false),
        )
}

internal fun aSpaceDocsListState(
    content: SpaceDocsListContent = SpaceDocsListContent.Documents(
        persistentListOf(
            DocRow(id = "main", title = null, editedAt = "Yesterday"),
            DocRow(id = "11111111-1111-4111-8111-111111111111", title = "Trip plans", editedAt = "14:05"),
            DocRow(id = "22222222-2222-4222-8222-222222222222", title = "A guide with a name long enough to be cut at the end of the row", editedAt = "Mon"),
        )
    ),
    canCreate: Boolean = true,
    hasUnreadableNames: Boolean = false,
    nameSheet: DocNameSheetState? = null,
) = SpaceDocsListState(
    spaceName = "Matcha team",
    content = content,
    canCreate = canCreate,
    hasUnreadableNames = hasUnreadableNames,
    nameSheet = nameSheet,
    eventSink = {},
)
