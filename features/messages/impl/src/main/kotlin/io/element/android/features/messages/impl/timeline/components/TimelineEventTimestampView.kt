/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.R
import io.element.android.features.messages.impl.timeline.TimelineEvent
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.event.isEdited
import io.element.android.features.messages.impl.timeline.model.event.isRedacted
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.bubbleMetaText
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.readTick
import io.element.android.libraries.matrix.api.timeline.item.event.LocalEventSendState
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.libraries.designsystem.R as DesignSystemR

/**
 * The meta row at the bottom-end of a bubble: [Edited] [pin] time [send state].
 *
 * @param isPinned whether the event is pinned in the room.
 * @param onMedia true when the row sits on the dark scrim of an image or a video, where it is drawn in white.
 */
@Composable
fun TimelineEventTimestampView(
    event: TimelineItem.Event,
    eventSink: (TimelineEvent.TimelineItemEvent) -> Unit,
    modifier: Modifier = Modifier,
    isPinned: Boolean = false,
    onMedia: Boolean = false,
    isLayoutDirectionMismatched: Boolean = false,
) {
    val formattedTime = event.sentTime
    val hasError = event.failedToSend
    val isMessageEdited = event.content.isEdited()
    val isMessageRedacted = event.content.isRedacted()
    // Inside a bubble the time never turns red: a failed send shows the red error icon instead.
    val tint = if (onMedia) Color.White else ElementTheme.colors.bubbleMetaText

    val shield = event.messageShield
    val isVerifiedUserSendFailure = event.localSendState is LocalEventSendState.Failed.VerifiedUser
    val onClickLabel = when {
        hasError -> stringResource(CommonStrings.a11y_view_details)
        shield != null -> stringResource(CommonStrings.a11y_view_details)
        isVerifiedUserSendFailure -> stringResource(CommonStrings.action_open_context_menu)
        else -> null
    }
    val clickableModifier = remember(event) {
        when {
            hasError -> {
                Modifier.clickable(
                    onClickLabel = onClickLabel,
                ) {
                    eventSink(TimelineEvent.ShowSendFailureDialog(event))
                }
            }
            shield != null -> {
                Modifier.clickable(
                    onClickLabel = onClickLabel,
                ) {
                    eventSink(TimelineEvent.ShowShieldDialog(shield))
                }
            }
            else -> Modifier
        }
    }

    val padding = if (!isLayoutDirectionMismatched) {
        PaddingValues(start = TimelineEventTimestampViewDefaults.spacing)
    } else {
        PaddingValues(end = TimelineEventTimestampViewDefaults.spacing)
    }

    Row(
        modifier = Modifier
            .padding(padding)
            // For a better click target, make the corners rounded
            .clip(RoundedCornerShape(8.dp))
            .then(clickableModifier)
            .then(modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isMessageEdited) {
            Text(
                stringResource(CommonStrings.common_edited_suffix),
                style = ElementTheme.typography.fontBodyXsRegular,
                color = tint,
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        if (isPinned) {
            Icon(
                imageVector = CompoundIcons.PinSolid(),
                contentDescription = stringResource(CommonStrings.common_pinned),
                tint = tint,
                modifier = Modifier.size(12.dp),
            )
            Spacer(modifier = Modifier.width(2.dp))
        }
        Text(
            formattedTime,
            style = ElementTheme.typography.fontBodyXsRegular,
            color = tint,
        )
        if (hasError) {
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                imageVector = CompoundIcons.ErrorSolid(),
                contentDescription = stringResource(id = CommonStrings.common_sending_failed),
                tint = ElementTheme.colors.iconCriticalPrimary,
                modifier = Modifier.size(15.dp, 18.dp),
            )
        } else if (event.isMine) {
            SendStateTick(
                sendState = event.tickState(),
                tint = tint,
            )
        }

        if (!isMessageRedacted) {
            shield?.let { shield ->
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = shield.toIcon(),
                    contentDescription = stringResource(id = CommonStrings.a11y_encryption_details),
                    modifier = Modifier.size(15.dp),
                    tint = shield.toIconColor(),
                )
                Spacer(modifier = Modifier.width(4.dp))
            }
        }
    }
}

/** What the tick after the time of your own message says. There is no "delivered" state: Matrix has no delivery receipts. */
internal enum class TickState {
    Sending,
    Sent,
    Read,
}

internal fun TimelineItem.Event.tickState(): TickState = when {
    localSendState is LocalEventSendState.Sending -> TickState.Sending
    // A local echo without an event id has not reached the server yet.
    eventId == null && localSendState !is LocalEventSendState.Sent -> TickState.Sending
    isReadByOthers -> TickState.Read
    else -> TickState.Sent
}

@Composable
private fun SendStateTick(
    sendState: TickState,
    tint: Color,
) {
    Spacer(modifier = Modifier.width(3.dp))
    when (sendState) {
        TickState.Sending -> Icon(
            imageVector = CompoundIcons.Time(),
            contentDescription = stringResource(CommonStrings.common_sending),
            tint = tint,
            modifier = Modifier.size(12.dp),
        )
        TickState.Sent -> Icon(
            imageVector = CompoundIcons.Check(),
            contentDescription = stringResource(CommonStrings.common_sent),
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        TickState.Read -> Icon(
            resourceId = DesignSystemR.drawable.ic_matcha_done_all,
            contentDescription = stringResource(R.string.a11y_matcha_message_read),
            tint = ElementTheme.colors.readTick,
            modifier = Modifier.size(16.dp),
        )
    }
}

@PreviewsDayNight
@Composable
internal fun TimelineEventTimestampViewPreview(@PreviewParameter(
    TimelineItemEventForTimestampViewPreviewParam::class
) event: TimelineItem.Event) = ElementPreview {
    TimelineEventTimestampView(
        event = event,
        eventSink = {},
    )
}

object TimelineEventTimestampViewDefaults {
    val spacing = 16.dp
}
