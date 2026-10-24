/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.factories

import io.element.android.appconfig.MatchaConfig
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemStateContent
import io.element.android.features.messages.impl.timeline.model.virtual.TimelineItemReadMarkerModel
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.RoomMember
import io.element.android.libraries.matrix.api.room.RoomMembershipState

/** Rooms with more joined members than this never show the read tick: it stops at "sent". */
internal const val MAX_MEMBERS_FOR_READ_TICK = 32

/**
 * Fills in what can only be known from the whole timeline, in one pass from the newest item to the oldest:
 * - [TimelineItem.Event.isReadByOthers] on your own messages: every joined member except you and the bots has a read receipt on this message or a newer one.
 *   A DM needs the other member's receipt. Rooms with more than [MAX_MEMBERS_FOR_READ_TICK] joined members never show it.
 * - [TimelineItemReadMarkerModel.unreadCount]: the number of messages from others newer than the read marker (state events do not count).
 *
 * The receiver holds the timeline items ordered from the newest to the oldest, as the timeline renders them.
 */
internal fun List<TimelineItem>.withReadStates(roomMembers: List<RoomMember>): List<TimelineItem> {
    val currentUserId = firstNotNullOfOrNull { (it as? TimelineItem.Event)?.takeIf { event -> event.isMine }?.senderId }
    val readers = roomMembers.requiredReaders(currentUserId)
    val canBeRead = readers.isNotEmpty() && readers.size <= MAX_MEMBERS_FOR_READ_TICK
    val hasRead = HashSet<String>()
    var unreadCount = 0
    var isBeforeReadMarker = true
    return map { item ->
        when (item) {
            is TimelineItem.Event -> {
                item.readReceiptState.receipts.forEach { hasRead.add(it.avatarData.id) }
                if (isBeforeReadMarker && !item.isMine && item.content !is TimelineItemStateContent) unreadCount++
                val isReadByOthers = item.isMine && canBeRead && hasRead.containsAll(readers)
                if (item.isReadByOthers == isReadByOthers) item else item.copy(isReadByOthers = isReadByOthers)
            }
            is TimelineItem.GroupedEvents -> {
                item.aggregatedReadReceipts.forEach { hasRead.add(it.avatarData.id) }
                item
            }
            is TimelineItem.Virtual -> {
                if (isBeforeReadMarker && item.model is TimelineItemReadMarkerModel) {
                    isBeforeReadMarker = false
                    item.copy(model = TimelineItemReadMarkerModel(unreadCount = unreadCount))
                } else {
                    item
                }
            }
        }
    }
}

/** The joined members who must have read a message for it to show as read; bots send no read receipts, so they are left out. */
private fun List<RoomMember>.requiredReaders(currentUserId: UserId?): Set<String> {
    if (currentUserId == null) return emptySet()
    // Service members (MSC4171) and the Matcha reminder bot of any homeserver: in a federated room, a member of another server who set a
    // reminder has invited the bot of their own server, which sends no receipts either.
    return asSequence()
        .filter { it.membership == RoomMembershipState.JOIN }
        .filter { it.userId != currentUserId && !it.isServiceMember && it.userId.extractedDisplayName != MatchaConfig.REMINDER_BOT_LOCALPART }
        .map { it.userId.value }
        .toSet()
}
