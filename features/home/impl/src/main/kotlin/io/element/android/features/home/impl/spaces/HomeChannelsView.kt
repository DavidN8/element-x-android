/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.spaces

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.home.impl.R
import io.element.android.features.home.impl.components.RoomSummaryPlaceholderRow
import io.element.android.features.home.impl.components.RoomSummaryRow
import io.element.android.features.home.impl.contentType
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.features.home.impl.roomlist.RoomListEvent
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.CurrentUserMembership
import io.element.android.libraries.matrix.api.spaces.SpaceRoom
import io.element.android.libraries.matrix.ui.components.SpaceRoomItemView
import io.element.android.libraries.matrix.ui.model.getAvatarData
import io.element.android.libraries.designsystem.R as DesignSystemR

/**
 * The Channels tab, in place of the Spaces tab while the Matcha workspaces are on. Like WhatsApp's Communities tab: each joined
 * space is a header row with its channels (group chats) under it, then the channels that are in no space. Pending space invites
 * stay at the top, as on the Spaces tab. Under each header, All notes, All boards and All chats open the space's notes, the boards
 * of its channels, and the space itself.
 *
 * The open chat, [selectedRoomId], is highlighted when it is shown next to the list, and the chat list's events,
 * [roomListEventSink], let a long press on a channel open the same menu as in Chats.
 */
@Composable
fun HomeChannelsView(
    state: HomeSpacesState,
    lazyListState: LazyListState,
    contentPadding: PaddingValues,
    selectedRoomId: RoomId?,
    showUnreadCount: Boolean,
    showAllActivity: Boolean,
    onSpaceClick: (RoomId) -> Unit,
    onSpaceNotesClick: (RoomId) -> Unit,
    onSpaceBoardsClick: (RoomId) -> Unit,
    onRoomClick: (RoomId) -> Unit,
    onCreateSpaceClick: () -> Unit,
    roomListEventSink: (RoomListEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val channels = state.channels
    val invites = state.spaceRooms.filter { it.state == CurrentUserMembership.INVITED }
    if (channels != null && channels.isEmpty && invites.isEmpty()) {
        EmptySpaceHomeView(
            title = stringResource(R.string.screen_home_channels_empty_title),
            onCreateSpaceClick = onCreateSpaceClick,
            onExploreClick = {},
            canExploreSpaces = false,
            modifier = modifier.padding(contentPadding),
        )
        return
    }
    LazyColumn(
        modifier = modifier,
        state = lazyListState,
        contentPadding = contentPadding,
    ) {
        if (channels == null) {
            // Like the chat list while it loads.
            items(SKELETON_ROW_COUNT) { RoomSummaryPlaceholderRow() }
            return@LazyColumn
        }
        items(items = invites, key = { it.roomId.value }) { spaceRoom ->
            SpaceRoomItemView(
                spaceRoom = spaceRoom,
                showUnreadIndicator = spaceRoom.roomId !in state.seenSpaceInvites,
                hideAvatars = state.hideInvitesAvatar,
                onClick = { onSpaceClick(spaceRoom.roomId) },
                onLongClick = {},
            )
        }
        fun channelRows(groupKey: String, rooms: List<RoomListRoomSummary>, isCompact: Boolean) = items(
            items = rooms,
            // A chat can be in several spaces, so the key also names the group.
            key = { room -> "$groupKey/${room.roomId.value}" },
            contentType = { room -> room.contentType() },
        ) { room ->
            RoomSummaryRow(
                room = room,
                hideInviteAvatars = false,
                isInviteSeen = true,
                isSelected = room.roomId == selectedRoomId,
                onClick = { onRoomClick(it.roomId) },
                showUnreadCount = showUnreadCount,
                showAllActivity = showAllActivity,
                isCompact = isCompact,
                eventSink = roomListEventSink,
            )
        }
        channels.spaces.forEachIndexed { index, spaceChannels ->
            val spaceId = spaceChannels.space.roomId
            if (index > 0 || invites.isNotEmpty()) sectionBand("band/${spaceId.value}")
            item(key = "space/${spaceId.value}") {
                SpaceChannelsHeaderRow(
                    space = spaceChannels.space,
                    channelCount = spaceChannels.channels.size,
                    onClick = { onSpaceClick(spaceId) },
                )
            }
            item(key = "shortcuts/${spaceId.value}") {
                Column {
                    SpaceShortcutRow(ImageVector.vectorResource(DesignSystemR.drawable.ic_matcha_notes), R.string.screen_home_channels_all_notes) {
                        onSpaceNotesClick(spaceId)
                    }
                    SpaceShortcutRow(ImageVector.vectorResource(DesignSystemR.drawable.ic_matcha_board), R.string.screen_home_channels_all_boards) {
                        onSpaceBoardsClick(spaceId)
                    }
                    // The same as the header, under a name that says what it opens.
                    SpaceShortcutRow(CompoundIcons.Chat(), R.string.screen_home_channels_all_chats) { onSpaceClick(spaceId) }
                }
            }
            channelRows(groupKey = spaceChannels.space.roomId.value, rooms = spaceChannels.channels, isCompact = true)
        }
        if (channels.otherChannels.isNotEmpty()) {
            // Without any space, the channels are the whole list and need no section.
            if (channels.spaces.isNotEmpty()) {
                sectionBand("band/other")
                item(key = "other") { OtherChannelsHeader() }
            } else if (invites.isNotEmpty()) {
                sectionBand("band/other")
            }
            channelRows(groupKey = "other", rooms = channels.otherChannels, isCompact = false)
        }
    }
}

private const val SKELETON_ROW_COUNT = 8

/** An 8dp band in the raised fill between the blocks, like the space screen's sections and WhatsApp's communities. */
private fun LazyListScope.sectionBand(key: String) = item(key = key) {
    Spacer(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(ElementTheme.colors.bgSubtleSecondary)
    )
}

/** A space at the head of its channels: its rounded-square avatar, its name and how many of its channels the user is in. */
@Composable
private fun SpaceChannelsHeaderRow(
    space: SpaceRoom,
    channelCount: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            // TalkBack users can jump from space to space.
            .semantics { heading() }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(
            avatarData = space.getAvatarData(AvatarSize.SpaceListItem),
            avatarType = AvatarType.Space(),
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = space.displayName,
                style = ElementTheme.typography.fontBodyLgMedium,
                color = ElementTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(R.plurals.screen_home_channels_count, channelCount, channelCount),
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
                maxLines = 1,
            )
        }
    }
}

