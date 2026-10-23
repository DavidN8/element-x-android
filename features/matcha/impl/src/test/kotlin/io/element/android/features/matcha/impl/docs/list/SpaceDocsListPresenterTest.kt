/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs.list

import com.google.common.truth.Truth.assertThat
import io.element.android.features.matcha.impl.docs.ANCHOR
import io.element.android.features.matcha.impl.docs.CONFIGURED_STATE
import io.element.android.features.matcha.impl.docs.DocNameError
import io.element.android.features.matcha.impl.docs.DocsProtocol
import io.element.android.features.matcha.impl.docs.DocsRoomFixture
import io.element.android.features.matcha.impl.docs.FakeYjsEngine
import io.element.android.features.matcha.impl.docs.aCatalogEntry
import io.element.android.features.matcha.impl.docs.aCreateItem
import io.element.android.features.matcha.impl.docs.aFrameItem
import io.element.android.features.matcha.impl.docs.frameItems
import io.element.android.libraries.dateformatter.test.FakeDateFormatter
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class SpaceDocsListPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    private val plans = "11111111-1111-4111-8111-111111111111"
    private val guide = "22222222-2222-4222-8222-222222222222"
    private val opened = mutableListOf<String>()
    private var setupOpened = 0

    private fun catalogItems(): List<MatrixTimelineItem> =
        frameItems("\$c1", aCatalogEntry("c1", plans, "Plans", createdAt = 5), "c1", DocsProtocol.CATALOG_DOCUMENT_ID) +
            frameItems("\$c2", aCatalogEntry("c2", guide, "Guide", createdAt = 6), "c2", DocsProtocol.CATALOG_DOCUMENT_ID)

    private fun TestScope.aPresenter(fixture: DocsRoomFixture) = SpaceDocsListPresenter(
        context = fixture.context(backgroundScope),
        navigator = object : SpaceDocsListPresenter.Navigator {
            override fun openDocument(documentId: String) {
                opened += documentId
            }

            override fun openSetup() {
                setupOpened++
            }
        },
        dateFormatter = FakeDateFormatter { timestamp, _, _ -> "at $timestamp" },
    )

    @Test
    fun `docs not turned on show the admin tip or the member line`() = runTest {
        aPresenter(DocsRoomFixture(listOf(aCreateItem()), canSendState = true)).test {
            val state = consumeItemsUntilPredicate { it.content is SpaceDocsListContent.NotSetUp }.last()
            assertThat(state.content).isEqualTo(SpaceDocsListContent.NotSetUp(canSetUp = true))
            assertThat(state.canCreate).isFalse()
            state.eventSink(SpaceDocsListEvent.OpenSetup)
            assertThat(setupOpened).isEqualTo(1)
        }
        aPresenter(DocsRoomFixture(listOf(aCreateItem()))).test {
            assertThat(consumeItemsUntilPredicate { it.content is SpaceDocsListContent.NotSetUp }.last().content)
                .isEqualTo(SpaceDocsListContent.NotSetUp(canSetUp = false))
        }
    }

    @Test
    fun `Space notes come first, then the docs by last edit`() = runTest {
        val items = listOf(aCreateItem()) + catalogItems() +
            aFrameItem("\$edit", DocsProtocol.frames(FakeYjsEngine.update(FakeYjsEngine.operation("e")), ANCHOR, "e1", plans).single(), timestamp = 50)
        aPresenter(DocsRoomFixture(items, stateEvents = CONFIGURED_STATE)).test {
            val state = consumeItemsUntilPredicate { (it.content as? SpaceDocsListContent.Documents)?.documents?.size == 3 }.last()
            val rows = (state.content as SpaceDocsListContent.Documents).documents
            assertThat(rows.map { it.id }).containsExactly("main", plans, guide).inOrder()
            assertThat(rows.map { it.title }).containsExactly(null, "Plans", "Guide").inOrder()
            assertThat(rows.map { it.editedAt }).containsExactly(null, "at 50", "at 6").inOrder()
            assertThat(state.canCreate).isTrue()
            assertThat(state.spaceName).isNotNull()
            state.eventSink(SpaceDocsListEvent.OpenDocument(guide))
            assertThat(opened).containsExactly(guide)
        }
    }

    @Test
    fun `a new doc needs a name, is saved, then opens`() = runTest {
        val fixture = DocsRoomFixture(listOf(aCreateItem()), stateEvents = CONFIGURED_STATE)
        aPresenter(fixture).test {
            val ready = consumeItemsUntilPredicate { it.canCreate }.last()
            ready.eventSink(SpaceDocsListEvent.NewDocument)
            val sheet = consumeItemsUntilPredicate { it.nameSheet != null }.last()
            assertThat(sheet.nameSheet?.documentId).isNull()
            sheet.eventSink(SpaceDocsListEvent.SubmitName)
            consumeItemsUntilPredicate { it.nameSheet?.error == DocNameError.Empty }
            sheet.eventSink(SpaceDocsListEvent.UpdateName("A".repeat(101)))
            consumeItemsUntilPredicate { it.nameSheet?.error == DocNameError.TooLong }
            sheet.eventSink(SpaceDocsListEvent.UpdateName("Trip plans"))
            consumeItemsUntilPredicate { it.nameSheet?.name == "Trip plans" && it.nameSheet?.error == null }
            sheet.eventSink(SpaceDocsListEvent.SubmitName)
            consumeItemsUntilPredicate { it.nameSheet == null }
            assertThat(opened).containsExactly("33333333-3333-4333-8333-333333333333")
            assertThat(fixture.sent.single().second).contains("\"doc\":\"catalog\"")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a failed save keeps the sheet open and retries the same doc`() = runTest {
        val fixture = DocsRoomFixture(listOf(aCreateItem()), stateEvents = CONFIGURED_STATE)
        fixture.sendResult = { _, _ -> Result.failure(IllegalStateException("offline")) }
        var ids = 0
        val presenter = SpaceDocsListPresenter(
            context = fixture.context(backgroundScope, newDocumentId = { "4444444${ids++}-4444-4444-8444-444444444444" }),
            navigator = object : SpaceDocsListPresenter.Navigator {
                override fun openDocument(documentId: String) {
                    opened += documentId
                }

                override fun openSetup() = Unit
            },
            dateFormatter = FakeDateFormatter(),
        )
        presenter.test {
            val ready = consumeItemsUntilPredicate { it.canCreate }.last()
            ready.eventSink(SpaceDocsListEvent.NewDocument)
            ready.eventSink(SpaceDocsListEvent.UpdateName("Trip plans"))
            ready.eventSink(SpaceDocsListEvent.SubmitName)
            val failed = consumeItemsUntilPredicate { it.nameSheet?.error == DocNameError.SaveFailed }.last()
            assertThat(failed.nameSheet?.unsavedDocumentId).isEqualTo("44444440-4444-4444-8444-444444444444")
            fixture.sendResult = { _, _ -> Result.success(EventId("\$ok")) }
            failed.eventSink(SpaceDocsListEvent.SubmitName)
            consumeItemsUntilPredicate { it.nameSheet == null }
            assertThat(opened).containsExactly("44444440-4444-4444-8444-444444444444")
            assertThat(ids).isEqualTo(1)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a rename starts from the current name`() = runTest {
        val fixture = DocsRoomFixture(listOf(aCreateItem()) + catalogItems(), stateEvents = CONFIGURED_STATE)
        aPresenter(fixture).test {
            val ready = consumeItemsUntilPredicate { it.canCreate && (it.content as? SpaceDocsListContent.Documents)?.documents?.size == 3 }.last()
            ready.eventSink(SpaceDocsListEvent.Rename(plans))
            val sheet = consumeItemsUntilPredicate { it.nameSheet != null }.last().nameSheet
            assertThat(sheet?.documentId).isEqualTo(plans)
            assertThat(sheet?.name).isEqualTo("Plans")
            ready.eventSink(SpaceDocsListEvent.UpdateName("Plans 2027"))
            ready.eventSink(SpaceDocsListEvent.SubmitName)
            val renamed = consumeItemsUntilPredicate { it.nameSheet == null }.last()
            assertThat(opened).isEmpty()
            assertThat(fixture.sent).hasSize(1)
            consumeItemsUntilPredicate { state -> (state.content as? SpaceDocsListContent.Documents)?.documents?.any { it.title == "Plans 2027" } == true }
            assertThat(renamed.nameSheet).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `an unreadable catalog shows the strip, a broken one the error page`() = runTest {
        val encrypted = aFrameItem(
            "\$enc",
            """{"algorithm": "m.megolm.v1.aes-sha2", "ciphertext": "…", "m.relates_to": {"rel_type": "m.reference", "event_id": "$ANCHOR"}}""",
            type = "m.room.encrypted",
        )
        aPresenter(DocsRoomFixture(listOf(aCreateItem(), encrypted), stateEvents = CONFIGURED_STATE)).test {
            val state = consumeItemsUntilPredicate { it.hasUnreadableNames }.last()
            assertThat(state.canCreate).isFalse()
            assertThat((state.content as SpaceDocsListContent.Documents).documents.map { it.id }).containsExactly("main")
        }
        aPresenter(DocsRoomFixture(emptyList(), stateEvents = CONFIGURED_STATE, createEventId = null)).test {
            val state = consumeItemsUntilPredicate { it.content == SpaceDocsListContent.LoadError }.last()
            assertThat(state.canCreate).isFalse()
            state.eventSink(SpaceDocsListEvent.Retry)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
