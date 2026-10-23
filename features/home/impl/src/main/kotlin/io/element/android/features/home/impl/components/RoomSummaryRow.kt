/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Arrangement.Absolute.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.home.impl.R
import io.element.android.features.home.impl.model.LatestEvent
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.features.home.impl.model.RoomListRoomSummaryPreviewParam
import io.element.android.features.home.impl.model.RoomSummaryDisplayType
import io.element.android.features.home.impl.model.aRoomListRoomSummary
import io.element.android.features.home.impl.roomlist.RoomListEvent
import io.element.android.libraries.core.extensions.orEmpty
import io.element.android.libraries.core.extensions.toSafeLength
import io.element.android.libraries.designsystem.atomic.atoms.UnreadIndicatorAtom
import io.element.android.libraries.designsystem.atomic.molecules.InviteButtonsRowMolecule
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.modifiers.onKeyboardContextMenuAction
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.mutedBadge
import io.element.android.libraries.designsystem.theme.roomListRoomMessage
import io.element.android.libraries.designsystem.theme.roomListRoomMessageDate
import io.element.android.libraries.designsystem.theme.roomListRoomName
import io.element.android.libraries.designsystem.theme.rowSelected
import io.element.android.libraries.matrix.api.notification.CallIntent
import io.element.android.libraries.matrix.api.user.DisplayedStatus
import io.element.android.libraries.matrix.ui.components.DisplayNameWithStatus
import io.element.android.libraries.matrix.ui.components.InviteSenderView
import io.element.android.libraries.matrix.ui.model.InviteSender
import io.element.android.libraries.ui.strings.CommonStrings
import timber.log.Timber

internal val minHeight = 76.dp

