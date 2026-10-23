/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs.setup

import io.element.android.appconfig.MatchaConfig
import io.element.android.features.matcha.impl.docs.DocsProtocol
import io.element.android.features.matcha.impl.protocol.longOrNull
import io.element.android.features.matcha.impl.protocol.objectOrNull
import io.element.android.features.matcha.impl.protocol.stringOrNull
import io.element.android.libraries.core.data.tryOrNull
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.room.CurrentUserMembership
import io.element.android.libraries.matrix.api.room.JoinedRoom
import io.element.android.libraries.matrix.api.room.StateEventType
import io.element.android.libraries.matrix.api.room.powerlevels.use
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Whether a space's docs are turned on, and whether this user may turn them on. */
data class SpaceDocsSetupStatus(
    val isConfigured: Boolean,
    val canSetup: Boolean,
    val isEncrypted: Boolean,
)

/**
 * The one-time setup that lets every member of a space write docs, like the web client's `SpaceDocsViewModel`: encryption on
 * with Megolm, and the power levels of `com.matchahoneylatte.docs.update` and `m.room.encrypted` at 0. Child rooms never change.
 */
class SpaceDocsSetup(private val room: JoinedRoom) {
    suspend fun status(): SpaceDocsSetupStatus {
        val algorithm = stateContent(StateEventType.RoomEncryption)?.stringOrNull("algorithm")
        val events = stateContent(StateEventType.RoomPowerLevels)?.objectOrNull("events")
        val info = room.info()
        // Only a space: in a chat, these power levels would let every member post anything encrypted.
        val isJoinedSpace = info.isSpace && info.currentUserMembership == CurrentUserMembership.JOINED
        return SpaceDocsSetupStatus(
            isConfigured = isJoinedSpace && algorithm == DocsProtocol.ENCRYPTION_ALGORITHM &&
                events?.longOrNull(MatchaConfig.EVENT_DOCS_UPDATE) == 0L && events.longOrNull(DocsProtocol.EVENT_ROOM_ENCRYPTED) == 0L,
            canSetup = isJoinedSpace && room.roomPermissions().use(false) {
                it.canOwnUserSendState(StateEventType.RoomEncryption) && it.canOwnUserSendState(StateEventType.RoomPowerLevels)
            },
            isEncrypted = algorithm != null || info.isEncrypted == true,
        )
    }

    /** Turns encryption on if needed, then sets only the two power levels, waiting for each change to sync. */
    suspend fun turnOn(): Result<Unit> = runCatchingExceptions {
        if (!status().canSetup) error("Not allowed to set up docs")
        val algorithm = stateContent(StateEventType.RoomEncryption)?.stringOrNull("algorithm")
        if (algorithm != null && algorithm != DocsProtocol.ENCRYPTION_ALGORITHM) error("Unsupported space encryption")
        if (algorithm == null) {
            room.enableEncryption().getOrThrow()
            awaitSynced { stateContent(StateEventType.RoomEncryption)?.stringOrNull("algorithm") == DocsProtocol.ENCRYPTION_ALGORITHM }
        }
        // Read the power levels fresh, right before changing them, so nobody else's change is lost.
        val powerLevels = stateContent(StateEventType.RoomPowerLevels) ?: error("No power levels")
        val events = powerLevels.objectOrNull("events").orEmpty() +
            mapOf(MatchaConfig.EVENT_DOCS_UPDATE to JsonPrimitive(0), DocsProtocol.EVENT_ROOM_ENCRYPTED to JsonPrimitive(0))
        val content = JsonObject(powerLevels + ("events" to JsonObject(events)))
        room.sendRawStateEvent(DocsProtocol.EVENT_POWER_LEVELS, "", content.toString()).getOrThrow()
        awaitSynced { status().isConfigured }
    }.onFailure { Timber.w(it, "Could not turn on space docs") }

    private suspend fun awaitSynced(isDone: suspend () -> Boolean) {
        try {
            // State changes arrive with syncs. The room info doesn't emit for every state change (power levels don't always), so
            // the stored state is checked again on each room info update and at least every SYNC_POLL_INTERVAL.
            withTimeout(SYNC_TIMEOUT) {
                val ticks = flow {
                    while (true) {
                        emit(Unit)
                        delay(SYNC_POLL_INTERVAL)
                    }
                }
                merge(room.roomInfoFlow.map { }, ticks).first { isDone() }
            }
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException("The space state did not sync", e)
        }
    }

    private suspend fun stateContent(type: StateEventType): JsonObject? =
        room.getStateEventsJson(type).getOrNull()?.get("")?.let { tryOrNull { Json.parseToJsonElement(it).jsonObject } }

    private companion object {
        val SYNC_TIMEOUT = 20.seconds
        val SYNC_POLL_INTERVAL = 500.milliseconds
    }
}
