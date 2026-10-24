/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.fixtures

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import io.element.android.features.matcha.impl.docs.DocFrameResult
import io.element.android.features.matcha.impl.docs.DocPacket
import io.element.android.features.matcha.impl.docs.DocTooLargeException
import io.element.android.features.matcha.impl.docs.DocsProtocol
import io.element.android.features.matcha.impl.docs.transport.catalogDocuments
import io.element.android.features.matcha.impl.protocol.CardRevision
import io.element.android.features.matcha.impl.protocol.KanbanProtocol
import io.element.android.features.matcha.impl.protocol.ProjectedCard
import io.element.android.features.matcha.impl.protocol.RawEvent
import io.element.android.features.matcha.impl.protocol.ReminderProtocol
import io.element.android.features.matcha.impl.protocol.ReminderRequestRef
import io.element.android.features.matcha.impl.protocol.ReminderTime
import io.element.android.features.matcha.impl.protocol.ReminderUnit
import io.element.android.features.matcha.impl.protocol.TaskParticipants
import io.element.android.features.matcha.impl.protocol.TaskStatus
import io.element.android.features.matcha.impl.protocol.ThreadMetadata
import io.element.android.features.matcha.impl.protocol.ThreadMetadataPatch
import io.element.android.features.matcha.impl.protocol.ThreadMetadataProtocol
import io.element.android.features.matcha.impl.protocol.booleanOrNull
import io.element.android.features.matcha.impl.protocol.isShownOnBoard
import io.element.android.features.matcha.impl.protocol.longOrNull
import io.element.android.features.matcha.impl.protocol.objectOrNull
import io.element.android.features.matcha.impl.protocol.referenceTarget
import io.element.android.features.matcha.impl.protocol.stringOrNull
import io.element.android.features.matcha.impl.reminders.localInstant
import io.element.android.features.matcha.impl.reminders.validateReminder
import io.element.android.features.matcha.impl.tasks.LocalDeadline
import io.element.android.features.matcha.impl.tasks.toDeadline
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Test
import java.io.File
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.io.encoding.Base64

/**
 * Runs the protocol conformance fixtures in `docs/matcha/fixtures`, shared with the web and iOS clients (see its README): every
 * case must give exactly what this code computes. After a deliberate protocol change, regenerate the expected values from this code
 * with `MATCHA_FIXTURES_WRITE=1 ./gradlew :features:matcha:impl:testDebugUnitTest --tests '*MatchaFixturesTest*' --rerun`
 * and review the diff.
 */
class MatchaFixturesTest {
    @Test
    fun `every fixture case gives what the code computes`() {
        val write = System.getenv("MATCHA_FIXTURES_WRITE") == "1"
        val mismatches = mutableListOf<String>()
        val files = fixtureFiles()
        assertThat(files).isNotEmpty()
        for (file in files) {
            val fixture = Json.parseToJsonElement(file.readText()).jsonObject
            val op = fixture.getValue("op").jsonPrimitive.content
            val run = OPS[op] ?: error("${file.name}: unknown op $op")
            val cases = fixture.getValue("cases").jsonArray.map { element ->
                val case = element.jsonObject
                val name = case.getValue("name").jsonPrimitive.content
                val actual = outcome { run(case.getValue("input").jsonObject) }
                val expected = JsonObject(case.filterKeys { it == EXPECT || it == ERROR })
                if (actual != expected) mismatches += "${file.relativeTo(fixturesDir)} \"$name\": expected $expected, the code gives $actual"
                JsonObject(case.filterKeys { it != EXPECT && it != ERROR } + actual)
            }
            if (write) file.writeText(prettyJson.encodeToString(JsonElement.serializer(), JsonObject(fixture + ("cases" to JsonArray(cases)))) + "\n")
        }
        if (write) checksumsFile.writeText(checksums())
        assertWithMessage(mismatches.joinToString("\n")).that(mismatches).isEmpty()
    }

    @Test
    fun `the checksums list every fixture file`() {
        assertThat(checksumsFile.readText()).isEqualTo(checksums())
    }

    private val prettyJson = Json { prettyPrint = true }

    private val fixturesDir: File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { it.resolve("docs/matcha/fixtures") }
        .first { it.isDirectory }

    private val checksumsFile get() = fixturesDir.resolve("SHA256SUMS")

    private fun fixtureFiles() = fixturesDir.walkTopDown().filter { it.isFile && it.extension == "json" }.sortedBy { it.relativeTo(fixturesDir).path }.toList()

    /** In the format of `shasum -a 256`, so `shasum -a 256 -c SHA256SUMS` checks a copy. */
    private fun checksums() = fixtureFiles().joinToString("") { file ->
        val digest = MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
        "$digest  ${file.relativeTo(fixturesDir).invariantSeparatorsPath}\n"
    }
}

