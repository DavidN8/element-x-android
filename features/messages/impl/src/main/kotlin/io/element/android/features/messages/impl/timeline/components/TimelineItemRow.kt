/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.toggleableState
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.messages.impl.timeline.TimelineEvent
import io.element.android.features.messages.impl.timeline.TimelineRoomInfo
import io.element.android.features.messages.impl.timeline.components.event.TimelineItemEventContentView
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.TimelineItemSelectionData
import io.element.android.features.messages.impl.timeline.model.event.RtcNotificationState
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemLegacyCallInviteContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemPollContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemRtcNotificationContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemStateContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemVoiceContent
import io.element.android.features.messages.impl.timeline.protection.TimelineProtectionState
import io.element.android.libraries.designsystem.modifiers.onKeyboardContextMenuAction
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.chatWallpaper
import io.element.android.libraries.designsystem.theme.focusHighlight
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.ui.common.layout.ContentAvoidingLayoutData
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.libraries.ui.utils.a11y.isTalkbackActive
import io.element.android.wysiwyg.link.Link
import kotlinx.coroutines.delay
import kotlin.time.DurationUnit

@Composable
internal fun TimelineItemRow(
    timelineItem: TimelineItem,
    timelineMode: Timeline.Mode,
    timelineRoomInfo: TimelineRoomInfo,
    isLastOutgoingMessage: Boolean,
    timelineProtectionState: TimelineProtectionState,
    focusedEventId: EventId?,
    displayThreadSummaries: Boolean,
    selectionData: TimelineItemSelectionData,
    onUserDataClick: (MatrixUser) -> Unit,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
    onContentClick: (TimelineItem.Event) -> Unit,
    onGalleryItemClick: (TimelineItem.Event, Int) -> Unit,
    onLongClick: (TimelineItem.Event) -> Unit,
    inReplyToClick: (EventId) -> Unit,
    onReactionLongClick: (key: String, TimelineItem.Event) -> Unit,
    onReadReceiptClick: (TimelineItem.Event) -> Unit,
    onJoinCallClick: (isAudioCall: Boolean) -> Unit,
    onSwipeToReply: (TimelineItem.Event) -> Unit,
    eventSink: (TimelineEvent.TimelineItemEvent) -> Unit,
    modifier: Modifier = Modifier,
    eventContentView: @Composable (TimelineItem.Event, Modifier, (ContentAvoidingLayoutData) -> Unit) -> Unit =
        { event, contentModifier, onContentLayoutChange ->
            TimelineItemEventContentView(
                eventId = event.eventId,
                content = event.content,
                timelineProtectionState = timelineProtectionState,
                onContentClick = { onContentClick(event) },
                onGalleryItemClick = { index -> onGalleryItemClick(event, index) },
                onLongClick = { onLongClick(event) },
                onLinkClick = onLinkClick,
                onLinkLongClick = onLinkLongClick,
                eventSink = eventSink,
                modifier = contentModifier,
                onContentLayoutChange = onContentLayoutChange,
            )
        },
) {
    val isSelectionModeActive = selectionData.isSelectionModeActive
    val isSelected = selectionData.isSelected
    val backgroundModifier = when {
        timelineItem.isEvent(focusedEventId) && !isSelectionModeActive -> Modifier.focusedEvent()
        // A selected row gets a full-width tonal green tint.
        isSelectionModeActive && isSelected -> Modifier.background(ElementTheme.colors.bgAccentRest.copy(alpha = SELECTED_ROW_TINT_ALPHA))
        else -> Modifier
    }
    val selectableEvent = (timelineItem as? TimelineItem.Event)?.takeIf { selectionData.canBeSelected }
    Box(modifier = modifier.then(backgroundModifier)) {
        TimelineItemRowContent(
            modifier = Modifier.fillMaxWidth(),
            selectionData = selectionData,
            timelineItem = timelineItem,
            timelineMode = timelineMode,
            timelineRoomInfo = timelineRoomInfo,
            timelineProtectionState = timelineProtectionState,
            isLastOutgoingMessage = isLastOutgoingMessage,
            focusedEventId = focusedEventId,
            displayThreadSummaries = displayThreadSummaries,
            onUserDataClick = onUserDataClick,
            onLinkClick = onLinkClick,
            onLinkLongClick = onLinkLongClick,
            onContentClick = onContentClick,
            onGalleryItemClick = onGalleryItemClick,
            onLongClick = onLongClick,
            inReplyToClick = inReplyToClick,
            onReactionLongClick = onReactionLongClick,
            onReadReceiptClick = onReadReceiptClick,
            onJoinCallClick = onJoinCallClick,
            onSwipeToReply = onSwipeToReply,
            eventSink = eventSink,
            eventContentView = eventContentView,
        )
        if (isSelectionModeActive) {
            // In selection mode the whole row toggles selection.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable(enabled = selectionData.canBeSelected, role = Role.Checkbox) {
                        if (selectableEvent != null) {
                            onContentClick(selectableEvent)
                        }
                    }
                    .semantics { hideFromAccessibility() }
            )
        }
    }
}

