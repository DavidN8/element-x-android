/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components

import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import io.element.android.features.messages.impl.timeline.aTimelineItemEvent
import io.element.android.features.messages.impl.timeline.aTimelineItemReactions
import io.element.android.features.messages.impl.timeline.model.TimelineItemGroupPosition
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemStickerContent
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.matrix.ui.messages.reply.InReplyToDetailsPreviewParam

/** Stickers have no bubble; a sticker that quotes a message keeps the bubble for its quote. */
@PreviewsDayNight
@Composable
internal fun TimelineItemEventRowWithStickerPreview() = ElementPreview {
    Column {
        sequenceOf(false, true).forEach { isMine ->
            ATimelineItemEventRow(
                event = aTimelineItemEvent(
                    isMine = isMine,
                    timelineItemReactions = aTimelineItemReactions(count = 0),
                    content = aTimelineItemStickerContent(aspectRatio = 1f),
                    groupPosition = TimelineItemGroupPosition.First,
                ),
            )
            ATimelineItemEventRow(
                event = aTimelineItemEvent(
                    isMine = isMine,
                    timelineItemReactions = aTimelineItemReactions(count = 0),
                    content = aTimelineItemStickerContent(aspectRatio = 1f),
                    inReplyTo = InReplyToDetailsPreviewParam().values.first(),
                    groupPosition = TimelineItemGroupPosition.Last,
                ),
            )
        }
    }
}
