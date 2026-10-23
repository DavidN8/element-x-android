/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.topbars

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import io.element.android.features.messages.impl.R
import io.element.android.features.messages.impl.typing.TypingNotificationState
import io.element.android.features.messages.impl.typing.computeTypingNotificationText
import io.element.android.libraries.ui.strings.CommonPlurals
import io.element.android.libraries.ui.strings.CommonStrings

/**
 * The second line of a chat's top bar.
 *
 * @property text the text to show.
 * @property isTyping true when [text] says who is typing; it is then drawn in the accent colour.
 */
@Immutable
data class TopBarSubtitle(
    val text: AnnotatedString,
    val isTyping: Boolean,
)

/**
 * Builds the subtitle of the room top bar: who is typing, else the member count of a group, else nothing (a DM, or a tombstoned room).
 */
@Composable
internal fun roomTopBarSubtitle(
    isDm: Boolean,
    isTombstoned: Boolean,
    joinedMemberCount: Long,
    typingNotificationState: TypingNotificationState,
): TopBarSubtitle? {
    if (isTombstoned) return null
    typingSubtitle(isDm, typingNotificationState)?.let { return it }
    if (isDm || joinedMemberCount <= 0) return null
    val count = joinedMemberCount.toInt()
    return TopBarSubtitle(
        text = AnnotatedString(pluralStringResource(CommonPlurals.common_matcha_member_count, count, count)),
        isTyping = false,
    )
}

/**
 * Builds the subtitle of a thread shown with the room's top bar: who is typing, else the thread's name, else "Untitled thread".
 */
@Composable
internal fun threadTopBarSubtitle(
    threadName: String?,
    isDm: Boolean,
    typingNotificationState: TypingNotificationState,
): TopBarSubtitle {
    return typingSubtitle(isDm, typingNotificationState) ?: TopBarSubtitle(
        text = AnnotatedString(threadName ?: stringResource(CommonStrings.common_matcha_thread_untitled)),
        isTyping = false,
    )
}

@Composable
private fun typingSubtitle(
    isDm: Boolean,
    typingNotificationState: TypingNotificationState,
): TopBarSubtitle? {
    val typingMembers = typingNotificationState.typingMembers
    if (!typingNotificationState.renderTypingNotifications || typingMembers.isEmpty()) return null
    val text = if (isDm) {
        AnnotatedString(stringResource(R.string.screen_matcha_room_typing_dm))
    } else {
        computeTypingNotificationText(typingMembers)
    }
    return TopBarSubtitle(text = text, isTyping = true)
}
