/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs.transport

import com.google.common.truth.Truth.assertThat
import io.element.android.appconfig.MatchaConfig
import io.element.android.features.matcha.impl.docs.ANCHOR
import io.element.android.features.matcha.impl.docs.BOB
import io.element.android.features.matcha.impl.docs.DocsProtocol
import io.element.android.features.matcha.impl.docs.DocsRoomFixture
import io.element.android.features.matcha.impl.docs.FakeYjsEngine.Companion.operation
import io.element.android.features.matcha.impl.docs.FakeYjsEngine.Companion.update
import io.element.android.features.matcha.impl.docs.aCreateItem
import io.element.android.features.matcha.impl.docs.aFrameItem
import io.element.android.features.matcha.impl.docs.anItem
import io.element.android.features.matcha.impl.docs.engine.YjsEngineException
import io.element.android.features.matcha.impl.docs.frameItems
import io.element.android.features.matcha.impl.docs.settle
import io.element.android.features.matcha.impl.store.MatchaRoomStore
import io.element.android.features.networkmonitor.api.NetworkStatus
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.RawEventsPage
import io.element.android.libraries.matrix.api.timeline.MatrixTimelineItem
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.test.room.aRoomInfo
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Test
import kotlin.io.encoding.Base64

class RoomDocumentSessionTest {
    private fun hello() = update(operation("a", text = "Hello"))

    @Test
    fun `the whole history is replayed before the document is ready`() = runTest {
        val fixture = DocsRoomFixture(
            listOf(
                aCreateItem(),
                aFrameItem("\$f1", DocsProtocol.frames(hello(), ANCHOR, "u1", "main").single()),
                // Another document, another anchor, and a duplicate of the first update are all skipped.
                aFrameItem("\$f2", DocsProtocol.frames(update(operation("x", text = "Other")), ANCHOR, "u2", DocsProtocol.CATALOG_DOCUMENT_ID).single()),
                aFrameItem("\$f3", DocsProtocol.frames(update(operation("y", text = "Elsewhere")), "\$other", "u3", "main").single()),
                aFrameItem("\$f4", DocsProtocol.frames(update(operation("z", text = "Duplicate")), ANCHOR, "u1", "main").single()),
            ),
        )
        val session = fixture.session(backgroundScope)
        assertThat(session.state.value.sync).isEqualTo(DocumentSync.Loading)
        settle()
        val state = session.state.value
        assertThat(state.isReady).isTrue()
        assertThat(state.canEdit).isTrue()
        assertThat(state.sync).isEqualTo(DocumentSync.Saved)
        assertThat(state.text).isEqualTo("Hello")
        assertThat(fixture.engine.documents.single().applyCount).isEqualTo(1)
        // The replay crosses the bridge once.
        assertThat(fixture.engine.documents.single().applyAllCount).isEqualTo(1)
    }

    @Test
    fun `an update the engine refuses stops the replay there, and reading the document again stops there again`() = runTest {
        val fixture = DocsRoomFixture(
            listOf(
                aCreateItem(),
                aFrameItem("\$f1", DocsProtocol.frames(hello(), ANCHOR, "u1", "main").single()),
                aFrameItem("\$bad", DocsProtocol.frames("garbage".toByteArray(), ANCHOR, "u2", "main").single()),
                aFrameItem("\$f3", DocsProtocol.frames(update(operation("c", text = "After", deps = listOf("a"))), ANCHOR, "u3", "main").single()),
            ),
        )
        val session = fixture.session(backgroundScope)
        settle()
        val document = fixture.engine.documents.single()
        assertThat(session.state.value.error).isEqualTo(DocumentError.InvalidUpdate)
        assertThat(document.snapshot().text).isEqualTo("Hello")
        session.retry()
        settle()
        assertThat(session.state.value.error).isEqualTo(DocumentError.InvalidUpdate)
        assertThat(document.snapshot().text).isEqualTo("Hello")
        assertThat(document.applyCount).isEqualTo(1)
    }

