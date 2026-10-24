/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appconfig

import io.element.android.libraries.matrix.api.core.UserId

/**
 * Matcha workspace features (named threads, task boards, reminders, documents), shared with the Matcha web client.
 * The event types and limits are a wire protocol: they must stay identical to the web client's.
 */
object MatchaConfig {
    /** Encrypted timeline patch carrying a thread's shared name and archive state, related to the thread root by m.reference. */
    const val EVENT_THREAD_METADATA = "com.matchahoneylatte.thread.metadata"

    /** Legacy, read-only state event with a thread's archive flag; the state key is the thread root id. */
    const val STATE_THREAD_ARCHIVE = "com.matchahoneylatte.thread_archive"

    /** State event, empty state key, pointing at the room's board root event. */
    const val STATE_KANBAN = "com.matchahoneylatte.kanban"

    /** Timeline event used as the board root that card revisions relate to. */
    const val EVENT_KANBAN_BOARD = "com.matchahoneylatte.kanban.board"

    /** A complete card revision, related to the board root by m.reference. */
    const val EVENT_KANBAN_CARD = "com.matchahoneylatte.kanban.card"

    /** A Yjs document update frame, related to the room's m.room.create event by m.reference. */
    const val EVENT_DOCS_UPDATE = "com.matchahoneylatte.docs.update"

    /** Timeline event types the Matcha features read; they are hidden from the regular timelines. */
    val MESSAGE_LIKE_EVENT_TYPES = setOf(
        EVENT_THREAD_METADATA,
        EVENT_KANBAN_BOARD,
        EVENT_KANBAN_CARD,
        EVENT_DOCS_UPDATE,
    )

    /** State event types the Matcha features read. */
    val STATE_EVENT_TYPES = setOf(STATE_KANBAN, STATE_THREAD_ARCHIVE)

    /** The localpart of the bot that schedules and delivers reminders sent as `!remind` commands; each homeserver runs its own. */
    const val REMINDER_BOT_LOCALPART = "matcha-reminder"

    /** The reminder bot of the homeserver of [sessionId]: `@matcha-reminder:<server name>`. */
    fun reminderBotUserId(sessionId: UserId): UserId = UserId("@$REMINDER_BOT_LOCALPART:${sessionId.value.substringAfter(':')}")

    const val THREAD_NAME_MAX_LENGTH = 100
    const val CARD_TITLE_MAX_LENGTH = 200
    const val CARD_DESCRIPTION_MAX_LENGTH = 4000
    const val DOCUMENT_NAME_MAX_LENGTH = 100
    const val REMINDER_MESSAGE_MAX_LENGTH = 2000
    const val REMINDER_MAX_DELAY_DAYS = 365

    /** Raw Yjs bytes per document frame; leaves room for base64, JSON and the encryption envelope under 64 KiB. */
    const val DOCS_CHUNK_BYTES = 16 * 1024
    const val DOCS_MAX_CHUNKS = 256
}