@Composable
internal fun RoomSummaryRow(
    room: RoomListRoomSummary,
    hideInviteAvatars: Boolean,
    isInviteSeen: Boolean,
    isSelected: Boolean,
    onClick: (RoomListRoomSummary) -> Unit,
    modifier: Modifier = Modifier,
    showUnreadCount: Boolean = false,
    showAllActivity: Boolean = false,
    eventSink: (RoomListEvent) -> Unit,
) {
    val background = if (isSelected) Modifier.background(ElementTheme.colors.rowSelected) else Modifier
    Box(modifier = modifier.then(background)) {
        when (room.displayType) {
            RoomSummaryDisplayType.PLACEHOLDER -> {
                RoomSummaryPlaceholderRow()
            }
            RoomSummaryDisplayType.INVITE -> {
                RoomSummaryScaffoldRow(
                    room = room,
                    hideAvatarImage = hideInviteAvatars,
                    onClick = onClick,
                    onLongClick = {
                        Timber.d("Long click on invite room")
                    },
                ) {
                    InviteNameAndIndicatorRow(name = room.name, isInviteSeen = isInviteSeen)
                    InviteSubtitle(isDm = room.isDm, inviteSender = room.inviteSender)
                    if (!room.isDm && room.inviteSender != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        InviteSenderView(
                            modifier = Modifier.fillMaxWidth(),
                            inviteSender = room.inviteSender,
                            hideAvatarImage = hideInviteAvatars
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    InviteButtonsRowMolecule(
                        onAcceptClick = {
                            eventSink(RoomListEvent.AcceptInvite(room))
                        },
                        onDeclineClick = {
                            eventSink(RoomListEvent.ShowDeclineInviteMenu(room))
                        }
                    )
                }
            }
            RoomSummaryDisplayType.ROOM -> {
                RoomSummaryScaffoldRow(
                    room = room,
                    onClick = onClick,
                    onLongClick = {
                        eventSink(RoomListEvent.ShowContextMenu(room))
                    },
                ) {
                    NameAndTimestampRow(
                        name = room.name,
                        timestamp = room.timestamp,
                        isUnread = room.hasNewContent && !room.isMuted,
                        dmUserStatus = room.dmUserStatus,
                    )
                    MessagePreviewAndIndicatorRow(room = room, showUnreadCount = showUnreadCount, showAllActivity = showAllActivity)
                }
            }
            RoomSummaryDisplayType.KNOCKED -> {
                RoomSummaryScaffoldRow(
                    room = room,
                    onClick = onClick,
                    onLongClick = {
                        Timber.d("Long click on knocked room")
                    },
                ) {
                    NameAndTimestampRow(
                        name = room.name,
                        timestamp = null,
                        isUnread = false,
                        dmUserStatus = null,
                    )
                    if (room.canonicalAlias != null) {
                        Text(
                            text = room.canonicalAlias.value,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = ElementTheme.typography.fontBodyMdRegular,
                            color = ElementTheme.colors.textSecondary,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    Text(
                        text = stringResource(id = R.string.screen_roomlist_knock_event_sent_description),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = ElementTheme.typography.fontBodyMdRegular,
                        color = ElementTheme.colors.textSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun RoomSummaryScaffoldRow(
    room: RoomListRoomSummary,
    onClick: (RoomListRoomSummary) -> Unit,
    onLongClick: (RoomListRoomSummary) -> Unit,
    modifier: Modifier = Modifier,
    hideAvatarImage: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val clickModifier = Modifier
        .combinedClickable(
            onClick = { onClick(room) },
            onLongClick = { onLongClick(room) },
            onLongClickLabel = stringResource(CommonStrings.action_open_context_menu),
            indication = ripple(),
            interactionSource = remember { MutableInteractionSource() }
        )
        .onKeyboardContextMenuAction { onLongClick(room) }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .then(clickModifier)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .height(IntrinsicSize.Min),
    ) {
        Avatar(
            avatarData = room.avatarData,
            avatarType = if (room.isSpace) {
                AvatarType.Space(isTombstoned = room.isTombstoned)
            } else {
                AvatarType.Room(
                    heroes = room.heroes,
                    isTombstoned = room.isTombstoned,
                )
            },
            hideImage = hideAvatarImage,
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
    }
}

@Composable
private fun NameAndTimestampRow(
    name: String?,
    timestamp: String?,
    isUnread: Boolean,
    dmUserStatus: DisplayedStatus?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = spacedBy(16.dp)
    ) {
        val displayName = name?.toSafeLength(ellipsize = true) ?: stringResource(id = CommonStrings.common_no_room_name)
        DisplayNameWithStatus(
            name = displayName,
            status = dmUserStatus,
            modifier = Modifier.weight(1f),
            style = ElementTheme.typography.fontBodyLgMedium,
            nameColor = ElementTheme.colors.roomListRoomName,
            nameFontStyle = FontStyle.Italic.takeIf { name == null },
        )
        // Timestamp, green while the chat has unread messages, like the badge
        Text(
            text = timestamp ?: "",
            style = if (isUnread) ElementTheme.typography.fontBodySmMedium else ElementTheme.typography.fontBodySmRegular,
            color = if (isUnread) {
                ElementTheme.colors.textActionAccent
            } else {
                ElementTheme.colors.roomListRoomMessageDate
            },
        )
    }
}

@Composable
private fun InviteSubtitle(
    isDm: Boolean,
    inviteSender: InviteSender?,
    modifier: Modifier = Modifier
) {
    val subtitle = if (isDm) {
        inviteSender?.userId?.value
    } else {
        null
    }
    if (subtitle != null) {
        Text(
            modifier = modifier.clipToBounds(),
            text = subtitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.roomListRoomMessage,
        )
    }
}

@Composable
private fun MessagePreviewAndIndicatorRow(
    room: RoomListRoomSummary,
    showUnreadCount: Boolean,
    showAllActivity: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
    ) {
        if (room.isTombstoned) {
            Text(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.screen_roomlist_tombstoned_room_description),
                color = ElementTheme.colors.roomListRoomMessage,
                style = ElementTheme.typography.fontBodyMdRegular,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            if (room.latestEvent is LatestEvent.Error) {
                Icon(
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(16.dp),
                    imageVector = CompoundIcons.ErrorSolid(),
                    // The last message contains the error.
                    contentDescription = null,
                    tint = ElementTheme.colors.iconCriticalPrimary,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    modifier = Modifier.weight(1f),
                    text = stringResource(CommonStrings.common_message_failed_to_send),
                    color = ElementTheme.colors.textCriticalPrimary,
                    style = ElementTheme.typography.fontBodyMdRegular,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                if (room.latestEvent is LatestEvent.Sending) {
                    Icon(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(16.dp),
                        imageVector = CompoundIcons.Time(),
                        contentDescription = stringResource(CommonStrings.common_sending),
                        tint = ElementTheme.colors.iconTertiary,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                val messagePreview = room.latestEvent.content()
                val annotatedMessagePreview = messagePreview as? AnnotatedString ?: AnnotatedString(text = messagePreview.orEmpty().toString())
                Text(
                    modifier = Modifier
                        .weight(1f)
                        .clipToBounds(),
                    text = annotatedMessagePreview,
                    color = ElementTheme.colors.roomListRoomMessage,
                    style = ElementTheme.typography.fontBodyMdRegular,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        // Call and unread
        Row(
            modifier = Modifier
                // Used to force this line to be read aloud earlier than the latest event when using Talkback
                .zIndex(-1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (room.hasRoomCall) {
                OnGoingCallIcon(
                    color = if (room.isHighlighted) ElementTheme.colors.textActionAccent else ElementTheme.colors.iconQuaternary,
                    isAudio = room.activeCallIntent == CallIntent.AUDIO
                )
            }
            if (room.isMuted) {
                NotificationOffIndicatorAtom()
            }
            if (room.numberOfUnreadMentions > 0) {
                MentionIndicatorAtom()
            }
            if (room.hasNewContent) {
                UnreadBadge(
                    count = room.unreadIndicatorCount(showUnreadCount = showUnreadCount, showAllActivity = showAllActivity),
                    isMuted = room.isMuted,
                )
            }
        }
    }
}

/**
 * The unread count pill: green, or grey for a muted chat, like WhatsApp.
 */
@Composable
private fun UnreadBadge(
    count: Long?,
    isMuted: Boolean,
) {
    // A count is at least a 20dp pill, a dot keeps its own size.
    val sizeModifier = if (count != null && count > 0) Modifier.sizeIn(minWidth = 20.dp, minHeight = 20.dp) else Modifier
    UnreadIndicatorAtom(
        modifier = sizeModifier,
        count = count,
        color = if (isMuted) ElementTheme.colors.mutedBadge else ElementTheme.colors.iconAccentPrimary,
        contentColor = if (isMuted) ElementTheme.colors.textSecondary else ElementTheme.colors.textOnSolidPrimary,
        contentDescription = if (count != null && count > 0) {
            pluralStringResource(R.plurals.a11y_matcha_unread_messages, count.toInt(), count.toInt())
        } else {
            stringResource(CommonStrings.a11y_notifications_new_messages)
        },
    )
}

/**
 * Computes the value to pass to [UnreadIndicatorAtom] as `count`.
 * - `null`: nothing is displayed.
 * - `0`: dot indicator without a count.
 * - `> 0`: the number to display.
 */
internal fun RoomListRoomSummary.unreadIndicatorCount(
    showUnreadCount: Boolean,
    showAllActivity: Boolean,
): Long? {
    return when {
        // Show only unread notifications when the room is not muted and there are unread notifications
        !isMuted && numberOfUnreadNotifications > 0 -> if (showUnreadCount) numberOfUnreadNotifications else 0
        // A muted room does not notify, so it counts its unread messages instead, on a grey badge
        isMuted && showAllActivity && numberOfUnreadMessages > 0 -> if (showUnreadCount) numberOfUnreadMessages else 0
        // Otherwise, show a dot for unread messages when showAllActivity is true and there are unread messages
        showAllActivity -> if (numberOfUnreadMessages > 0) 0 else null
        // Or don't display anything
        else -> null
    }
}

@Composable
private fun InviteNameAndIndicatorRow(
    name: String?,
    isInviteSeen: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            modifier = Modifier
                .weight(1f)
                .clipToBounds(),
            style = ElementTheme.typography.fontBodyLgMedium,
            text = name?.toSafeLength(ellipsize = true) ?: stringResource(id = CommonStrings.common_no_room_name),
            fontStyle = FontStyle.Italic.takeIf { name == null },
            color = ElementTheme.colors.roomListRoomName,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!isInviteSeen) {
            UnreadIndicatorAtom(count = 0)
        }
    }
}

@Composable
private fun OnGoingCallIcon(
    color: Color,
    isAudio: Boolean
) {
    Icon(
        modifier = Modifier.size(16.dp),
        imageVector = if (isAudio) CompoundIcons.VoiceCallSolid() else CompoundIcons.VideoCallSolid(),
        contentDescription = stringResource(CommonStrings.a11y_notifications_ongoing_call),
        tint = color,
    )
}

@Composable
private fun NotificationOffIndicatorAtom() {
    Icon(
        modifier = Modifier.size(16.dp),
        contentDescription = stringResource(CommonStrings.a11y_notifications_muted),
        imageVector = CompoundIcons.NotificationsOffSolid(),
        tint = ElementTheme.colors.iconQuaternary,
    )
}

@Composable
private fun MentionIndicatorAtom() {
    Box(
        modifier = Modifier
            .size(20.dp)
            .background(ElementTheme.colors.iconAccentPrimary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            modifier = Modifier.size(14.dp),
            contentDescription = stringResource(R.string.a11y_matcha_mentioned),
            imageVector = CompoundIcons.Mention(),
            tint = ElementTheme.colors.iconOnSolidPrimary,
        )
    }
}

@PreviewsDayNight
@Composable
internal fun RoomSummaryRowPreview(@PreviewParameter(RoomListRoomSummaryPreviewParam::class) data: RoomListRoomSummary) = ElementPreview {
    RoomSummaryRow(
        room = data,
        hideInviteAvatars = false,
        // Set isInviteSeen to true for the preview when the room has name "Bob"
        isInviteSeen = data.name == "Bob",
        isSelected = false,
        onClick = {},
        eventSink = {},
        showUnreadCount = data.numberOfUnreadNotifications > 0,
        showAllActivity = true,
    )
}

@PreviewsDayNight
@Composable
internal fun RoomSummaryRowSelectedPreview() = ElementPreview {
    RoomSummaryRow(
        room = aRoomListRoomSummary(),
        hideInviteAvatars = false,
        isInviteSeen = false,
        isSelected = true,
        onClick = {},
        eventSink = {},
    )
}