    @Test
    fun `the frames come from the anchor's relations, which hold what reading the whole history finds`() = runTest {
        val frames = listOf(
            "\$f1" to DocsProtocol.frames(hello(), ANCHOR, "u1", "main").single(),
            "\$f2" to DocsProtocol.frames(update(operation("b", text = "Hello again", deps = listOf("a"))), ANCHOR, "u2", "main").single(),
        )
        val undecryptable = "\$enc" to """{"algorithm": "m.megolm.v1.aes-sha2", "ciphertext": "…", "m.relates_to": $RELATION}"""
        listOf(frames, frames + undecryptable).forEach { events ->
            fun json(event: Pair<String, String>) = frameJson(event.first, event.second, isEncrypted = event == undecryptable)
            // Without the SDK's relations the whole history is read; this timeline holds the frames there, as older pages would.
            val history = LongHistoryRoom(listOf(aCreateItem()) + events.map { anItem(json(it), it.first) }, related = null)
            // With them the frames come with the relations, and the history holds none.
            val relations = LongHistoryRoom(listOf(aCreateItem()), related = events.map(::json))
            val fromHistory = history.fixture.session(backgroundScope)
            val fromRelations = relations.fixture.session(backgroundScope)
            settle()
            assertThat(fromRelations.state.value).isEqualTo(fromHistory.state.value)
            assertThat(history.pagesRead).isEqualTo(LongHistoryRoom.PAGES)
            assertThat(relations.pagesRead).isEqualTo(MatchaRoomStore.INITIAL_HISTORY_PAGES)
            if (undecryptable in events) {
                assertThat(fromRelations.state.value.error).isEqualTo(DocumentError.DecryptError)
            } else {
                assertThat(fromRelations.state.value.text).isEqualTo("Hello again")
            }
        }
    }

    @Test
    fun `chunks are applied once committed and complete, in index order`() = runTest {
        val big = update(operation("big", text = "x".repeat(MatchaConfig.DOCS_CHUNK_BYTES * 2)))
        val frames = frameItems("\$big", big, "u-big")
        assertThat(frames).hasSize(4)
        val fixture = DocsRoomFixture(listOf(aCreateItem(), frames[1], frames[0], frames[2]))
        val session = fixture.session(backgroundScope)
        settle()
        // Uncommitted parts are not missing updates yet: the sender may still be sending.
        assertThat(session.state.value.isReady).isTrue()
        assertThat(session.state.value.text).isEmpty()
        fixture.timelineItems.value += frames[3]
        settle()
        assertThat(session.state.value.text).hasLength(MatchaConfig.DOCS_CHUNK_BYTES * 2)
    }

    @Test
    fun `a committed update with a missing part, or a missing predecessor, turns editing off until it arrives`() = runTest {
        val big = update(operation("big", text = "x".repeat(MatchaConfig.DOCS_CHUNK_BYTES + 1)))
        val frames = frameItems("\$big", big, "u-big")
        val fixture = DocsRoomFixture(listOf(aCreateItem(), frames[0], frames[2]))
        val session = fixture.session(backgroundScope)
        settle()
        assertThat(session.state.value.error).isEqualTo(DocumentError.IncompleteUpdate)
        assertThat(session.state.value.canEdit).isFalse()
        fixture.timelineItems.value += frames[1]
        settle()
        assertThat(session.state.value.error).isNull()
        assertThat(session.state.value.isReady).isTrue()

        val second = update(operation("second", text = "After", deps = listOf("first")))
        fixture.timelineItems.value += aFrameItem("\$second", DocsProtocol.frames(second, ANCHOR, "u-second", "main").single())
        settle()
        assertThat(session.state.value.error).isEqualTo(DocumentError.IncompleteUpdate)
        fixture.timelineItems.value += aFrameItem("\$first", DocsProtocol.frames(update(operation("first")), ANCHOR, "u-first", "main").single())
        settle()
        assertThat(session.state.value.error).isNull()
        assertThat(session.state.value.text).isEqualTo("After")
    }

