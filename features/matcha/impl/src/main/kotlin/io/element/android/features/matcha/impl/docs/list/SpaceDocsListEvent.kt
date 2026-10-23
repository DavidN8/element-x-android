/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs.list

sealed interface SpaceDocsListEvent {
    data class OpenDocument(val documentId: String) : SpaceDocsListEvent
    data object OpenSetup : SpaceDocsListEvent
    data object NewDocument : SpaceDocsListEvent
    data class Rename(val documentId: String) : SpaceDocsListEvent
    data class UpdateName(val name: String) : SpaceDocsListEvent
    data object SubmitName : SpaceDocsListEvent
    data object DismissNameSheet : SpaceDocsListEvent
    data object Retry : SpaceDocsListEvent
}