private const val EXPECT = "expect"
private const val ERROR = "error"

/** A case's outcome: the value computed, or the code of the error raised. */
private fun outcome(run: () -> JsonElement): JsonObject = try {
    JsonObject(mapOf(EXPECT to run()))
} catch (e: FixtureError) {
    JsonObject(mapOf(ERROR to JsonPrimitive(e.code)))
} catch (e: DocTooLargeException) {
    JsonObject(mapOf(ERROR to JsonPrimitive("too_large")))
} catch (e: IllegalArgumentException) {
    JsonObject(mapOf(ERROR to JsonPrimitive("invalid_argument")))
}

private class FixtureError(val code: String) : Exception(code)

private val OPS: Map<String, (JsonObject) -> JsonElement> = mapOf(
    "thread_metadata.parse_patch" to { input -> input.event()?.let { ThreadMetadataProtocol.parsePatch(it, input.string("rootId")) }.toJson() },
    "thread_metadata.resolve" to { input ->
        val rootId = input.string("rootId")
        val patches = input.events().mapNotNull { ThreadMetadataProtocol.parsePatch(it, rootId) }
        ThreadMetadataProtocol.resolve(rootId, input.stringOrNull("rootBody"), patches, input.booleanOrNull("legacyArchived")).toJson()
    },
    "thread_metadata.patch_content" to { input ->
        ThreadMetadataProtocol.patchContent(input.string("rootId"), input.stringOrNull("name"), input.booleanOrNull("archived")).toJsonElement()
    },
    "thread_metadata.bootstrap_reply_content" to { input ->
        ThreadMetadataProtocol.bootstrapReplyContent(input.string("rootId"), input.string("name")).toJsonElement()
    },
    "thread_metadata.bootstrap_root" to { input -> JsonPrimitive(input.event()?.let(ThreadMetadataProtocol::bootstrapRootId)) },
    "kanban.board_pointer" to { input -> JsonPrimitive(KanbanProtocol.parseBoardPointerJson(input["content"]?.takeUnless { it is JsonNull }?.toString())) },
    "kanban.parse_card" to { input -> input.event()?.let { KanbanProtocol.parseCard(it, input.string("rootId")) }?.toJson() ?: JsonNull },
    "kanban.card_content" to { input -> KanbanProtocol.cardContent(input.getValue("card").jsonObject.toCard(), input.string("rootId")).toJsonElement() },
    "kanban.thread_card_id" to { input -> JsonPrimitive(KanbanProtocol.threadCardId(input.string("threadId"))) },
    "kanban.board" to ::board,
    "tasks.local_deadline" to { input ->
        when (val deadline = LocalDateTime.parse(input.string("dateTime")).toDeadline(ZoneId.of(input.string("timeZone")))) {
            is LocalDeadline.Valid -> buildJsonObject {
                put("result", "valid")
                put("epochMillis", deadline.epochMillis)
            }
            LocalDeadline.Skipped -> buildJsonObject { put("result", "skipped") }
            LocalDeadline.OutOfRange -> buildJsonObject { put("result", "out_of_range") }
        }
    },
    "reminders.command" to { input -> JsonPrimitive(ReminderProtocol.command(input.reminderTime(), input.string("message"))) },
    "reminders.is_valid" to { input ->
        val now = input.long("now")
        buildJsonObject {
            put("dueAt", ReminderProtocol.dueAt(input.reminderTime(), now))
            put("isValid", ReminderProtocol.isValid(input.reminderTime(), input.string("message"), now))
        }
    },
    "reminders.validate" to { input ->
        val error = validateReminder(input.longOrNull("dueAt"), input.string("message"), input.long("maxMessageLength").toInt(), input.long("now"))
        JsonPrimitive(error?.name?.toSnakeCase())
    },
    "reminders.local_instant" to { input ->
        JsonPrimitive(localInstant(LocalDate.parse(input.string("date")), LocalTime.parse(input.string("time")), ZoneId.of(input.string("timeZone"))))
    },
    "reminders.parse_acknowledgement" to { input ->
        ReminderProtocol.parseAcknowledgement(input.stringOrNull("body"))?.let {
            buildJsonObject {
                put("reminderId", it.reminderId)
                put("dueAt", it.dueAt)
            }
        } ?: JsonNull
    },
    "reminders.with_link" to { input -> JsonPrimitive(ReminderProtocol.withLink(input.string("message"), input.stringOrNull("link"))) },
    "docs.frames" to { input ->
        val frames = DocsProtocol.frames(Base64.decode(input.string("update")), input.string("anchor"), input.string("updateId"), input.string("documentId"))
        JsonArray(frames.map { it.toJsonElement() })
    },
    "docs.parse_frame" to { input ->
        val event = RawEvent.parse(input.getValue("event").toString()) ?: throw FixtureError("not_an_event")
        when (val result = DocsProtocol.parseFrame(event, input.string("anchor"), input.string("documentId"))) {
            DocFrameResult.Unrelated -> buildJsonObject { put("result", "unrelated") }
            DocFrameResult.Invalid -> buildJsonObject { put("result", "invalid") }
            is DocFrameResult.Frame -> buildJsonObject {
                put("result", "frame")
                put("senderId", result.senderId)
                put("updateId", result.frame.updateId)
                put("kind", result.frame.kind.wire)
                put("chunks", result.frame.chunks)
                put("chunk", result.frame.chunk)
                put("data", result.frame.data?.let(Base64::encode))
            }
        }
    },
    "docs.assemble" to ::assemble,
    "docs.catalog" to { input ->
        val catalog = catalogDocuments(input.getValue("entries").jsonObject)
        buildJsonObject {
            put("documents", JsonArray(catalog.documents.map { document ->
                buildJsonObject {
                    put("id", document.id)
                    put("title", document.title)
                    put("createdAt", document.createdAt)
                }
            }))
            put("isInvalid", catalog.isInvalid)
        }
    },
)