    @Test
    fun `an update missing its predecessor turns editing off even after a failed send, and unsent edits still go out`() = runTest {
        val fixture = DocsRoomFixture(listOf(aCreateItem()))
        fixture.sendResult = { _, _ -> Result.failure(IllegalStateException("server error")) }
        val session = fixture.session(backgroundScope)
        settle()
        backgroundScope.launch { session.edit("", "Draft") }
        settle()
        assertThat(session.state.value.error).isEqualTo(DocumentError.SendError)
        assertThat(session.state.value.canEdit).isTrue()
        val second = update(operation("second", text = "After", deps = listOf("first")))
        fixture.timelineItems.value += aFrameItem("\$second", DocsProtocol.frames(second, ANCHOR, "u-second", "main").single())
        settle()
        assertThat(session.state.value.error).isEqualTo(DocumentError.IncompleteUpdate)
        assertThat(session.state.value.canEdit).isFalse()
        fixture.sendResult = { _, _ -> Result.success(EventId("\$ok")) }
        backgroundScope.launch { session.flush() }
        settle()
        // Saving cleared the send error, not the missing update.
        assertThat(fixture.sent).hasSize(1)
        assertThat(session.state.value.pendingUpdates).isEqualTo(0)
        assertThat(session.state.value.error).isEqualTo(DocumentError.IncompleteUpdate)
        assertThat(session.state.value.isReady).isFalse()
        assertThat(session.state.value.canEdit).isFalse()
        fixture.timelineItems.value += aFrameItem("\$first", DocsProtocol.frames(update(operation("first")), ANCHOR, "u-first", "main").single())
        settle()
        assertThat(session.state.value.error).isNull()
        assertThat(session.state.value.canEdit).isTrue()
    }

    @Test
    fun `frames that break the protocol make the document invalid`() = runTest {
        val garbage = Base64.encode("garbage".toByteArray())
        val malformed = listOf(
            """"version": 2, "update_id": "u", "kind": "update", "chunks": 1, "chunk": 0, "data": "AAAA"""",
            """"version": 1, "update_id": "u", "kind": "update", "chunks": 2, "chunk": 0, "data": "AAAA"""",
            """"version": 1, "update_id": "u", "kind": "commit", "chunks": 2, "data": "AAAA"""",
            """"version": 1, "update_id": "u", "kind": "update", "chunks": 1, "chunk": 0, "data": "A=A="""",
            """"version": 1, "update_id": "not valid!", "kind": "update", "chunks": 1, "chunk": 0, "data": "AAAA"""",
            """"version": 1, "update_id": "u", "kind": "update", "chunks": 1, "chunk": 0, "data": "$garbage"""",
        ).map { """{"doc": "main", "m.relates_to": $RELATION, $it}""" }
        malformed.forEach { content ->
            val fixture = DocsRoomFixture(listOf(aCreateItem(), aFrameItem("\$bad", content)))
            val session = fixture.session(backgroundScope)
            settle()
            assertThat(session.state.value.error).isEqualTo(DocumentError.InvalidUpdate)
            assertThat(session.state.value.isReady).isFalse()
            session.close()
        }
    }

    @Test
    fun `a part sent again with different bytes is invalid, an identical one is fine`() = runTest {
        val big = update(operation("big", text = "x".repeat(MatchaConfig.DOCS_CHUNK_BYTES + 1)))
        val frames = DocsProtocol.frames(big, ANCHOR, "u-big", "main")
        val altered = Json.parseToJsonElement(frames[0]).jsonObject.toMutableMap().apply {
            put("data", JsonPrimitive(Base64.encode(ByteArray(10) { 1 })))
        }.let { JsonObject(it).toString() }
        val same =
            DocsRoomFixture(
                listOf(aCreateItem(), aFrameItem("\$a", frames[0]), aFrameItem("\$b", frames[0]), aFrameItem("\$c", frames[1]), aFrameItem("\$d", frames[2]))
            )
        val sameSession = same.session(backgroundScope)
        val conflicting = DocsRoomFixture(listOf(aCreateItem(), aFrameItem("\$a", frames[0]), aFrameItem("\$b", altered)))
        val conflictingSession = conflicting.session(backgroundScope)
        settle()
        assertThat(sameSession.state.value.isReady).isTrue()
        assertThat(conflictingSession.state.value.error).isEqualTo(DocumentError.InvalidUpdate)
    }

