/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2022-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.messages.impl.timeline.aTimelineItemReactions
import io.element.android.features.messages.impl.timeline.model.AggregatedReaction
import io.element.android.features.messages.impl.timeline.model.TimelineItemReactions
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.chatWallpaper
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.composerSurface
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrixmedia.api.MediaRequestData
import kotlinx.collections.immutable.toImmutableList

internal const val REACTION_IMAGE_ASPECT_RATIO = 1.0f

private const val MAX_REACTIONS_IN_PILL = 3
private val PILL_HEIGHT = 24.dp

/**
 * The reactions of a message as one pill that overlaps the bottom of the bubble: up to three emoji and the total count.
 * Tapping it opens the reaction summary, where each reaction and who sent it are listed.
 *
 * The pill is hidden from screen readers: the reactions are announced with the message.
 */
@Composable
fun TimelineItemReactionsView(
    reactionsState: TimelineItemReactions,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reactions = reactionsState.reactions
    if (reactions.isEmpty()) return
    val totalCount = reactions.sumOf { it.count }
    val isLight = ElementTheme.colors.isLight
    Row(
        modifier = modifier
            .semantics { hideFromAccessibility() }
            // Compose extends the touch area of this 27 dp pill to the 48 dp minimum, without changing its layout.
            .height(PILL_HEIGHT + RING_WIDTH * 2)
            .then(if (isLight) Modifier.shadow(1.dp, CircleShape) else Modifier)
            .border(BorderStroke(RING_WIDTH, ElementTheme.colors.chatWallpaper), CircleShape)
            .padding(RING_WIDTH)
            .clip(CircleShape)
            .background(ElementTheme.colors.composerSurface)
            .combinedClickable(onClick = onClick, onLongClick = onClick)
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        reactions.take(MAX_REACTIONS_IN_PILL).forEach { reaction ->
            ReactionKey(reaction)
        }
        if (totalCount > 1) {
            Text(
                modifier = Modifier.padding(start = 2.dp),
                text = totalCount.toString(),
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
    }
}

private val RING_WIDTH = 1.5.dp

@Composable
private fun ReactionKey(reaction: AggregatedReaction) {
    // Check if this is a custom reaction (MSC4027)
    if (reaction.key.startsWith("mxc://")) {
        AsyncImage(
            modifier = Modifier
                .height(16.dp)
                .aspectRatio(REACTION_IMAGE_ASPECT_RATIO, false),
            model = MediaRequestData(MediaSource(reaction.key), MediaRequestData.Kind.Content),
            contentDescription = null
        )
    } else {
        Text(
            text = reaction.displayKey,
            maxLines = 1,
            style = ElementTheme.typography.fontBodyMdRegular.copy(
                fontSize = 14.sp,
                lineHeight = 18.sp,
            ),
        )
    }
}

@PreviewsDayNight
@Composable
internal fun TimelineItemReactionsViewPreview() = ElementPreview {
    val reactions = aTimelineItemReactions().reactions
    Column(
        modifier = Modifier
            .background(ElementTheme.colors.chatWallpaper)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(1, 3, reactions.size).forEach { count ->
            TimelineItemReactionsView(
                reactionsState = TimelineItemReactions(reactions.take(count).toImmutableList()),
                onClick = {},
            )
        }
    }
}