/**
 * A room's threads and board as `MatchaRoomStore.buildSnapshot` builds them from the Matcha events (newest first), the SDK's thread
 * list and the legacy archive state. Thread replies among the events stand in for the replies the store reads per thread.
 */
private fun board(input: JsonObject): JsonElement {
    val rootId = input.string("rootId")
    val events = input.events()
    val listed = input.getValue("threads").jsonArray.map { it.jsonObject }.associateBy { it.string("id") }
    val legacyArchive = input.objectOrNull("legacyArchive").orEmpty().mapValues { it.value.jsonPrimitive.content.toBooleanStrict() }
    val latestCards = KanbanProtocol.latestRevisions(events.mapNotNull { KanbanProtocol.parseCard(it, rootId) })
    val patchesByRoot = events.mapNotNull { event ->
        event.content.referenceTarget()?.let { root -> ThreadMetadataProtocol.parsePatch(event, root)?.let { root to it } }
    }.groupBy({ it.first }, { it.second })
    val bootstrapped = events.mapNotNullTo(HashSet(), ThreadMetadataProtocol::bootstrapRootId)
    val repliers = TaskParticipants.repliers(events)
    val threadIds = LinkedHashSet<String>().apply {
        addAll(listed.keys)
        latestCards.mapNotNullTo(this) { it.threadId }
        addAll(legacyArchive.filterValues { it }.keys)
    }
    val threads = threadIds.map { id ->
        val item = listed[id]
        val rootBody = item?.stringOrNull("rootBody") ?: latestCards.firstOrNull { it.threadId == id }?.title
        FixtureThread(
            id = id,
            metadata = ThreadMetadataProtocol.resolve(id, rootBody, patchesByRoot[id].orEmpty(), legacyArchive[id]),
            lastActivity = item?.longOrNull("lastActivity") ?: 0,
            hasHiddenFirstReply = id in bootstrapped,
            participants = buildSet {
                item?.stringOrNull("rootSender")?.let(::add)
                item?.stringOrNull("latestSender")?.let(::add)
                addAll(repliers[id].orEmpty())
            },
        )
    }.sortedByDescending { it.lastActivity }
    val threadParticipants = threads.associate { it.id to it.participants }
    val standaloneAuthors = TaskParticipants.standaloneAuthors(events, rootId)
    val directMembers = input.strings("directMembers").toSet()
    val cards = KanbanProtocol.project(latestCards, threads.associate { it.id to it.metadata }).map { card ->
        card.copy(participants = TaskParticipants.of(card, threadParticipants, standaloneAuthors, directMembers))
    }
    val viewer = input.string("viewer")
    return buildJsonObject {
        put("threads", JsonArray(threads.map { it.toJson() }))
        put("cards", JsonArray(cards.map { it.toJson(viewer) }))
    }
}

private data class FixtureThread(
    val id: String,
    val metadata: ThreadMetadata,
    val lastActivity: Long,
    val hasHiddenFirstReply: Boolean,
    val participants: Set<String>,
) {
    fun toJson() = buildJsonObject {
        put("id", id)
        metadata.toJson().forEach { (key, value) -> put(key, value) }
        put("hasHiddenFirstReply", hasHiddenFirstReply)
        put("participants", participants.sorted().toJsonArray())
    }
}