    @Test
    fun `an undecryptable frame blocks the document until it is decrypted`() = runTest {
        val encrypted =
            aFrameItem("\$enc", """{"algorithm": "m.megolm.v1.aes-sha2", "ciphertext": "…", "m.relates_to": $RELATION}""", type = "m.room.encrypted")
        val fixture = DocsRoomFixture(listOf(aCreateItem(), encrypted))
        val session = fixture.session(backgroundScope)
        settle()
        assertThat(session.state.value.error).isEqualTo(DocumentError.DecryptError)
        assertThat(session.state.value.canEdit).isFalse()
        // The keys arrive: the same event comes back decrypted.
        fixture.timelineItems.value = listOf(aCreateItem(), aFrameItem("\$enc", DocsProtocol.frames(hello(), ANCHOR, "u1", "main").single()))
        settle()
        assertThat(session.state.value.error).isNull()
        assertThat(session.state.value.text).isEqualTo("Hello")
    }

    @Test
    fun `edits are debounced, framed and only saved once acknowledged`() = runTest {
        val fixture = DocsRoomFixture(listOf(aCreateItem()))
        val session = fixture.session(backgroundScope)
        settle()
        backgroundScope.launch { session.edit("", "Hel") }
        settle(100)
        backgroundScope.launch { session.edit("Hel", "Hello") }
        settle(100)
        assertThat(session.state.value.text).isEqualTo("Hello")
        assertThat(session.state.value.sync).isEqualTo(DocumentSync.Saving)
        assertThat(session.state.value.pendingUpdates).isEqualTo(2)
        assertThat(fixture.sent).isEmpty()
        settle(499)
        assertThat(fixture.sent).hasSize(1)
        val (type, content) = fixture.sent.single()
        assertThat(type).isEqualTo(MatchaConfig.EVENT_DOCS_UPDATE)
        val frame = Json.parseToJsonElement(content).jsonObject
        assertThat(frame["version"]!!.jsonPrimitive.int).isEqualTo(1)
        assertThat(frame["doc"]!!.jsonPrimitive.content).isEqualTo("main")
        assertThat(frame["update_id"]!!.jsonPrimitive.content).isEqualTo("main-update-0")
        assertThat(frame["kind"]!!.jsonPrimitive.content).isEqualTo("update")
        assertThat(frame["chunks"]!!.jsonPrimitive.int).isEqualTo(1)
        assertThat(frame["chunk"]!!.jsonPrimitive.int).isEqualTo(0)
        assertThat(frame["m.relates_to"].toString()).isEqualTo(RELATION.replace(" ", ""))
        settle()
        assertThat(session.state.value.sync).isEqualTo(DocumentSync.Saved)
        assertThat(session.state.value.pendingUpdates).isEqualTo(0)
        // The echo of our own frame is not applied again.
        assertThat(fixture.engine.documents.single().applyCount).isEqualTo(0)
    }

    @Test
    fun `a large edit is sent in chunks followed by a commit`() = runTest {
        val fixture = DocsRoomFixture(listOf(aCreateItem()))
        val session = fixture.session(backgroundScope)
        settle()
        backgroundScope.launch { session.edit("", "y".repeat(MatchaConfig.DOCS_CHUNK_BYTES * 2)) }
        settle()
        val kinds = fixture.sent.map { Json.parseToJsonElement(it.second).jsonObject["kind"]!!.jsonPrimitive.content }
        assertThat(kinds).containsExactly("chunk", "chunk", "chunk", "commit").inOrder()
        assertThat(session.state.value.sync).isEqualTo(DocumentSync.Saved)
    }

