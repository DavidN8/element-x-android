/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs.setup

import com.google.common.truth.Truth.assertThat
import io.element.android.features.matcha.impl.docs.CONFIGURED_STATE
import io.element.android.features.matcha.impl.docs.DocsRoomFixture
import io.element.android.features.matcha.impl.docs.aCreateItem
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.room.StateEventType
import io.element.android.libraries.matrix.test.room.aRoomInfo
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Rule
import org.junit.Test

class SpaceDocsSetupPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    private val powerLevels = """{"users": {"@admin:x": 100}, "events_default": 50, "events": {"m.room.name": 50}}"""
    private val encryption = """{"algorithm": "m.megolm.v1.aes-sha2"}"""
    private var doneCount = 0

    /** A space whose writes sync back at once, as the state of the room. */
    private fun aFixture(isEncrypted: Boolean, enableEncryptionResult: () -> Result<Unit> = { Result.success(Unit) }): DocsRoomFixture {
        lateinit var fixture: DocsRoomFixture
        fixture = DocsRoomFixture(
            items = listOf(aCreateItem()),
            roomInfo = aRoomInfo(isEncrypted = isEncrypted, isSpace = true),
            canSendState = true,
            stateEvents =
                mapOf(StateEventType.RoomPowerLevels to powerLevels) + if (isEncrypted) mapOf(StateEventType.RoomEncryption to encryption) else emptyMap(),
            enableEncryptionResult = {
                enableEncryptionResult().onSuccess {
                    fixture.stateEvents[StateEventType.RoomEncryption] = encryption
                    fixture.baseRoom.givenRoomInfo(aRoomInfo(isEncrypted = true, isSpace = true))
                }
            },
            sendStateResult = { type, _, content ->
                if (type == "m.room.power_levels") fixture.stateEvents[StateEventType.RoomPowerLevels] = content
                Result.success(EventId("\$pl"))
            },
        )
        return fixture
    }

    private fun aPresenter(fixture: DocsRoomFixture) = SpaceDocsSetupPresenter(SpaceDocsSetup(fixture.room), onDone = { doneCount++ })

    @Test
    fun `turning docs on asks to turn on encryption first, then sets only the two power levels`() = runTest {
        val fixture = aFixture(isEncrypted = false)
        assertThat(SpaceDocsSetup(fixture.room).status()).isEqualTo(SpaceDocsSetupStatus(isConfigured = false, canSetup = true, isEncrypted = false))
        aPresenter(fixture).test {
            val initial = awaitItem()
            initial.eventSink(SpaceDocsSetupEvent.TurnOn)
            val asking = consumeItemsUntilPredicate { it.showEncryptionDialog }.last()
            asking.eventSink(SpaceDocsSetupEvent.ConfirmEncryption)
            consumeItemsUntilPredicate { !it.isWorking && doneCount == 1 }
            val (type, key, content) = fixture.sentState.single()
            assertThat(type).isEqualTo("m.room.power_levels")
            assertThat(key).isEmpty()
            val sent = Json.parseToJsonElement(content).jsonObject
            assertThat(sent["events_default"]!!.jsonPrimitive.int).isEqualTo(50)
            assertThat(sent["users"].toString()).isEqualTo("""{"@admin:x":100}""")
            val events = sent["events"]!!.jsonObject
            assertThat(events["m.room.name"]!!.jsonPrimitive.int).isEqualTo(50)
            assertThat(events["com.matchahoneylatte.docs.update"]!!.jsonPrimitive.int).isEqualTo(0)
            assertThat(events["m.room.encrypted"]!!.jsonPrimitive.int).isEqualTo(0)
            assertThat(SpaceDocsSetup(fixture.room).status().isConfigured).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an encrypted space goes straight to the power levels, and the dialog can be dismissed`() = runTest {
        val encrypted = aFixture(isEncrypted = true)
        aPresenter(encrypted).test {
            awaitItem().eventSink(SpaceDocsSetupEvent.TurnOn)
            consumeItemsUntilPredicate { doneCount == 1 && !it.isWorking }
            assertThat(encrypted.sentState).hasSize(1)
            cancelAndIgnoreRemainingEvents()
        }
        val plain = aFixture(isEncrypted = false)
        aPresenter(plain).test {
            awaitItem().eventSink(SpaceDocsSetupEvent.TurnOn)
            consumeItemsUntilPredicate { it.showEncryptionDialog }.last().eventSink(SpaceDocsSetupEvent.DismissEncryption)
            consumeItemsUntilPredicate { !it.showEncryptionDialog }
            assertThat(plain.sentState).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failure is shown above the button`() = runTest {
        val fixture = aFixture(isEncrypted = false, enableEncryptionResult = { Result.failure(IllegalStateException("network")) })
        aPresenter(fixture).test {
            awaitItem().eventSink(SpaceDocsSetupEvent.TurnOn)
            consumeItemsUntilPredicate { it.showEncryptionDialog }.last().eventSink(SpaceDocsSetupEvent.ConfirmEncryption)
            val failed = consumeItemsUntilPredicate { it.hasFailed && !it.isWorking }.last()
            assertThat(failed.hasFailed).isTrue()
            assertThat(doneCount).isEqualTo(0)
            assertThat(fixture.sentState).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a chat is never set up as a space`() = runTest {
        val fixture = DocsRoomFixture(
            listOf(aCreateItem()),
            roomInfo = aRoomInfo(isEncrypted = true, isSpace = false),
            canSendState = true,
            stateEvents = CONFIGURED_STATE,
        )
        val setup = SpaceDocsSetup(fixture.room)
        assertThat(setup.status()).isEqualTo(SpaceDocsSetupStatus(isConfigured = false, canSetup = false, isEncrypted = true))
        assertThat(setup.turnOn().isFailure).isTrue()
        assertThat(fixture.sentState).isEmpty()
    }

    @Test
    fun `a member can't set docs up`() = runTest {
        val fixture = DocsRoomFixture(listOf(aCreateItem()), stateEvents = mapOf(StateEventType.RoomPowerLevels to powerLevels))
        val setup = SpaceDocsSetup(fixture.room)
        assertThat(setup.status().canSetup).isFalse()
        assertThat(setup.turnOn().isFailure).isTrue()
        assertThat(fixture.sentState).isEmpty()
    }
}
