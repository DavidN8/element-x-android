/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.protocol

import io.element.android.libraries.core.extensions.runCatchingExceptions
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull

const val REL_TYPE_REFERENCE = "m.reference"
const val REL_TYPE_THREAD = "m.thread"

/**
 * A decrypted Matrix event read from its raw JSON (as given by the SDK's debug info), with only the fields the protocol needs.
 */
data class RawEvent(
    val type: String,
    val content: JsonObject,
    val eventId: String?,
    val senderId: String?,
    val timestamp: Long?,
    val stateKey: String?,
    val isRedacted: Boolean,
) {
    companion object {
        /** Parses a full event JSON object, or returns null when it is not one. */
        fun parse(json: String?): RawEvent? {
            if (json.isNullOrBlank()) return null
            val root = runCatchingExceptions { Json.parseToJsonElement(json).jsonObject }.getOrNull() ?: return null
            val type = root.stringOrNull("type") ?: return null
            val unsigned = root.objectOrNull("unsigned")
            return RawEvent(
                type = type,
                content = root.objectOrNull("content") ?: JsonObject(emptyMap()),
                eventId = root.stringOrNull("event_id"),
                senderId = root.stringOrNull("sender"),
                timestamp = root.longOrNull("origin_server_ts"),
                stateKey = root.stringOrNull("state_key"),
                isRedacted = unsigned?.containsKey("redacted_because") == true,
            )
        }
    }
}

/** The event id an `m.reference` relation in this content points at, if any. */
fun JsonObject.referenceTarget(): String? {
    val relation = objectOrNull("m.relates_to") ?: return null
    return relation.stringOrNull("event_id")?.takeIf { relation.stringOrNull("rel_type") == REL_TYPE_REFERENCE }
}

internal fun JsonObject.stringOrNull(key: String): String? = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content

internal fun JsonObject.booleanOrNull(key: String): Boolean? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.booleanOrNull

/** An integral JSON number; fractional numbers and strings are rejected. */
internal fun JsonObject.longOrNull(key: String): Long? = (this[key] as? JsonPrimitive)?.takeIf { !it.isString }?.longOrNull

internal fun JsonObject.objectOrNull(key: String): JsonObject? = this[key] as? JsonObject
