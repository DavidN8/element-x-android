/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.room

import io.element.android.libraries.matrix.api.room.RawEventsPage
import org.matrix.rustcomponents.sdk.RelationType
import org.matrix.rustcomponents.sdk.Room
import org.matrix.rustcomponents.sdk.RoomMessageLikeEvent
import java.lang.reflect.InvocationTargetException
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

/**
 * Calls `Room.fetchRelationsJson`, which only the Matcha-patched Rust SDK has (see docs/matcha/rust-sdk/). The published SDK
 * lacks it, so it is looked up by reflection: builds against either SDK compile, and the published one fails here cleanly.
 *
 * ponytail: reflection keeps the published SDK buildable; call it directly once the method is upstream or the patched AAR is
 * the only SDK this fork builds with.
 */
internal suspend fun Room.fetchReferencingEventsJson(eventId: String, from: String?, limit: Int): RawEventsPage {
    // Kotlin mangles the name because the limit is an unsigned value class, e.g. fetchRelationsJson-<hash>.
    val method = fetchRelationsJsonMethod
        ?: throw UnsupportedOperationException("This Rust SDK can't fetch related events as JSON; build with the Matcha SDK patch")
    val page = try {
        suspendCoroutineUninterceptedOrReturn<Any?> { continuation ->
            method.invoke(this, eventId, RelationType.REFERENCE, from, limit, continuation)
        }
    } catch (e: InvocationTargetException) {
        throw e.cause ?: e
    } ?: error("No relations page")
    @Suppress("UNCHECKED_CAST")
    val events = page.javaClass.getMethod("getEvents").invoke(page) as List<String>
    val nextBatch = page.javaClass.getMethod("getNextBatch").invoke(page) as String?
    return RawEventsPage(events = events, nextBatch = nextBatch)
}

private val fetchRelationsJsonMethod by lazy {
    Room::class.java.methods.firstOrNull { it.name == "fetchRelationsJson" || it.name.startsWith("fetchRelationsJson-") }
}

/** The event as raw JSON in the shape of a timeline event, for parsers that read `type`, `event_id`, `sender` and `content`. */
internal fun RoomMessageLikeEvent.toRawJson(type: String): String =
    """{"type":${type.jsonQuoted()},"event_id":${eventId.jsonQuoted()},"sender":${sender.jsonQuoted()},""" +
        """"origin_server_ts":$timestamp,"content":$contentJson}"""

private fun String.jsonQuoted(): String = buildString(length + 2) {
    append('"')
    for (c in this@jsonQuoted) {
        when {
            c == '"' -> append("\\\"")
            c == '\\' -> append("\\\\")
            c < ' ' -> append("\\u%04x".format(c.code))
            else -> append(c)
        }
    }
    append('"')
}
