/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components.virtual

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.messages.impl.R
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.systemPillBackground

private const val MAX_UNREAD_COUNT = 99

/**
 * The unread band: a full-width band above the first unread message, saying how many messages are unread.
 */
@Composable
internal fun TimelineItemReadMarkerView(
    unreadCount: Int,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .heightIn(min = 28.dp)
            .background(ElementTheme.colors.systemPillBackground)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        val text = when {
            unreadCount <= 0 -> stringResource(id = R.string.screen_room_timeline_read_marker_title)
            unreadCount > MAX_UNREAD_COUNT -> stringResource(id = R.string.screen_matcha_room_unread_band_many, MAX_UNREAD_COUNT)
            else -> pluralStringResource(id = R.plurals.screen_matcha_room_unread_band, count = unreadCount, unreadCount)
        }
        Text(
            text = text,
            style = ElementTheme.typography.fontBodySmMedium,
            color = ElementTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

@PreviewsDayNight
@Composable
internal fun TimelineItemReadMarkerViewPreview() = ElementPreview {
    Column {
        TimelineItemReadMarkerView(unreadCount = 0)
        TimelineItemReadMarkerView(unreadCount = 1)
        TimelineItemReadMarkerView(unreadCount = 12)
        TimelineItemReadMarkerView(unreadCount = 140)
    }
}
