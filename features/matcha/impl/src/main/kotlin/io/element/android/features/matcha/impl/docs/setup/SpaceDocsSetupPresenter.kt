/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs.setup

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import io.element.android.libraries.architecture.Presenter
import kotlinx.coroutines.launch

/** The one-time Turn on docs screen: confirms encryption when it is off, then turns docs on. */
class SpaceDocsSetupPresenter(
    private val setup: SpaceDocsSetup,
    private val onDone: () -> Unit,
) : Presenter<SpaceDocsSetupState> {
    @Composable
    override fun present(): SpaceDocsSetupState {
        val coroutineScope = rememberCoroutineScope()
        var isWorking by remember { mutableStateOf(false) }
        var hasFailed by remember { mutableStateOf(false) }
        var showEncryptionDialog by remember { mutableStateOf(false) }

        suspend fun turnOn() {
            isWorking = true
            hasFailed = false
            setup.turnOn().fold(
                onSuccess = { onDone() },
                onFailure = { hasFailed = true },
            )
            isWorking = false
        }

        fun handleEvent(event: SpaceDocsSetupEvent) {
            when (event) {
                SpaceDocsSetupEvent.TurnOn -> if (!isWorking) {
                    coroutineScope.launch {
                        isWorking = true
                        if (setup.status().isEncrypted) {
                            turnOn()
                        } else {
                            isWorking = false
                            showEncryptionDialog = true
                        }
                    }
                }
                SpaceDocsSetupEvent.ConfirmEncryption -> {
                    showEncryptionDialog = false
                    coroutineScope.launch { turnOn() }
                }
                SpaceDocsSetupEvent.DismissEncryption -> showEncryptionDialog = false
            }
        }

        return SpaceDocsSetupState(
            isWorking = isWorking,
            hasFailed = hasFailed,
            showEncryptionDialog = showEncryptionDialog,
            eventSink = ::handleEvent,
        )
    }
}
