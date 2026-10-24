/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.matcha

import io.element.android.features.matcha.api.MatchaReminderTarget
import io.element.android.features.matcha.api.MatchaThreadInfo
import io.element.android.libraries.matrix.api.core.ThreadId
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf

/**
 * What the chat screen shows of the Matcha workspace features; the chat has none of it while the feature is off.
 */
data class MatchaChatState(
    /** The chat has named threads, so the chip bar is drawn; otherwise its entries live in the top bar's menu. */
    val hasChatBar: Boolean,
    /** To do and In progress tasks on the chat's board, counted on the top bar's board button; 0 shows no count. */
    val openTaskCount: Int,
    /** The Matcha names and archive flags of the chat's threads. */
    val threads: ImmutableMap<ThreadId, MatchaThreadInfo>,
    /** What the open "Remind me" sheet is about, or null while it is closed. */
    val reminderTarget: MatchaReminderTarget?,
    /** "Remind me" is offered: the user can post here and, in a DM, the reminder bot has already joined. */
    val canRemind: Boolean,
)

fun aMatchaChatState(
    hasChatBar: Boolean = true,
    openTaskCount: Int = 0,
    threads: ImmutableMap<ThreadId, MatchaThreadInfo> = persistentMapOf(),
    reminderTarget: MatchaReminderTarget? = null,
    canRemind: Boolean = true,
) = MatchaChatState(
    hasChatBar = hasChatBar,
    openTaskCount = openTaskCount,
    threads = threads,
    reminderTarget = reminderTarget,
    canRemind = canRemind,
)