/** Width reserved for the selection indicator : the row content slides by this amount. */
internal val SELECTION_COLUMN_WIDTH = 36.dp

@Suppress("LongMethod")
@Composable
private fun TimelineItemRowContent(
    timelineItem: TimelineItem,
    timelineMode: Timeline.Mode,
    timelineRoomInfo: TimelineRoomInfo,
    isLastOutgoingMessage: Boolean,
    timelineProtectionState: TimelineProtectionState,
    focusedEventId: EventId?,
    displayThreadSummaries: Boolean,
    onUserDataClick: (MatrixUser) -> Unit,
    onLinkClick: (Link) -> Unit,
    onLinkLongClick: (Link) -> Unit,
    onContentClick: (TimelineItem.Event) -> Unit,
    onGalleryItemClick: (TimelineItem.Event, Int) -> Unit,
    onLongClick: (TimelineItem.Event) -> Unit,
    inReplyToClick: (EventId) -> Unit,
    onReactionLongClick: (key: String, TimelineItem.Event) -> Unit,
    onReadReceiptClick: (TimelineItem.Event) -> Unit,
    onJoinCallClick: (isAudioCall: Boolean) -> Unit,
    onSwipeToReply: (TimelineItem.Event) -> Unit,
    eventSink: (TimelineEvent.TimelineItemEvent) -> Unit,
    modifier: Modifier = Modifier,
    selectionData: TimelineItemSelectionData = TimelineItemSelectionData(),
    eventContentView: @Composable (TimelineItem.Event, Modifier, (ContentAvoidingLayoutData) -> Unit) -> Unit,
) {
    Box(modifier) {
        when (timelineItem) {
            is TimelineItem.Virtual -> {
                TimelineItemVirtualRow(
                    virtual = timelineItem,
                    timelineRoomInfo = timelineRoomInfo,
                    eventSink = eventSink,
                )
            }
            is TimelineItem.Event -> {
                when (timelineItem.content) {
                    is TimelineItemStateContent, is TimelineItemLegacyCallInviteContent -> {
                        TimelineItemStateEventRow(
                            event = timelineItem,
                            onClick = { onContentClick(timelineItem) },
                            onLongClick = { onLongClick(timelineItem) },
                            timelineProtectionState = timelineProtectionState,
                            eventSink = eventSink,
                        )
                    }
                    is TimelineItemRtcNotificationContent -> {
                        when (timelineItem.content.state) {
                            is RtcNotificationState.Active -> ActiveCallTimelineItemView(
                                timelineRoomInfo = timelineRoomInfo,
                                event = timelineItem,
                                state = timelineItem.content.state,
                                isLastOutgoingMessage = isLastOutgoingMessage,
                                onLongClick = onLongClick,
                                onReadReceiptsClick = onReadReceiptClick,
                                onJoinCallClick = onJoinCallClick,
                            )
                            is RtcNotificationState.Started, is RtcNotificationState.Declined ->
                                TimelineItemCallNotifyView(
                                    timelineRoomInfo = timelineRoomInfo,
                                    event = timelineItem,
                                    content = timelineItem.content,
                                    state = timelineItem.content.state,
                                    isLastOutgoingMessage = isLastOutgoingMessage,
                                    onLongClick = onLongClick,
                                    onReadReceiptsClick = onReadReceiptClick,
                                )
                        }
                    }
                    else -> {
                        val a11yVoiceMessage = stringResource(CommonStrings.a11y_voice_message)
                        val isSelectionToggle = selectionData.isSelectionModeActive && selectionData.canBeSelected
                        TimelineItemEventRow(
                            modifier = Modifier
                                .semantics(mergeDescendants = true) {
                                    contentDescription = if (timelineItem.content is TimelineItemVoiceContent) {
                                        val voiceMessageText = String.format(a11yVoiceMessage, timelineItem.content.duration.toString(DurationUnit.MINUTES))
                                        "${timelineItem.safeSenderName}, $voiceMessageText"
                                    } else {
                                        timelineItem.safeSenderName
                                    }
                                    if (isSelectionToggle) {
                                        toggleableState = ToggleableState(selectionData.isSelected)
                                    }
                                    // For Polls, allow the answers to be traversed by Talkback
                                    isTraversalGroup = timelineItem.content is TimelineItemPollContent ||
                                        timelineItem.failedToSend ||
                                        timelineItem.messageShield != null
                                    // TODO Also set to true when the event has link(s)
                                }
                                // Custom clickable that applies over the whole item for accessibility
                                .then(
                                    when {
                                        !isTalkbackActive() -> Modifier
                                        // While selecting, the row only toggles the selection. The context menu cannot be opened, so the long press
                                        // that opens it is neither offered nor advertised.
                                        isSelectionToggle -> Modifier.clickable { onContentClick(timelineItem) }
                                        else -> Modifier
                                            .combinedClickable(
                                                onClick = { onContentClick(timelineItem) },
                                                onLongClick = { onLongClick(timelineItem) },
                                                onLongClickLabel = stringResource(CommonStrings.action_open_context_menu),
                                            )
                                            .onKeyboardContextMenuAction { onLongClick(timelineItem) }
                                    }
                                ),
                            event = timelineItem,
                            timelineMode = timelineMode,
                            timelineRoomInfo = timelineRoomInfo,
                            timelineProtectionState = timelineProtectionState,
                            displayThreadSummaries = displayThreadSummaries,
                            onEventClick = { onContentClick(timelineItem) },
                            onLongClick = { onLongClick(timelineItem) },
                            onLinkClick = onLinkClick,
                            onLinkLongClick = onLinkLongClick,
                            onUserDataClick = onUserDataClick,
                            inReplyToClick = inReplyToClick,
                            onReactionLongClick = onReactionLongClick,
                            onSwipeToReply = { onSwipeToReply(timelineItem) },
                            onGalleryItemClick = { index -> onGalleryItemClick(timelineItem, index) },
                            eventSink = eventSink,
                            selectionData = selectionData,
                            eventContentView = { contentModifier, onContentLayoutChange ->
                                eventContentView(timelineItem, contentModifier, onContentLayoutChange)
                            },
                        )
                    }
                }
            }
            is TimelineItem.GroupedEvents -> {
                TimelineItemGroupedEventsRow(
                    timelineItem = timelineItem,
                    timelineMode = timelineMode,
                    timelineRoomInfo = timelineRoomInfo,
                    timelineProtectionState = timelineProtectionState,
                    isLastOutgoingMessage = isLastOutgoingMessage,
                    focusedEventId = focusedEventId,
                    displayThreadSummaries = displayThreadSummaries,
                    onClick = onContentClick,
                    onLongClick = onLongClick,
                    inReplyToClick = inReplyToClick,
                    onUserDataClick = onUserDataClick,
                    onLinkClick = onLinkClick,
                    onLinkLongClick = onLinkLongClick,
                    onReactionLongClick = onReactionLongClick,
                    onReadReceiptClick = onReadReceiptClick,
                    eventSink = eventSink,
                )
            }
        }
    }
}