    @Test
    fun `a failed send keeps the edits, shows offline, and resends them on reconnection`() = runTest {
        val fixture = DocsRoomFixture(listOf(aCreateItem()))
        fixture.sendResult = { _, _ -> Result.failure(IllegalStateException("offline")) }
        val session = fixture.session(backgroundScope)
        settle()
        fixture.networkMonitor.connectivity.value = NetworkStatus.Disconnected
        backgroundScope.launch { session.edit("", "Draft") }
        settle()
        assertThat(session.state.value.error).isEqualTo(DocumentError.SendError)
        assertThat(session.state.value.sync).isEqualTo(DocumentSync.Offline)
        assertThat(session.state.value.pendingUpdates).isEqualTo(1)
        // Editing goes on while offline.
        assertThat(session.state.value.canEdit).isTrue()
        backgroundScope.launch { session.edit("Draft", "Draft 2") }
        settle()
        fixture.networkMonitor.connectivity.value = NetworkStatus.Connected
        settle()
        assertThat(session.state.value.sync).isEqualTo(DocumentSync.Error)
        fixture.sendResult = { _, _ -> Result.success(EventId("\$ok")) }
        fixture.networkMonitor.connectivity.value = NetworkStatus.Disconnected
        settle()
        fixture.networkMonitor.connectivity.value = NetworkStatus.Connected
        settle()
        assertThat(session.state.value.sync).isEqualTo(DocumentSync.Saved)
        assertThat(session.state.value.error).isNull()
        // The first edit was already framed; the second followed as its own update.
        val updateIds = fixture.sent.map { Json.parseToJsonElement(it.second).jsonObject["update_id"]!!.jsonPrimitive.content }
        assertThat(updateIds).containsExactly("main-update-0", "main-update-1").inOrder()
    }

    @Test
    fun `edits that arrived while typing are kept`() = runTest {
        val fixture = DocsRoomFixture(listOf(aCreateItem(), aFrameItem("\$f1", DocsProtocol.frames(hello(), ANCHOR, "u1", "main").single())))
        val session = fixture.session(backgroundScope)
        settle()
        fixture.timelineItems.value +=
            aFrameItem("\$f2", DocsProtocol.frames(update(operation("b", text = "Oh Hello", deps = listOf("a"))), ANCHOR, "u2", "main").single())
        settle()
        // The field still showed "Hello" when the user typed "!".
        backgroundScope.launch { session.edit("Hello", "Hello!") }
        settle()
        assertThat(session.state.value.text).isEqualTo("Oh Hello!")
    }

    @Test
    fun `the text is read back from the engine only when an update was applied`() = runTest {
        val fixture = DocsRoomFixture(listOf(aCreateItem(), aFrameItem("\$f1", DocsProtocol.frames(hello(), ANCHOR, "u1", "main").single())))
        val session = fixture.session(backgroundScope)
        settle()
        backgroundScope.launch { session.edit("Hello", "Hello!") }
        settle()
        val document = fixture.engine.documents.single()
        val reads = document.snapshotCount
        // The echo of our own frame, a frame of another document and a duplicate are skipped without reading the text back.
        fixture.timelineItems.value += listOf(
            aFrameItem("\$echo", fixture.sent.single().second, sender = fixture.room.sessionId.value),
            aFrameItem("\$f2", DocsProtocol.frames(update(operation("x", text = "Other")), ANCHOR, "u2", DocsProtocol.CATALOG_DOCUMENT_ID).single()),
            aFrameItem("\$f3", DocsProtocol.frames(hello(), ANCHOR, "u1", "main").single()),
        )
        settle()
        assertThat(document.snapshotCount).isEqualTo(reads)
        assertThat(session.state.value.text).isEqualTo("Hello!")
        val remote = update(operation("b", text = "Hi", deps = listOf("a")))
        fixture.timelineItems.value += aFrameItem("\$f4", DocsProtocol.frames(remote, ANCHOR, "u4", "main").single())
        settle()
        assertThat(document.snapshotCount).isEqualTo(reads + 1)
        assertThat(session.state.value.text).isEqualTo("Hi")
    }

    @Test
    fun `without permission the document is view only`() = runTest {
        val fixture = DocsRoomFixture(listOf(aCreateItem()), canSend = false)
        val session = fixture.session(backgroundScope)
        settle()
        assertThat(session.state.value.isReady).isTrue()
        assertThat(session.state.value.canEdit).isFalse()
        backgroundScope.launch { session.edit("", "Nope") }
        settle()
        assertThat(session.state.value.text).isEmpty()
        assertThat(fixture.sent).isEmpty()
    }

