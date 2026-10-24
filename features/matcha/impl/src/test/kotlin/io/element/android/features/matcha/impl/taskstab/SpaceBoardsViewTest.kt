/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class)

package io.element.android.features.matcha.impl.taskstab

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import io.element.android.features.matcha.impl.R
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarMessage
import io.element.android.tests.testutils.robolectric.RobolectricTest
import io.element.android.tests.testutils.setSafeContent
import org.junit.Test

class SpaceBoardsViewTest : RobolectricTest() {
    @Test
    fun `a task's snackbar shows on the boards`() = runAndroidComposeUiTest {
        // On a phone Home isn't drawn under the boards, so they show Archive's Undo themselves.
        setSafeContent {
            SpaceBoardsView(
                state = aTasksTabState(spaceName = "Acme"),
                snackbarMessage = SnackbarMessage(R.string.screen_matcha_tasks_archived_snackbar),
                onBackClick = {},
            )
        }
        val text = activity!!.getString(R.string.screen_matcha_tasks_archived_snackbar)
        waitUntil { onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }
}
