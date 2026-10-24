/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.room

import io.element.android.libraries.matrix.api.room.RawEventsPage
import io.element.android.libraries.matrix.api.room.RawRelationType
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
internal suspend fun Room.fetchRelatedEventsJson(eventId: String, relation: RawRelationType, from: String?, limit: Int): RawEventsPage {
    // Kotlin mangles the name because the limit is an unsigned value class, e.g. fetchRelationsJson-<hash>.
    val method = fetchRelationsJsonMethod
        ?: throw UnsupportedOperationException("This Rust SDK can't fetch related events as JSON; build with the Matcha SDK patch")
    val page = try {
        suspendCoroutineUninterceptedOrReturn<Any?> { continuation ->
            val relationType = when (relation) {
                RawRelationType.Reference -> RelationType.REFERENCE
                RawRelationType.Thread -> RelationType.THREAD
            }
            method.invoke(this, eventId, relationType, from, limit, continuation)
        }
    } catch (e: InvocationTargetException) {
        throw e.cause ?: e
    } ?: error("No relations page")
    @Suppress("UNCHECKED_CAST")
    val events = page.javaClass.getMethod("getEvents").invoke(page) as List<String>
    val nextBatch = page.javaClass.getMethod("getNextBatch").invoke(page) as? String
    return RawEventsPage(events = events, nextBatch = nextBatch)
}

/**
 * Calls `Room.fetchStateEventContentJson`, which only the Matcha-patched Rust SDK has, by reflection like
 * [fetchRelatedEventsJson]; the name differs from the SDK method's so a patched build doesn't shadow it.
 */
internal suspend fun Room.fetchServerStateContentJson(eventType: String, stateKey: String): String? {
    val method = fetchStateEventContentJsonMethod
        ?: throw UnsupportedOperationException("This Rust SDK can't fetch room state from the server; build with the Matcha SDK patch")
    val content = try {
        suspendCoroutineUninterceptedOrReturn<Any?> { continuation ->
            method.invoke(this, eventType, stateKey, continuation)
        }
    } catch (e: InvocationTargetException) {
        throw e.cause ?: e
    }
    return content as? String
}

private val fetchStateEventContentJsonMethod by lazy {
    Room::class.java.methods.firstOrNull { it.name == "fetchStateEventContentJson" }
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
