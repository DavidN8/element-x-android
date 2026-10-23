/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.reminders

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.matcha.api.MatchaReminderRenderer
import io.element.android.features.matcha.api.MatchaReminderTarget
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.api.FeatureFlags

@ContributesBinding(RoomScope::class)
class DefaultMatchaReminderRenderer(
    private val presenterFactory: RoomReminderPresenter.Factory,
    private val featureFlagService: FeatureFlagService,
) : MatchaReminderRenderer {
    @Composable
    override fun Render(target: MatchaReminderTarget?, onDismiss: () -> Unit) {
        val isEnabled by remember { featureFlagService.isFeatureEnabledFlow(FeatureFlags.MatchaWorkspaces) }.collectAsState(initial = false)
        if (target == null || !isEnabled) return
        // A new target starts a new sheet.
        key(target) {
            val presenter = remember { presenterFactory.create(target) }
            RoomReminderView(state = presenter.present(), onDismiss = onDismiss)
        }
    }
}