    @Test
    fun `a read-only session never edits`() = runTest {
        val session = DocsRoomFixture(listOf(aCreateItem())).session(backgroundScope, isReadOnly = true)
        settle()
        assertThat(session.state.value.isReady).isTrue()
        assertThat(session.state.value.canEdit).isFalse()
    }

    @Test
    fun `history that can't be read is a load error, and retry recovers`() = runTest {
        var fail = true
        val fixture = DocsRoomFixture(
            listOf(aCreateItem()),
            hasMoreHistory = true,
            paginateResult = {
                if (fail) {
                    Result.failure(IllegalStateException("network"))
                } else {
                    Result.success(true)
                }
            },
        )
        val session = fixture.session(backgroundScope)
        settle()
        assertThat(session.state.value.error).isEqualTo(DocumentError.LoadError)
        fail = false
        fixture.timeline.backwardPaginationStatus.value = fixture.timeline.backwardPaginationStatus.value.copy(hasMoreToLoad = false)
        session.retry()
        settle()
        assertThat(session.state.value.isReady).isTrue()
    }

    @Test
    fun `the anchor is the create event of the room state or of a version 12 room id, never what the frames point at`() = runTest {
        val frame = aFrameItem("\$f1", DocsProtocol.frames(hello(), ANCHOR, "u1", "main").single())
        // The create event is in neither the room state nor the timeline, as in a DM or a room whose start is hidden.
        val noAnchor = DocsRoomFixture(listOf(frame), createEventId = null).session(backgroundScope)
        val v12RoomId = "a".repeat(43)
        val fromRoomId = DocsRoomFixture(
            listOf(aFrameItem("\$f2", DocsProtocol.frames(hello(), "\$$v12RoomId", "u2", "main").single())),
            roomId = RoomId("!$v12RoomId"),
            createEventId = null,
        ).session(backgroundScope)
        // A member's frame related to another event is not part of this room's document.
        val spoofed = aFrameItem("\$f3", DocsProtocol.frames(update(operation("s", text = "Spoofed")), "\$spoof", "u3", "main").single())
        val fromState = DocsRoomFixture(listOf(spoofed, frame)).session(backgroundScope)
        settle()
        assertThat(noAnchor.state.value.error).isEqualTo(DocumentError.AnchorError)
        assertThat(noAnchor.state.value.canEdit).isFalse()
        assertThat(fromRoomId.state.value.text).isEqualTo("Hello")
        assertThat(fromState.state.value.text).isEqualTo("Hello")
    }

    @Test
    fun `an engine that can't start is a load error`() = runTest {
        val fixture = DocsRoomFixture(listOf(aCreateItem()))
        fixture.engine.openError = YjsEngineException("no WebView")
        val session = fixture.session(backgroundScope)
        settle()
        assertThat(session.state.value.error).isEqualTo(DocumentError.LoadError)
        fixture.engine.openError = null
        session.retry()
        settle()
        assertThat(session.state.value.isReady).isTrue()
    }

