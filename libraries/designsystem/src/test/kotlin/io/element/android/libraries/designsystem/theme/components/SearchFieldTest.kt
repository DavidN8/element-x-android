/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class)

package io.element.android.libraries.designsystem.theme.components

import androidx.activity.ComponentActivity
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test

class SearchFieldTest : RobolectricTest() {
    @Test
    fun `the clear button shows while the field is not focused, and clears the text`() =
        runAndroidComposeUiTest<ComponentActivity> {
            val state = TextFieldState("Query")
            setContent {
                SearchField(state = state)
            }
            val clear = onNodeWithContentDescription(activity!!.getString(CommonStrings.action_clear))
            clear.assertIsDisplayed()
            clear.performClick()
            assertThat(state.text.toString()).isEmpty()
        }
}
