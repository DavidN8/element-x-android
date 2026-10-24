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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.home.api.HomeTab
import io.element.android.libraries.designsystem.atomic.molecules.IconTitleSubtitleMolecule
import io.element.android.libraries.designsystem.components.BigIcon
import io.element.android.libraries.designsystem.modifiers.backgroundVerticalGradient
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.libraries.designsystem.R as DesignSystemR

/**
 * Shown in the detail pane of the two-pane layout when nothing is open, with a message that depends on the selected Home [tab].
 *
 * Like WhatsApp's empty pane it has no header of its own: only the band that continues the list pane's header, and a
 * centred message.
 */
@Composable
fun NoRoomSelectedView(
    tab: HomeTab,
    modifier: Modifier = Modifier,
) {
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
        val (title, subtitle) = when (tab) {
            HomeTab.Chats -> CommonStrings.common_no_chat_selected to CommonStrings.common_no_room_selected
            HomeTab.Tasks -> CommonStrings.common_matcha_no_task_selected to CommonStrings.common_matcha_select_task
            HomeTab.Spaces -> CommonStrings.common_matcha_no_space_selected to CommonStrings.common_matcha_select_space
            HomeTab.Notes -> CommonStrings.common_matcha_no_note_selected to CommonStrings.common_matcha_select_note
            HomeTab.Channels -> CommonStrings.common_matcha_no_channel_selected to CommonStrings.common_matcha_select_channel
        }
        IconTitleSubtitleMolecule(
            title = stringResource(title),
            subTitle = stringResource(subtitle),
            iconStyle = BigIcon.Style.Default(
                when (tab) {
                    HomeTab.Chats -> CompoundIcons.Chat()
                    HomeTab.Tasks -> ImageVector.vectorResource(DesignSystemR.drawable.ic_matcha_board)
                    HomeTab.Spaces -> CompoundIcons.Space()
                    HomeTab.Notes -> ImageVector.vectorResource(DesignSystemR.drawable.ic_matcha_notes)
                    HomeTab.Channels -> CompoundIcons.Room()
                }
            ),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
        )
    }
}

@PreviewsDayNight
@Composable
internal fun NoRoomSelectedViewPreview(@PreviewParameter(HomeTabPreviewParam::class) tab: HomeTab) = ElementPreview {
    NoRoomSelectedView(tab = tab)
}
