/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.pinned.banner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import im.vector.app.features.analytics.plan.Interaction
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.utils.annotatedTextWithBold
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.services.analytics.compose.LocalAnalyticsService
import io.element.android.services.analyticsproviders.api.trackers.captureInteraction

@Composable
fun PinnedMessagesBannerView(
    state: PinnedMessagesBannerState,
    onClick: (EventId) -> Unit,
    onViewAllClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        PinnedMessagesBannerState.Hidden -> Unit
        is PinnedMessagesBannerState.Visible -> {
            PinnedMessagesBannerRow(
                state = state,
                onClick = onClick,
                onViewAllClick = onViewAllClick,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun PinnedMessagesBannerRow(
    state: PinnedMessagesBannerState.Visible,
    onClick: (EventId) -> Unit,
    onViewAllClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val analyticsService = LocalAnalyticsService.current
    // A flat strip under the top bar: no border and no page indicator.
    Row(
        modifier = modifier
            .background(color = ElementTheme.colors.bgCanvasDefault)
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable {
                if (state is PinnedMessagesBannerState.Loaded) {
                    analyticsService.captureInteraction(Interaction.Name.PinnedMessageBannerClick)
                    onClick(state.currentPinnedMessage.eventId)
                    state.eventSink(PinnedMessagesBannerEvent.MoveToNextPinned)
                }
            }
            .padding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = CompoundIcons.Pin(),
            contentDescription = null,
            tint = ElementTheme.colors.iconSecondary,
            modifier = Modifier
                .padding(start = 16.dp, end = 12.dp)
                .size(16.dp)
        )
        PinnedMessageItem(
            index = state.currentPinnedMessageIndex(),
            totalCount = state.pinnedMessagesCount(),
            message = state.formattedMessage(),
            modifier = Modifier.weight(1f)
        )
        ViewAllButton(
            state = state,
            onViewAllClick = {
                onViewAllClick()
                analyticsService.captureInteraction(Interaction.Name.PinnedMessageBannerViewAllButton)
            },
        )
    }
}

@Composable
private fun ViewAllButton(
    state: PinnedMessagesBannerState,
    onViewAllClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val text = if (state is PinnedMessagesBannerState.Loaded) {
        stringResource(id = CommonStrings.screen_room_pinned_banner_view_all_button_title)
    } else {
        ""
    }
    TextButton(
        text = text,
        showProgress = state is PinnedMessagesBannerState.Loading,
        onClick = onViewAllClick,
        modifier = modifier,
    )
}

@Composable
private fun PinnedMessageItem(
    index: Int,
    totalCount: Int,
    message: AnnotatedString?,
    modifier: Modifier = Modifier,
) {
    val countMessage = stringResource(id = CommonStrings.screen_room_pinned_banner_indicator, index + 1, totalCount)
    val fullCountMessage = stringResource(id = CommonStrings.screen_room_pinned_banner_indicator_description, countMessage)
    Column(modifier = modifier) {
        AnimatedVisibility(totalCount > 1) {
            Text(
                text = annotatedTextWithBold(
                    text = fullCountMessage,
                    boldText = countMessage,
                ),
                style = ElementTheme.typography.fontBodySmMedium,
                color = ElementTheme.colors.textActionAccent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (message != null) {
            Text(
                text = message,
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textPrimary,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
            )
        }
    }
}

@Stable
internal interface PinnedMessagesBannerViewScrollBehavior {
    val isVisible: Boolean
    val nestedScrollConnection: NestedScrollConnection
}

internal object PinnedMessagesBannerViewDefaults {
    @Composable
    fun rememberScrollBehavior(pinnedMessagesCount: Int): PinnedMessagesBannerViewScrollBehavior = remember(pinnedMessagesCount) {
        ExitOnScrollBehavior()
    }
}

private class ExitOnScrollBehavior : PinnedMessagesBannerViewScrollBehavior {
    override var isVisible by mutableStateOf(true)
    override val nestedScrollConnection: NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
            if (available.y < -1) {
                isVisible = true
            }
            if (available.y > 1) {
                isVisible = false
            }
            return Offset.Zero
        }
    }
}

@PreviewsDayNight
@Composable
internal fun PinnedMessagesBannerViewPreview(@PreviewParameter(
    PinnedMessagesBannerStatePreviewParam::class
) state: PinnedMessagesBannerState) = ElementPreview {
    PinnedMessagesBannerView(
        state = state,
        onClick = {},
        onViewAllClick = {},
    )
}
