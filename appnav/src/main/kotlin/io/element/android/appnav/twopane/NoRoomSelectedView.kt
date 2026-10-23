/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appnav.twopane

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings

/**
 * Shown in the detail pane of the two-pane layout when no room is open.
 */
@Composable
fun NoRoomSelectedView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ElementTheme.colors.bgCanvasDefault)
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = stringResource(CommonStrings.common_no_room_selected),
            color = ElementTheme.colors.textSecondary,
            style = ElementTheme.typography.fontBodyLgRegular,
            textAlign = TextAlign.Center,
        )
    }
}

@PreviewsDayNight
@Composable
internal fun NoRoomSelectedViewPreview() = ElementPreview {
    NoRoomSelectedView()
}
