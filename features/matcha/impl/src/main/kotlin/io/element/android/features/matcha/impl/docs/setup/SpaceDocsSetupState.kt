/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs.setup

data class SpaceDocsSetupState(
    val isWorking: Boolean,
    val hasFailed: Boolean,
    /** Encryption is off: confirm turning it on, which can't be undone. */
    val showEncryptionDialog: Boolean,
    val eventSink: (SpaceDocsSetupEvent) -> Unit,
)

sealed interface SpaceDocsSetupEvent {
    data object TurnOn : SpaceDocsSetupEvent
    data object ConfirmEncryption : SpaceDocsSetupEvent
    data object DismissEncryption : SpaceDocsSetupEvent
}
