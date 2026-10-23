/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.matcha

import io.element.android.features.matcha.api.MatchaThreadInfo
import io.element.android.libraries.matrix.api.core.ThreadId
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf

/**
 * What the chat screen shows of the Matcha workspace features; the chat has none of it while the feature is off.
 */
data class MatchaChatState(
    /** The chat uses named threads or a board, so the chip bar is drawn; otherwise its entries live in the top bar's menu. */
    val hasChatBar: Boolean,
    /** The Matcha names and archive flags of the chat's threads. */
    val threads: ImmutableMap<ThreadId, MatchaThreadInfo>,
)

fun aMatchaChatState(
    hasChatBar: Boolean = true,
    threads: ImmutableMap<ThreadId, MatchaThreadInfo> = persistentMapOf(),
) = MatchaChatState(
    hasChatBar = hasChatBar,
    threads = threads,
)
