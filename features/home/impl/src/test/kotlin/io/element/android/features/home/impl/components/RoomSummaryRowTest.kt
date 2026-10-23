/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class)

package io.element.android.features.home.impl.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import io.element.android.features.home.impl.model.aRoomListRoomSummary
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test

class RoomSummaryRowTest : RobolectricTest() {
    @Test
    fun `the open chat is announced as selected on its clickable row`() = runAndroidComposeUiTest<ComponentActivity> {
        setContent {
            RoomSummaryRow(
                room = aRoomListRoomSummary(name = "Selected room"),
                hideInviteAvatars = false,
                isInviteSeen = true,
                isSelected = true,
                onClick = {},
                eventSink = {},
            )
        }
        // The merged node of the clickable row, the one TalkBack focuses.
        onNodeWithText("Selected room").assertIsSelected()
    }

    @Test
    fun `other chats are not announced as selected`() = runAndroidComposeUiTest<ComponentActivity> {
        setContent {
            RoomSummaryRow(
                room = aRoomListRoomSummary(name = "Other room"),
                hideInviteAvatars = false,
                isInviteSeen = true,
                isSelected = false,
                onClick = {},
                eventSink = {},
            )
        }
        onNodeWithText("Other room").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Selected))
    }
}
