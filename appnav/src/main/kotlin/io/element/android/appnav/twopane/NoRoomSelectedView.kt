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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.atomic.molecules.IconTitleSubtitleMolecule
import io.element.android.libraries.designsystem.components.BigIcon
import io.element.android.libraries.designsystem.modifiers.backgroundVerticalGradient
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.ui.strings.CommonStrings

/**
 * Shown in the detail pane of the two-pane layout when no room is open.
 *
 * Like WhatsApp's empty pane it has no header of its own: only the band that continues the list pane's header, and a
 * centred message.
 */
@Composable
fun NoRoomSelectedView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ElementTheme.colors.bgCanvasDefault),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .backgroundVerticalGradient()
                .statusBarsPadding()
                .height(TopAppBarDefaults.TopAppBarExpandedHeight),
        )
        IconTitleSubtitleMolecule(
            title = stringResource(CommonStrings.common_no_chat_selected),
            subTitle = stringResource(CommonStrings.common_no_room_selected),
            iconStyle = BigIcon.Style.Default(CompoundIcons.Chat()),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
        )
    }
}

@PreviewsDayNight
@Composable
internal fun NoRoomSelectedViewPreview() = ElementPreview {
    NoRoomSelectedView()
}
