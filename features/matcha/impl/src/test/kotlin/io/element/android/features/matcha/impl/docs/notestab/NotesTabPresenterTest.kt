/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs.notestab

import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.TurbineTestContext
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.element.android.features.matcha.impl.docs.ANCHOR
import io.element.android.features.matcha.impl.docs.CONFIGURED_STATE
import io.element.android.features.matcha.impl.docs.DocNameError
import io.element.android.features.matcha.impl.docs.DocsProtocol
import io.element.android.features.matcha.impl.docs.DocsRoomFixture
import io.element.android.features.matcha.impl.docs.FakeYjsEngine
import io.element.android.features.matcha.impl.docs.SpaceDocsContextFactory
import io.element.android.features.matcha.impl.docs.aCatalogEntry
import io.element.android.features.matcha.impl.docs.aCreateItem
import io.element.android.features.matcha.impl.docs.aFrameItem
import io.element.android.features.matcha.impl.docs.frameItems
import io.element.android.features.matcha.impl.store.settle
import io.element.android.features.networkmonitor.test.FakeNetworkMonitor
import io.element.android.libraries.dateformatter.test.FakeDateFormatter
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.featureflag.test.FakeFeatureFlagService
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.CurrentUserMembership
import io.element.android.libraries.matrix.api.spaces.SpaceService
import io.element.android.libraries.matrix.api.spaces.SpaceServiceFilter
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.spaces.FakeSpaceService
import io.element.android.libraries.previewutils.room.aSpaceRoom
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class NotesTabPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    private val design = RoomId("!design:matrix.org")
    private val launch = RoomId("!launch:matrix.org")
    private val company = RoomId("!company:matrix.org")
    private val research = RoomId("!research:matrix.org")
    private val lab = RoomId("!lab:matrix.org")
    private val drafts = RoomId("!drafts:matrix.org")
    private val plans = "11111111-1111-4111-8111-111111111111"
    private val guide = "22222222-2222-4222-8222-222222222222"
    private val opened = mutableListOf<Pair<RoomId, String?>>()

    @Test
    fun `nothing is shown and no space is read when the feature is off`() = runTest {
        val tab = aTab(aClient(listOf(aNotesSpace(design))), isEnabled = false)
        tab.test {
            assertThat(awaitItem().isVisible).isFalse()
            settle()
            // The spaces are never followed.
            assertThat(tab.reader.spaces.first()).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `every space with notes lists them, a space where the user can turn notes on offers it, and others are left out`() = runTest {
        val edit = DocsProtocol.frames(FakeYjsEngine.update(FakeYjsEngine.operation("e")), ANCHOR, "e1", plans).single()
        val designSpace = aNotesSpace(design, catalogItems() + aFrameItem("\$edit", edit, timestamp = 50))
        val canTurnOn = DocsRoomFixture(listOf(aCreateItem()), roomId = company, canSendState = true)
        val cannotTurnOn = DocsRoomFixture(listOf(aCreateItem()), roomId = launch)
        val engine = FakeYjsEngine()
        aTab(aClient(listOf(canTurnOn, designSpace, cannotTurnOn)), engine).test {
            val listed = awaitUntil { it.sections().size == 2 }
            // In the Spaces list's order.
            assertThat(listed.sections().map { it.space.spaceId }).containsExactly(company, design).inOrder()
            assertThat(listed.sections().first().content).isEqualTo(NotesSectionContent.TurnOn)
            // Nothing is read before it is on screen.
            assertThat(listed.sections().last().content).isEqualTo(NotesSectionContent.Loading)
            assertThat(engine.documents).isEmpty()

            listed.eventSink(NotesTabEvent.SpacesShown(setOf(company, design)))
            val loaded = awaitUntil { (it.sections().last().content as? NotesSectionContent.Notes)?.notes?.size == 3 }
            val notes = (loaded.sections().last().content as NotesSectionContent.Notes).notes
            assertThat(notes.map { it.id }).containsExactly("main", plans, guide).inOrder()
            assertThat(notes.map { it.title }).containsExactly(null, "Plans", "Guide").inOrder()
            assertThat(notes.map { it.editedAt }).containsExactly(null, "at 50", "at 6").inOrder()
            assertThat(loaded.canCreate).isTrue()

            loaded.eventSink(NotesTabEvent.OpenNote(design, plans))
            loaded.eventSink(NotesTabEvent.OpenNote(company, null))
            assertThat(opened).containsExactly(design to plans, company to null).inOrder()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `whether a space has notes is read from a room without a timeline, which is then let go`() = runTest {
        val space = aNotesSpace(design)
        // Only the room without a timeline is there to read.
        val client = aClient(listOf(space)).apply {
            givenGetRoomResult(design, null)
            givenGetBaseRoomResult(design, space.baseRoom)
        }
        aTab(client).test {
            assertThat(awaitUntil { it.sections().isNotEmpty() }.sections().single().content).isEqualTo(NotesSectionContent.Loading)
            space.baseRoom.assertDestroyed()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `without a space, or without a space with notes, the tab says where notes live`() = runTest {
        aTab(aClient(emptyList())).test {
            assertThat(awaitUntil { it.content is NotesTabContent.Empty }.content).isEqualTo(NotesTabContent.Empty(hasSpaces = false))
            cancelAndIgnoreRemainingEvents()
        }
        aTab(aClient(listOf(DocsRoomFixture(listOf(aCreateItem()), roomId = launch)))).test {
            val state = awaitUntil { it.content is NotesTabContent.Empty }
            assertThat(state.content).isEqualTo(NotesTabContent.Empty(hasSpaces = true))
            assertThat(state.canCreate).isFalse()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a space whose notes can't be read shows Retry without holding up the others, and Retry reads them again`() = runTest {
        val invalidFrame = """{"doc": "catalog", "version": 2, "m.relates_to": {"rel_type": "m.reference", "event_id": "$ANCHOR"}}"""
        val broken = aNotesSpace(design, listOf(aFrameItem("\$bad", invalidFrame)))
        val engine = FakeYjsEngine()
        aTab(aClient(listOf(broken, aNotesSpace(launch))), engine).test {
            val listed = awaitUntil { it.sections().size == 2 }
            listed.eventSink(NotesTabEvent.SpacesShown(setOf(design, launch)))
            val failed = awaitUntil {
                it.sections().first().content == NotesSectionContent.LoadError &&
                it.sections().last().content is NotesSectionContent.Notes
            }
            settle()
            assertThat(engine.documents).hasSize(2)
            // The broken catalog is closed; the other stays open while it is on screen.
            assertThat(engine.documents.count { it.isClosed }).isEqualTo(1)
            failed.eventSink(NotesTabEvent.Retry(design))
            settle()
            assertThat(engine.documents).hasSize(3)
            assertThat(engine.documents.count { it.isClosed }).isEqualTo(2)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `notes are read only while their space is on screen, what was read stays, and leaving the tab closes everything`() = runTest {
        val engine = FakeYjsEngine()
        aTab(aClient(listOf(aNotesSpace(design, catalogItems()))), engine).test {
            val listed = awaitUntil { it.sections().size == 1 }
            listed.eventSink(NotesTabEvent.SpacesShown(setOf(design)))
            awaitUntil { (it.sections().single().content as? NotesSectionContent.Notes)?.notes?.size == 3 }
            assertThat(engine.documents.single().isClosed).isFalse()

            listed.eventSink(NotesTabEvent.SpacesShown(emptySet()))
            settle(NotesTabReader.CLOSE_DELAY.inWholeMilliseconds + 1_000)
            assertThat(engine.documents.single().isClosed).isTrue()
            // The notes read stay listed: closing the catalog changes nothing on screen.
            expectNoEvents()

            listed.eventSink(NotesTabEvent.SpacesShown(setOf(design)))
            settle()
            assertThat(engine.documents).hasSize(2)
            cancelAndIgnoreRemainingEvents()
        }
        settle()
        assertThat(engine.documents.all { it.isClosed }).isTrue()
    }

    @Test
    fun `with one space that can take a note, New note asks for its name, creates it there and opens it`() = runTest {
        val space = aNotesSpace(design)
        val canTurnOn = DocsRoomFixture(listOf(aCreateItem()), roomId = company, canSendState = true)
        aTab(aClient(listOf(space, canTurnOn))).test {
            val ready = awaitUntil { it.canCreate && it.sections().size == 2 }
            ready.eventSink(NotesTabEvent.NewNote)
            val naming = awaitUntil { it.sheet is NotesTabSheet.Name }
            naming.eventSink(NotesTabEvent.SubmitName)
            awaitUntil { (it.sheet as? NotesTabSheet.Name)?.state?.error != null }
            naming.eventSink(NotesTabEvent.UpdateName("Trip plans"))
            naming.eventSink(NotesTabEvent.SubmitName)
            awaitUntil { it.sheet == null }
            val (spaceId, documentId) = opened.single()
            assertThat(spaceId).isEqualTo(design)
            assertThat(DocsProtocol.isDocumentId(documentId.orEmpty())).isTrue()
            assertThat(space.sent.single().second).contains("\"doc\":\"catalog\"")
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `with several spaces that can take a note, New note asks for the space first`() = runTest {
        aTab(aClient(listOf(aNotesSpace(design), aNotesSpace(launch)))).test {
            val ready = awaitUntil { it.sections().size == 2 }
            ready.eventSink(NotesTabEvent.NewNote)
            val choosing = awaitUntil { it.sheet is NotesTabSheet.ChooseSpace }
            assertThat((choosing.sheet as NotesTabSheet.ChooseSpace).spaces.map { it.spaceId }).containsExactly(design, launch).inOrder()
            choosing.eventSink(NotesTabEvent.ChooseSpace(launch))
            awaitUntil { it.sheet is NotesTabSheet.Name }
            choosing.eventSink(NotesTabEvent.DismissSheet)
            awaitUntil { it.sheet == null }
            assertThat(opened).isEmpty()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `each space is followed by its joined subspaces, a subspace of two spaces is listed once, and New note can target them`() = runTest {
        val spaces = listOf(design, research, drafts, launch, lab).map { aNotesSpace(it) }
        val filters = listOf(
            aFilter(design),
            aFilter(research, level = 1),
            // A subspace the user was only invited to.
            aFilter(drafts, level = 1, state = CurrentUserMembership.INVITED),
            aFilter(launch),
            // Research is also a subspace of Launch.
            aFilter(research, level = 1),
            aFilter(lab, level = 1),
        )
        val tab = aTab(aClient(spaces, filters))
        tab.test {
            val listed = awaitUntil { it.sections().size == 4 }
            assertThat(listed.sections().map { it.space.spaceId }).containsExactly(design, research, launch, lab).inOrder()
            assertThat(listed.sections().map { it.space.parentName }).containsExactly(null, "design", null, "launch").inOrder()
            assertThat(tab.reader.spaces.first()?.map { it.spaceId }).containsExactly(design, research, launch, lab).inOrder()

            listed.eventSink(NotesTabEvent.NewNote)
            val choosing = awaitUntil { it.sheet is NotesTabSheet.ChooseSpace }
            val choices = (choosing.sheet as NotesTabSheet.ChooseSpace).spaces
            assertThat(choices).containsExactlyElementsIn(listed.sections().map { it.space }).inOrder()
            choosing.eventSink(NotesTabEvent.ChooseSpace(lab))
            awaitUntil { it.sheet is NotesTabSheet.Name }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a note whose sheet is dismissed while it saves is still created once and opened, and its catalog is closed after`() = runTest {
        val space = aNotesSpace(design)
        val engine = FakeYjsEngine()
        aTab(aClient(listOf(space)), engine).test {
            awaitUntil { it.canCreate }.eventSink(NotesTabEvent.NewNote)
            val naming = awaitUntil { it.sheet is NotesTabSheet.Name }
            naming.eventSink(NotesTabEvent.UpdateName("Trip plans"))
            naming.eventSink(NotesTabEvent.SubmitName)
            // Back, a swipe, the scrim or the close button, while the note saves.
            naming.eventSink(NotesTabEvent.DismissSheet)
            awaitUntil { it.sheet == null }
            // New note shows the sheet of the note still saving rather than starting another.
            naming.eventSink(NotesTabEvent.NewNote)
            assertThat((awaitUntil { it.sheet != null }.sheet as NotesTabSheet.Name).state.isSaving).isTrue()
            naming.eventSink(NotesTabEvent.DismissSheet)
            awaitUntil { it.sheet == null }
            settle()

            val (spaceId, documentId) = opened.single()
            assertThat(spaceId).isEqualTo(design)
            assertThat(space.sent.single().second).contains("\"doc\":\"catalog\"")
            val catalog = engine.documents.single()
            assertThat(catalog.snapshot().entries.keys).containsExactly(documentId)
            assertThat(catalog.isClosed).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a save that fails after its sheet was dismissed shows the sheet again with the error, and Create saves the same note`() = runTest {
        val space = aNotesSpace(design).apply { sendResult = { _, _ -> Result.failure(IllegalStateException("offline")) } }
        val engine = FakeYjsEngine()
        aTab(aClient(listOf(space)), engine).test {
            awaitUntil { it.canCreate }.eventSink(NotesTabEvent.NewNote)
            val naming = awaitUntil { it.sheet is NotesTabSheet.Name }
            naming.eventSink(NotesTabEvent.UpdateName("Trip plans"))
            naming.eventSink(NotesTabEvent.SubmitName)
            naming.eventSink(NotesTabEvent.DismissSheet)
            awaitUntil { it.sheet == null }
            val failed = awaitUntil { (it.sheet as? NotesTabSheet.Name)?.state?.error == DocNameError.SaveFailed }
            val sheet = (failed.sheet as NotesTabSheet.Name).state
            assertThat(sheet.name).isEqualTo("Trip plans")
            assertThat(sheet.unsavedDocumentId).isNotNull()
            assertThat(opened).isEmpty()
            // The sheet's catalog stays open for Create.
            assertThat(engine.documents.single().isClosed).isFalse()

            space.sendResult = { _, _ -> Result.success(EventId("\$ok")) }
            failed.eventSink(NotesTabEvent.SubmitName)
            awaitUntil { it.sheet == null }
            settle()
            assertThat(opened).containsExactly(design to sheet.unsavedDocumentId)
            assertThat(space.sent.single().second).contains("\"doc\":\"catalog\"")
            val catalog = engine.documents.single()
            assertThat(catalog.snapshot().entries.keys).containsExactly(sheet.unsavedDocumentId)
            assertThat(catalog.isClosed).isTrue()
            cancelAndIgnoreRemainingEvents()
        }
    }

    private fun catalogItems(): List<MatrixTimelineItem> =
        frameItems("\$c1", aCatalogEntry("c1", plans, "Plans", createdAt = 5), "c1", DocsProtocol.CATALOG_DOCUMENT_ID) +
            frameItems("\$c2", aCatalogEntry("c2", guide, "Guide", createdAt = 6), "c2", DocsProtocol.CATALOG_DOCUMENT_ID)

    /** A space with notes turned on, holding [items] after its create event. */
    private fun aNotesSpace(roomId: RoomId, items: List<MatrixTimelineItem> = emptyList()) =
        DocsRoomFixture(listOf(aCreateItem()) + items, roomId = roomId, stateEvents = CONFIGURED_STATE)

    /**
     * A client holding the rooms of [spaces], whose space service gives [filters] as its space filters, replaying them like the real
     * one; by default every space is a joined top-level space, in this order.
     */
    private fun TestScope.aClient(spaces: List<DocsRoomFixture>, filters: List<SpaceServiceFilter> = spaces.map { aFilter(it.room.roomId) }) =
        FakeMatrixClient(
            sessionCoroutineScope = backgroundScope,
            spaceService = object : SpaceService by FakeSpaceService() {
                override val spaceFiltersFlow = MutableStateFlow(filters)
            },
        ).apply { spaces.forEach { givenGetRoomResult(it.room.roomId, it.room) } }

    /** The space filter of [roomId], a top-level space at level 0 and a subspace at level 1, named after its id. */
    private fun aFilter(roomId: RoomId, level: Int = 0, state: CurrentUserMembership = CurrentUserMembership.JOINED) = SpaceServiceFilter(
        spaceRoom = aSpaceRoom(roomId = roomId, displayName = roomId.value.removePrefix("!").substringBefore(":"), state = state),
        level = level,
        descendants = emptyList(),
    )

    private data class Tab(val reader: NotesTabReader, val presenter: NotesTabPresenter)

    /** The tab's reader and presenter; the name sheet opens its catalog through the same client and engine as the reader. */
    private fun TestScope.aTab(client: FakeMatrixClient, engine: FakeYjsEngine = FakeYjsEngine(), isEnabled: Boolean = true): Tab {
        val contextFactory = SpaceDocsContextFactory(client, engine, FakeNetworkMonitor(), testCoroutineDispatchers())
        val reader = NotesTabReader(client, contextFactory)
        val presenter = NotesTabPresenter(
            reader = reader,
            contextFactory = contextFactory,
            featureFlagService = FakeFeatureFlagService(mapOf(FeatureFlags.MatchaWorkspaces.key to isEnabled)),
            dateFormatter = FakeDateFormatter { timestamp, _, _ -> "at $timestamp" },
        )
        return Tab(reader, presenter)
    }

    private suspend fun Tab.test(block: suspend TurbineTestContext<NotesTabState>.() -> Unit) =
        moleculeFlow(RecompositionMode.Immediate) { presenter.present { spaceId, documentId -> opened += spaceId to documentId } }.test(validate = block)

    private fun NotesTabState.sections() = (content as? NotesTabContent.Spaces)?.sections.orEmpty()

    private suspend fun TurbineTestContext<NotesTabState>.awaitUntil(predicate: (NotesTabState) -> Boolean): NotesTabState =
        consumeItemsUntilPredicate(predicate = predicate).last()
}
