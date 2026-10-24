/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.chatbar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import io.element.android.features.matcha.api.isReminderBotJoined
import io.element.android.libraries.matrix.api.room.JoinedRoom
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Whether "Remind me" is offered in this room: the user can send messages and, in a DM, the reminder bot has already
 * joined, because inviting it would turn the DM into a group.
 */
@Composable
internal fun rememberCanRemind(room: JoinedRoom, canSendMessages: Boolean): Boolean {
    val isDm by remember { room.roomInfoFlow.map { it.isDm }.distinctUntilChanged() }.collectAsState(initial = room.isDm())
    val isBotJoined by produceState(initialValue = false, isDm) {
        if (isDm) value = room.isReminderBotJoined()
    }
    return canSendMessages && (!isDm || isBotJoined)
}