/** Reassembles the updates the frames among the events (oldest first) complete, the way `RoomDocumentSession` ingests them. */
private fun assemble(input: JsonObject): JsonElement {
    val anchor = input.string("anchor")
    val documentId = input.string("documentId")
    val applied = HashSet<String>()
    val incoming = LinkedHashMap<String, DocPacket>()
    val updates = mutableListOf<String>()
    // Each event is read once by event id; events without one are skipped.
    for (event in input.events().filter { it.eventId != null }.distinctBy { it.eventId }) {
        if (DocsProtocol.isUndecryptedFrame(event, anchor)) throw FixtureError("decrypt_error")
        when (val result = DocsProtocol.parseFrame(event, anchor, documentId)) {
            DocFrameResult.Unrelated -> Unit
            DocFrameResult.Invalid -> throw FixtureError("invalid_update")
            is DocFrameResult.Frame -> {
                val key = DocsProtocol.packetKey(result.senderId, result.frame.updateId)
                if (key !in applied) {
                    val packet = (incoming[key] ?: DocPacket(result.frame.chunks)).plus(result.frame) ?: throw FixtureError("invalid_update")
                    if (packet.isComplete) {
                        updates += Base64.encode(packet.update())
                        applied += key
                        incoming -= key
                    } else {
                        incoming[key] = packet
                    }
                }
            }
        }
    }
    return buildJsonObject {
        put("updates", updates.toJsonArray())
        // A committed update that misses parts makes the document incomplete; parts without a commit yet are only pending.
        put("missing", incoming.filterValues { it.isCommitted }.keys.sorted().toJsonArray())
        put("pending", incoming.filterValues { !it.isCommitted }.keys.sorted().toJsonArray())
    }
}

private fun JsonObject.string(key: String): String = stringOrNull(key) ?: error("Missing string $key")

private fun JsonObject.long(key: String): Long = longOrNull(key) ?: error("Missing number $key")

private fun JsonObject.strings(key: String): List<String> = (this[key] as? JsonArray).orEmpty().map { it.jsonPrimitive.content }

/** The input's event; null when it is not an event, which the SDK layer never hands over. */
private fun JsonObject.event(): RawEvent? = RawEvent.parse(getValue("event").toString())

private fun JsonObject.events(): List<RawEvent> = getValue("events").jsonArray.mapNotNull { RawEvent.parse(it.toString()) }

private fun JsonObject.reminderTime(): ReminderTime {
    val time = getValue("time").jsonObject
    time.longOrNull("at")?.let { return ReminderTime.At(it) }
    return ReminderTime.In(time.long("in"), ReminderUnit.valueOf(time.string("unit").replaceFirstChar(Char::uppercaseChar)))
}

private fun JsonObject.toCard() = CardRevision(
    cardId = string("cardId"),
    title = string("title"),
    description = string("description"),
    status = TaskStatus.fromWire(string("status")) ?: error("Unknown status"),
    archived = booleanOrNull("archived") ?: error("Missing archived"),
    threadId = stringOrNull("threadId"),
    dueAt = longOrNull("dueAt"),
    reminderRequested = objectOrNull("reminderRequested")?.let { ReminderRequestRef(it.string("eventId"), it.long("at")) },
    showToRoom = booleanOrNull("showToRoom") ?: error("Missing showToRoom"),
)

private fun String.toJsonElement(): JsonElement = Json.parseToJsonElement(this)

private fun List<String>.toJsonArray() = buildJsonArray { forEach { add(JsonPrimitive(it)) } }

private fun String.toSnakeCase() = replace(Regex("(?<=[a-z])(?=[A-Z])"), "_").lowercase()

private fun ThreadMetadataPatch?.toJson(): JsonElement = this?.let {
    buildJsonObject {
        put("name", name)
        put("archived", archived)
    }
} ?: JsonNull

private fun ThreadMetadata.toJson() = buildJsonObject {
    put("name", name)
    put("archived", archived)
    put("archiveExplicit", archiveExplicit)
    put("nameSource", nameSource.name.toSnakeCase())
}

private fun ReminderRequestRef?.toJson(): JsonElement = this?.let {
    buildJsonObject {
        put("eventId", eventId)
        put("at", at)
    }
} ?: JsonNull

private fun CardRevision.toJson() = buildJsonObject {
    put("cardId", cardId)
    put("title", title)
    put("description", description)
    put("status", status.wireValue)
    put("archived", archived)
    put("threadId", threadId)
    put("dueAt", dueAt)
    put("reminderRequested", reminderRequested.toJson())
    put("showToRoom", showToRoom)
}

private fun ProjectedCard.toJson(viewer: String) = buildJsonObject {
    put("id", id)
    put("title", title)
    put("description", description)
    put("status", status.wireValue)
    put("archived", archived)
    put("threadId", threadId)
    put("dueAt", dueAt)
    put("reminderRequested", reminderRequested.toJson())
    put("showToRoom", showToRoom)
    put("hasRevision", hasRevision)
    put("earlierCardIds", earlierCards.map { it.cardId }.toJsonArray())
    put("participants", participants.sorted().toJsonArray())
    put("isShownToViewer", isShownOnBoard(viewer))
}
