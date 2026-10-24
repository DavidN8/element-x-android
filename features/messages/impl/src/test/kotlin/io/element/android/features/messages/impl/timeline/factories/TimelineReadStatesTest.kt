/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.factories

import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.timeline.aTimelineItemEvent
import io.element.android.features.messages.impl.timeline.aTimelineItemReadReceipts
import io.element.android.features.messages.impl.timeline.components.receipt.aReadReceiptData
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.event.aTimelineItemStateEventContent
import io.element.android.features.messages.impl.timeline.model.virtual.TimelineItemReadMarkerModel
import io.element.android.libraries.designsystem.components.avatar.anAvatarData
import io.element.android.libraries.matrix.api.core.UniqueId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.room.RoomMembershipState
import io.element.android.libraries.matrix.test.room.aRoomMember
import org.junit.Test

class TimelineReadStatesTest {
    private val me = UserId("@senderId:domain")
    private val alice = UserId("@alice:domain")
    private val bob = UserId("@bob:domain")

    @Test
    fun `own message is read once every other joined member has a receipt on it or a newer event`() {
        val members = listOf(aRoomMember(me), aRoomMember(alice), aRoomMember(bob))
        val items = listOf(
            // Newest first
            anEvent(isMine = false, readers = listOf(bob)),
            anEvent(isMine = true, readers = listOf(alice)),
            anEvent(isMine = true),
        )
        val result = items.withReadStates(members).filterIsInstance<TimelineItem.Event>()
        assertThat(result.map { it.isReadByOthers }).containsExactly(false, true, true).inOrder()
    }

    @Test
    fun `own message is not read while a member has no receipt at or after it`() {
        val members = listOf(aRoomMember(me), aRoomMember(alice), aRoomMember(bob))
        val items = listOf(
            anEvent(isMine = true),
            anEvent(isMine = true, readers = listOf(alice)),
        )
        val result = items.withReadStates(members).filterIsInstance<TimelineItem.Event>()
        assertThat(result.map { it.isReadByOthers }).containsExactly(false, false).inOrder()
    }

    @Test
    fun `members who left, service members and the reminder bot are not waited for`() {
        val members = listOf(
            aRoomMember(me),
            aRoomMember(alice),
            aRoomMember(bob, membership = RoomMembershipState.LEAVE),
            aRoomMember(UserId("@bridge:domain"), isServiceMember = true),
            aRoomMember(UserId("@matcha-reminder:domain")),
        )
        val items = listOf(anEvent(isMine = true, readers = listOf(alice)))
        val result = items.withReadStates(members).filterIsInstance<TimelineItem.Event>()
        assertThat(result.single().isReadByOthers).isTrue()
    }

    @Test
    fun `the reminder bot of another homeserver is not waited for either`() {
        val otherServerBot = UserId("@matcha-reminder:other.org")
        val members = listOf(aRoomMember(me), aRoomMember(alice), aRoomMember(otherServerBot))
        val items = listOf(anEvent(isMine = true, readers = listOf(alice)))
        val result = items.withReadStates(members).filterIsInstance<TimelineItem.Event>()
        assertThat(result.single().isReadByOthers).isTrue()
    }

    @Test
    fun `a room with nobody else, or with more members than the cap, never shows read`() {
        val alone = listOf(anEvent(isMine = true, readers = listOf(alice)))
            .withReadStates(listOf(aRoomMember(me)))
            .filterIsInstance<TimelineItem.Event>()
        assertThat(alone.single().isReadByOthers).isFalse()

        val crowd = List(MAX_MEMBERS_FOR_READ_TICK + 1) { aRoomMember(UserId("@user$it:domain")) } + aRoomMember(me)
        val large = listOf(anEvent(isMine = true, readers = crowd.map { it.userId }))
            .withReadStates(crowd)
            .filterIsInstance<TimelineItem.Event>()
        assertThat(large.single().isReadByOthers).isFalse()
    }

    @Test
    fun `the read marker counts the messages from others newer than it`() {
        val items = listOf(
            anEvent(isMine = false),
            anEvent(isMine = true),
            anEvent(isMine = false, isStateEvent = true),
            anEvent(isMine = false),
            aReadMarker(),
            anEvent(isMine = false),
        )
        val marker = items.withReadStates(emptyList())
            .filterIsInstance<TimelineItem.Virtual>()
            .single()
            .model as TimelineItemReadMarkerModel
        // The state event and your own message do not count.
        assertThat(marker.unreadCount).isEqualTo(2)
    }

    private fun anEvent(
        isMine: Boolean,
        readers: List<UserId> = emptyList(),
        isStateEvent: Boolean = false,
    ): TimelineItem.Event {
        val event = aTimelineItemEvent(
            isMine = isMine,
            readReceiptState = aTimelineItemReadReceipts(
                receipts = readers.mapIndexed { index, userId -> aReadReceiptData(index, avatarData = anAvatarData(id = userId.value)) },
            ),
        )
        return if (isStateEvent) event.copy(content = aTimelineItemStateEventContent()) else event
    }

    private fun aReadMarker() = TimelineItem.Virtual(
        id = UniqueId("marker"),
        model = TimelineItemReadMarkerModel(),
    )
}