/** A space's notes, boards or chats: an icon tile lined up with the channels' avatars, and a regular label under the bolder names. */
@Composable
private fun SpaceShortcutRow(icon: ImageVector, @StringRes labelRes: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(ElementTheme.colors.bgSubtleSecondary, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = icon, contentDescription = null, tint = ElementTheme.colors.iconSecondary, modifier = Modifier.size(24.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = stringResource(labelRes),
            style = ElementTheme.typography.fontBodyLgRegular,
            color = ElementTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun OtherChannelsHeader() {
    Text(
        text = stringResource(R.string.screen_home_channels_other_section),
        style = ElementTheme.typography.fontBodyMdMedium,
        color = ElementTheme.colors.textSecondary,
        modifier = Modifier
            .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
            .semantics { heading() },
    )
}

@PreviewsDayNight
@Composable
internal fun HomeChannelsViewPreview(
    @PreviewParameter(ChannelsHomeSpacesStatePreviewParam::class) state: HomeSpacesState,
) = ElementPreview {
    HomeChannelsView(
        state = state,
        lazyListState = rememberLazyListState(),
        contentPadding = PaddingValues(bottom = 88.dp),
        selectedRoomId = null,
        showUnreadCount = true,
        showAllActivity = false,
        onSpaceClick = {},
        onSpaceNotesClick = {},
        onSpaceBoardsClick = {},
        onRoomClick = {},
        onCreateSpaceClick = {},
        roomListEventSink = {},
    )
}