private const val SELECTED_ROW_TINT_ALPHA = 0.15f
private const val FOCUS_HIGHLIGHT_HOLD_MILLIS = 1_000L
private const val FOCUS_HIGHLIGHT_FADE_MILLIS = 600

/**
 * A full-width neutral tint behind the message the user jumped to. It holds for a second, then fades out.
 */
@Suppress("ModifierComposable")
@Composable
private fun Modifier.focusedEvent(): Modifier {
    val highlightColor = ElementTheme.colors.focusHighlight
    val alpha = remember { Animatable(1f) }
    LaunchedEffect(Unit) {
        delay(FOCUS_HIGHLIGHT_HOLD_MILLIS)
        alpha.animateTo(0f, animationSpec = tween(FOCUS_HIGHLIGHT_FADE_MILLIS))
    }
    return drawBehind {
        drawRect(highlightColor.copy(alpha = highlightColor.alpha * alpha.value))
    }
}

@PreviewsDayNight
@Composable
internal fun FocusedEventPreview() = ElementPreview {
    Column(
        modifier = Modifier
            .background(ElementTheme.colors.chatWallpaper)
            .padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .focusedEvent(),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(80.dp)
                .background(ElementTheme.colors.bgAccentRest.copy(alpha = SELECTED_ROW_TINT_ALPHA)),
        )
    }
}
