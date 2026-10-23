/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs.editor

import com.google.common.truth.Truth.assertThat
import io.element.android.features.matcha.impl.docs.ANCHOR
import io.element.android.features.matcha.impl.docs.CONFIGURED_STATE
import io.element.android.features.matcha.impl.docs.DocNameError
import io.element.android.features.matcha.impl.docs.DocsProtocol
import io.element.android.features.matcha.impl.docs.DocsRoomFixture
import io.element.android.features.matcha.impl.docs.FakeYjsEngine.Companion.operation
import io.element.android.features.matcha.impl.docs.FakeYjsEngine.Companion.update
import io.element.android.features.matcha.impl.docs.aCatalogEntry
import io.element.android.features.matcha.impl.docs.aCreateItem
import io.element.android.features.matcha.impl.docs.aFrameItem
import io.element.android.features.matcha.impl.docs.frameItems
import io.element.android.features.networkmonitor.api.NetworkStatus
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import io.element.android.libraries.matrix.test.room.aRoomInfo
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.consumeItemsUntilTimeout
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

class DocEditorPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    private val plans = "11111111-1111-4111-8111-111111111111"
    private var doneCount = 0

    private fun items(vararg extra: MatrixTimelineItem): List<MatrixTimelineItem> = listOf(aCreateItem()) +
        frameItems("\$c1", aCatalogEntry("c1", plans, "Plans"), "c1", DocsProtocol.CATALOG_DOCUMENT_ID) +
        aFrameItem("\$t1", DocsProtocol.frames(update(operation("t", text = "Hello")), ANCHOR, "t1", plans).single()) +
        extra

    private fun TestScope.aPresenter(
        fixture: DocsRoomFixture,
        documentId: String = plans,
        isEarlier: Boolean = false,
    ) = DocEditorPresenter(
        context = fixture.context(backgroundScope, isEarlierChatDocument = isEarlier),
        documentId = documentId,
        onDone = { doneCount++ },
        networkMonitor = fixture.networkMonitor,
    )

    @Test
    fun `a doc loads into the preview with its name and is saved`() = runTest {
        aPresenter(DocsRoomFixture(items(), stateEvents = CONFIGURED_STATE)).test {
            assertThat(awaitItem().status).isEqualTo(DocStatus.Loading)
            val state = consumeItemsUntilPredicate { it.status == DocStatus.Saved }.last()
            assertThat(state.title).isEqualTo("Plans")
            assertThat(state.text).isEqualTo("Hello")
            assertThat(state.canEdit).isTrue()
            assertThat(state.canRename).isTrue()
            assertThat(state.isEditing).isFalse()
            assertThat(state.notice).isNull()
            assertThat(state.isLoadError).isFalse()
        }
    }

    @Test
    fun `edits are typed in edit mode and saved after the debounce`() = runTest {
        val fixture = DocsRoomFixture(items(), stateEvents = CONFIGURED_STATE)
        aPresenter(fixture).test {
            val ready = consumeItemsUntilPredicate { it.canEdit }.last()
            ready.eventSink(DocEditorEvent.StartEditing)
            val editing = consumeItemsUntilPredicate { it.isEditing }.last()
            editing.eventSink(DocEditorEvent.Edit("Hello", "Hello world"))
            val saving = consumeItemsUntilPredicate { it.status == DocStatus.Saving }.last()
            assertThat(saving.text).isEqualTo("Hello world")
            assertThat(saving.hasUnsavedChanges).isTrue()
            val saved = consumeItemsUntilPredicate { it.status == DocStatus.Saved && !it.hasUnsavedChanges }.last()
            assertThat(fixture.sent).hasSize(1)
            saved.eventSink(DocEditorEvent.StopEditing)
            assertThat(consumeItemsUntilPredicate { !it.isEditing }.last().text).isEqualTo("Hello world")
            saved.eventSink(DocEditorEvent.Back)
            assertThat(doneCount).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `back in edit mode returns to the preview, and leaving with unsent edits asks first`() = runTest {
        val fixture = DocsRoomFixture(items(), stateEvents = CONFIGURED_STATE)
        fixture.sendResult = { _, _ -> Result.failure(IllegalStateException("network")) }
        aPresenter(fixture).test {
            val ready = consumeItemsUntilPredicate { it.canEdit }.last()
            ready.eventSink(DocEditorEvent.StartEditing)
            ready.eventSink(DocEditorEvent.Edit("Hello", "Hello!"))
            val failed = consumeItemsUntilPredicate { it.status == DocStatus.Failed }.last()
            assertThat(failed.isEditing).isTrue()
            failed.eventSink(DocEditorEvent.Back)
            consumeItemsUntilPredicate { !it.isEditing }.last().eventSink(DocEditorEvent.Back)
            val asking = consumeItemsUntilPredicate { it.showLeaveDialog }.last()
            assertThat(doneCount).isEqualTo(0)
            asking.eventSink(DocEditorEvent.CancelLeave)
            consumeItemsUntilPredicate { !it.showLeaveDialog }.last().eventSink(DocEditorEvent.Back)
            consumeItemsUntilPredicate { it.showLeaveDialog }.last().eventSink(DocEditorEvent.ConfirmLeave)
            consumeItemsUntilPredicate { !it.showLeaveDialog }
            assertThat(doneCount).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed save says so, offline says that instead, and retry sends again`() = runTest {
        val fixture = DocsRoomFixture(items(), stateEvents = CONFIGURED_STATE)
        fixture.sendResult = { _, _ -> Result.failure(IllegalStateException("network")) }
        aPresenter(fixture).test {
            val ready = consumeItemsUntilPredicate { it.canEdit }.last()
            ready.eventSink(DocEditorEvent.Edit("Hello", "Hello!"))
            consumeItemsUntilPredicate { it.status == DocStatus.Failed }
            fixture.networkMonitor.connectivity.value = NetworkStatus.Disconnected
            val offline = consumeItemsUntilPredicate { it.status == DocStatus.Offline }.last()
            assertThat(offline.notice).isEqualTo(DocNotice.Offline)
            fixture.sendResult = { _, _ -> Result.success(EventId("\$ok")) }
            offline.eventSink(DocEditorEvent.Retry)
            val saved = consumeItemsUntilPredicate { it.status == DocStatus.Saved && !it.hasUnsavedChanges }.last()
            assertThat(saved.text).isEqualTo("Hello!")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `without the right to write, the doc is view only`() = runTest {
        aPresenter(DocsRoomFixture(items(), stateEvents = CONFIGURED_STATE, canSend = false)).test {
            val state = consumeItemsUntilPredicate { it.status == DocStatus.ViewOnly }.last()
            assertThat(state.canEdit).isFalse()
            assertThat(state.canRename).isFalse()
            state.eventSink(DocEditorEvent.StartEditing)
            state.eventSink(DocEditorEvent.Rename)
            assertThat(consumeItemsUntilTimeout().none { it.isEditing || it.nameSheet != null }).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the earlier chat doc is view only and named after the chat`() = runTest {
        val fixture = DocsRoomFixture(
            listOf(aCreateItem(), aFrameItem("\$t1", DocsProtocol.frames(update(operation("t", text = "Old notes")), ANCHOR, "t1", "main").single())),
            roomInfo = aRoomInfo(name = "Weekend trip", isEncrypted = true),
        )
        aPresenter(fixture, documentId = DocsProtocol.MAIN_DOCUMENT_ID, isEarlier = true).test {
            val state = consumeItemsUntilPredicate { it.status == DocStatus.EarlierChat }.last()
            assertThat(state.title).isEqualTo("Weekend trip")
            assertThat(state.text).isEqualTo("Old notes")
            assertThat(state.canEdit).isFalse()
            assertThat(state.canRename).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `undecryptable edits turn editing off with a strip`() = runTest {
        val encrypted = aFrameItem(
            "\$enc",
            """{"algorithm": "m.megolm.v1.aes-sha2", "ciphertext": "…", "m.relates_to": {"rel_type": "m.reference", "event_id": "$ANCHOR"}}""",
            type = "m.room.encrypted",
        )
        aPresenter(DocsRoomFixture(items(encrypted), stateEvents = CONFIGURED_STATE)).test {
            val state = consumeItemsUntilPredicate { it.notice == DocNotice.DecryptError }.last()
            assertThat(state.status).isEqualTo(DocStatus.ViewOnly)
            assertThat(state.canEdit).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a doc missing from the catalog, or a room without an anchor, can't be loaded`() = runTest {
        aPresenter(DocsRoomFixture(items(), stateEvents = CONFIGURED_STATE), documentId = "99999999-9999-4999-8999-999999999999").test {
            assertThat(consumeItemsUntilPredicate { it.isLoadError }.last().title).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
        aPresenter(DocsRoomFixture(emptyList(), stateEvents = CONFIGURED_STATE), documentId = DocsProtocol.MAIN_DOCUMENT_ID).test {
            val state = consumeItemsUntilPredicate(timeout = 10.seconds) { it.isLoadError }.last()
            assertThat(state.title).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `renaming keeps the sheet open until the name is saved`() = runTest {
        val fixture = DocsRoomFixture(items(), stateEvents = CONFIGURED_STATE)
        aPresenter(fixture).test {
            val ready = consumeItemsUntilPredicate { it.canRename }.last()
            ready.eventSink(DocEditorEvent.Rename)
            assertThat(consumeItemsUntilPredicate { it.nameSheet != null }.last().nameSheet?.name).isEqualTo("Plans")
            ready.eventSink(DocEditorEvent.UpdateName(""))
            ready.eventSink(DocEditorEvent.SubmitName)
            consumeItemsUntilPredicate { it.nameSheet?.error == DocNameError.Empty }
            ready.eventSink(DocEditorEvent.UpdateName("Plans 2027"))
            ready.eventSink(DocEditorEvent.SubmitName)
            consumeItemsUntilPredicate { it.nameSheet == null && it.title == "Plans 2027" }
            ready.eventSink(DocEditorEvent.Rename)
            consumeItemsUntilPredicate { it.nameSheet != null }
            ready.eventSink(DocEditorEvent.DismissNameSheet)
            consumeItemsUntilPredicate { it.nameSheet == null }
            cancelAndIgnoreRemainingEvents()
        }
    }
}
