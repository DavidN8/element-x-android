/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.store

import io.element.android.appconfig.MatchaConfig
import io.element.android.features.matcha.impl.protocol.ThreadMetadataProtocol
import io.element.android.libraries.matrix.api.core.ThreadId
import io.element.android.libraries.matrix.api.core.toThreadId
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import timber.log.Timber

/** The outcome of creating a named thread. */
sealed interface NamedThreadResult {
    data class Created(val threadId: ThreadId) : NamedThreadResult

    /** The root message was sent but the thread could not be set up. */
    data object RootOnly : NamedThreadResult

    /** Nothing was sent. */
    data object Failed : NamedThreadResult
}

/**
 * Sends the root message, then the hidden first reply that names it and makes it a thread, as the web client does.
 * [name] must be a valid thread name.
 */
suspend fun MatchaRoomStore.createNamedThread(name: String): NamedThreadResult {
    val trimmed = name.trim()
    val rootContent = buildJsonObject {
        put("msgtype", "m.text")
        put("body", trimmed)
    }.toString()
    val root = send(EVENT_ROOM_MESSAGE, rootContent).getOrElse {
        Timber.w(it, "Could not send the root of a new thread")
        return NamedThreadResult.Failed
    }
    return send(MatchaConfig.EVENT_THREAD_METADATA, ThreadMetadataProtocol.bootstrapReplyContent(root.value, trimmed)).fold(
        onSuccess = { NamedThreadResult.Created(root.toThreadId()) },
        onFailure = {
            Timber.w(it, "Could not set up the new thread %s", root.value)
            NamedThreadResult.RootOnly
        },
    )
}

private const val EVENT_ROOM_MESSAGE = "m.room.message"