    @Test
    fun `the catalog lists names, keeps main first by default and writes names after acknowledgement`() = runTest {
        val first =
            update(
                operation(
                    "c1",
                    key = "11111111-1111-4111-8111-111111111111",
                    value = Json.parseToJsonElement("""{"title": "Plans", "createdAt": 5}""").jsonObject
                )
            )
        val fixture = DocsRoomFixture(listOf(aCreateItem()) + frameItems("\$c", first, "c1", DocsProtocol.CATALOG_DOCUMENT_ID))
        val session = fixture.session(backgroundScope, documentId = DocsProtocol.CATALOG_DOCUMENT_ID)
        val catalog = DocumentCatalog(session, fixture.room, clock = { 42 }, newDocumentId = { "22222222-2222-4222-8222-222222222222" })
        var state: CatalogState? = null
        backgroundScope.launch { catalog.state.collect { state = it } }
        settle()
        assertThat(state!!.documents.map { it.id to it.title }).containsExactly(
            "main" to null,
            "11111111-1111-4111-8111-111111111111" to "Plans",
        ).inOrder()
        assertThat(state!!.canEdit).isTrue()
        var created: Result<String>? = null
        backgroundScope.launch { created = catalog.create("  Guide ") }
        settle()
        assertThat(created!!.getOrNull()).isEqualTo("22222222-2222-4222-8222-222222222222")
        assertThat(state!!.documents.last().title).isEqualTo("Guide")
        assertThat(state!!.documents.last().createdAt).isEqualTo(42)
        assertThat(fixture.sent.single().second).contains("\"doc\":\"catalog\"")
        var renamed: Result<Unit>? = null
        backgroundScope.launch { renamed = catalog.rename("main", "Notes") }
        settle()
        assertThat(renamed!!.isSuccess).isTrue()
        assertThat(state!!.documents.first { it.id == "main" }.title).isEqualTo("Notes")
        var invalid: Result<String>? = null
        backgroundScope.launch { invalid = catalog.create(" ") }
        settle()
        assertThat(invalid!!.isFailure).isTrue()
    }

    @Test
    fun `a catalog with an invalid entry is not ready, and names need an encrypted space`() = runTest {
        val bad = update(operation("c1", key = "not-a-uuid", value = Json.parseToJsonElement("""{"title": "Plans", "createdAt": 5}""").jsonObject))
        val invalidFixture = DocsRoomFixture(listOf(aCreateItem()) + frameItems("\$c", bad, "c1", DocsProtocol.CATALOG_DOCUMENT_ID))
        val invalid = DocumentCatalog(invalidFixture.session(backgroundScope, documentId = DocsProtocol.CATALOG_DOCUMENT_ID), invalidFixture.room)
        val plainFixture = DocsRoomFixture(listOf(aCreateItem()), roomInfo = aRoomInfo(isEncrypted = false, isSpace = true))
        val plain = DocumentCatalog(plainFixture.session(backgroundScope, documentId = DocsProtocol.CATALOG_DOCUMENT_ID), plainFixture.room)
        var invalidState: CatalogState? = null
        var plainState: CatalogState? = null
        backgroundScope.launch { invalid.state.collect { invalidState = it } }
        backgroundScope.launch { plain.state.collect { plainState = it } }
        settle()
        assertThat(invalidState!!.error).isEqualTo(DocumentError.InvalidUpdate)
        assertThat(invalidState!!.isReady).isFalse()
        assertThat(plainState!!.isReady).isTrue()
        assertThat(plainState!!.canEdit).isFalse()
        assertThat(plainState!!.documents.map { it.id }).containsExactly("main")
    }

    /** A room whose history takes [PAGES] pages to read, and whose anchor's relations are [related], or can't be read when null. */
    private class LongHistoryRoom(items: List<MatrixTimelineItem>, related: List<String>?) {
        var pagesRead = 0
        val fixture: DocsRoomFixture = DocsRoomFixture(
            items,
            hasMoreHistory = true,
            paginateResult = {
                pagesRead++
                if (pagesRead == PAGES) fixture.timeline.backwardPaginationStatus.value = Timeline.PaginationStatus(isPaginating = false, hasMoreToLoad = false)
                Result.success(pagesRead < PAGES)
            },
            fetchRelatedEventsJsonResult = { eventId, _, _ ->
                if (related == null) {
                    Result.failure(UnsupportedOperationException("No Matcha SDK patch"))
                } else {
                    Result.success(RawEventsPage(if (eventId.value == ANCHOR) related else emptyList(), null))
                }
            },
        )

        companion object {
            const val PAGES = 60
        }
    }

    private companion object {
        const val RELATION = """{"rel_type": "m.reference", "event_id": "$ANCHOR"}"""

        fun frameJson(eventId: String, content: String, isEncrypted: Boolean): String {
            val type = if (isEncrypted) DocsProtocol.EVENT_ROOM_ENCRYPTED else MatchaConfig.EVENT_DOCS_UPDATE
            return """{"type": "$type", "event_id": "$eventId", "sender": "$BOB", "origin_server_ts": 2, "content": $content}"""
        }
    }
}
