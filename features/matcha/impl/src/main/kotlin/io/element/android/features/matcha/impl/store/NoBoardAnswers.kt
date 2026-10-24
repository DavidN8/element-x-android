/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.store

import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes

/**
 * When the homeserver last said a chat has no board, per session and chat, so the Tasks tab doesn't ask every chat again each time
 * it is shown. An answer lasts [LIFETIME]; a board this client points a chat at forgets that chat's answer at once.
 *
 * ponytail: a process-wide map keyed by session; move it to a SessionScope binding if more than the Tasks tab needs it.
 */
internal object NoBoardAnswers {
    private val LIFETIME = 10.minutes
    private val answeredAt = ConcurrentHashMap<Pair<SessionId, RoomId>, Long>()

    /** A monotonic clock in milliseconds: a wall clock set back would keep an answer for far longer than [LIFETIME]. */
    fun now(): Long = System.nanoTime() / 1_000_000

    fun isRecent(sessionId: SessionId, roomId: RoomId, now: Long = now()): Boolean {
        val at = answeredAt[sessionId to roomId] ?: return false
        return now - at in 0 until LIFETIME.inWholeMilliseconds
    }

    fun record(sessionId: SessionId, roomId: RoomId, now: Long = now()) {
        answeredAt[sessionId to roomId] = now
    }

    fun forget(sessionId: SessionId, roomId: RoomId) {
        answeredAt.remove(sessionId to roomId)
    }

    /** Chats this client pointed at a board, until the Tasks tab, which may still hold "no board" for them, takes the news. */
    private val pointed: MutableSet<Pair<SessionId, RoomId>> = ConcurrentHashMap.newKeySet()

    /** This client pointed [roomId] at a board: its answer is forgotten, and the Tasks tab checks the chat again. */
    fun pointed(sessionId: SessionId, roomId: RoomId) {
        forget(sessionId, roomId)
        pointed += sessionId to roomId
    }

    /** Whether this client pointed [roomId] at a board since this was last asked. */
    fun takePointed(sessionId: SessionId, roomId: RoomId): Boolean = pointed.remove(sessionId to roomId)
}
