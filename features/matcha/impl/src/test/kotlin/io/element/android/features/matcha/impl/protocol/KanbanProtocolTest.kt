/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.protocol

import com.google.common.truth.Truth.assertThat
import io.element.android.appconfig.MatchaConfig
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Test

class KanbanProtocolTest {
    private val root = "\$board:example.org"

    private fun cardEvent(fields: String, redacted: Boolean = false): RawEvent {
        val unsigned = if (redacted) """, "unsigned": {"redacted_because": {}}""" else ""
        return RawEvent.parse(
            """{"type": "${MatchaConfig.EVENT_KANBAN_CARD}", "event_id": "${'$'}e", "sender": "@a:b", "origin_server_ts": 1,
                "content": {"version": 1, $fields, "m.relates_to": {"rel_type": "m.reference", "event_id": "$root"}}$unsigned}""".trimIndent()
        )!!
    }

    private val valid = """"card_id": "c-1", "title": "Write spec", "description": "", "column": "doing", "archived": false"""

    @Test
    fun `a valid card is read with defaults for optional fields`() {
        val card = KanbanProtocol.parseCard(cardEvent(valid), root)!!
        assertThat(card).isEqualTo(
            CardRevision(
                cardId = "c-1",
                title = "Write spec",
                description = "",
                status = TaskStatus.Doing,
                archived = false,
                threadId = null,
                dueAt = null,
                reminderRequested = null,
                showToRoom = true,
            )
        )
    }

    @Test
    fun `optional fields are read when valid and dropped when not`() {
        val card = KanbanProtocol.parseCard(
            cardEvent(
                """$valid, "thread_id": "${'$'}t", "due_at": 1767225600000, """ +
                    """"reminder_requested": {"event_id": "${'$'}cmd", "at": 1767225600000}, "show_to_room": false"""
            ),
            root,
        )!!
        assertThat(card.threadId).isEqualTo("\$t")
        assertThat(card.dueAt).isEqualTo(1_767_225_600_000L)
        assertThat(card.reminderRequested).isEqualTo(ReminderRequestRef("\$cmd", 1_767_225_600_000L))
        assertThat(card.showToRoom).isFalse()

        val lenient = KanbanProtocol.parseCard(cardEvent("""$valid, "thread_id": "not-an-id", "due_at": -5, "reminder_requested": {"event_id": "x"}"""), root)!!
        assertThat(lenient.threadId).isNull()
        assertThat(lenient.dueAt).isNull()
        assertThat(lenient.reminderRequested).isNull()
    }

    @Test
    fun `invalid revisions are rejected`() {
        listOf(
            """"card_id": "bad id", "title": "t", "description": "", "column": "todo", "archived": false""",
            """"card_id": "c", "title": "  ", "description": "", "column": "todo", "archived": false""",
            """"card_id": "c", "title": "${"t".repeat(201)}", "description": "", "column": "todo", "archived": false""",
            """"card_id": "c", "title": "t", "description": "${"d".repeat(4001)}", "column": "todo", "archived": false""",
            """"card_id": "c", "title": "t", "description": "", "column": "blocked", "archived": false""",
            """"card_id": "c", "title": "t", "description": "", "column": "todo", "archived": "no"""",
            """"card_id": "c", "title": "t", "description": "", "column": "todo", "archived": false, "show_to_room": "yes"""",
        ).forEach { assertThat(KanbanProtocol.parseCard(cardEvent(it), root)).isNull() }
        assertThat(KanbanProtocol.parseCard(cardEvent(valid, redacted = true), root)).isNull()
        assertThat(KanbanProtocol.parseCard(cardEvent(valid), "\$another-board")).isNull()
    }

    @Test
    fun `written cards round-trip`() {
        val card = CardRevision("c-1", "Title", "Notes", TaskStatus.Done, true, "\$t", 42L, ReminderRequestRef("\$cmd", 43L), false)
        val content = KanbanProtocol.cardContent(card, root)
        val event = RawEvent.parse("""{"type": "${MatchaConfig.EVENT_KANBAN_CARD}", "content": $content}""")!!
        assertThat(KanbanProtocol.parseCard(event, root)).isEqualTo(card)
    }

    @Test
    fun `show_to_room is read when a boolean, absent means shown to everyone, and anything else makes the card invalid`() {
        assertThat(KanbanProtocol.parseCard(cardEvent(valid), root)!!.showToRoom).isTrue()
        assertThat(KanbanProtocol.parseCard(cardEvent("""$valid, "show_to_room": true"""), root)!!.showToRoom).isTrue()
        assertThat(KanbanProtocol.parseCard(cardEvent("""$valid, "show_to_room": false"""), root)!!.showToRoom).isFalse()
        listOf("\"true\"", "1", "null", "{}").forEach { value ->
            assertThat(KanbanProtocol.parseCard(cardEvent("""$valid, "show_to_room": $value"""), root)).isNull()
        }
    }

    @Test
    fun `every written card says whether it is shown to everyone`() {
        val shown = CardRevision("c-1", "Title", "", TaskStatus.Todo, false, null, null, null, showToRoom = true)
        assertThat(KanbanProtocol.cardContent(shown, root)).contains(""""show_to_room":true""")
        assertThat(KanbanProtocol.cardContent(shown.copy(showToRoom = false), root)).contains(""""show_to_room":false""")
    }

    @Test
    fun `a thread's card without a revision is shown to everyone, and one with a revision keeps its value`() {
        val hidden = CardRevision("thread-x", "t", "", TaskStatus.Todo, false, "\$t1", null, null, showToRoom = false)
        val cards = KanbanProtocol.project(
            latestCards = listOf(hidden),
            threads = mapOf(
                "\$t1" to ThreadMetadata("One", archived = false, archiveExplicit = false),
                "\$t2" to ThreadMetadata("Two", archived = false, archiveExplicit = false),
            ),
        )
        assertThat(cards.map { it.showToRoom }).containsExactly(false, true).inOrder()
    }

    @Test
    fun `the board pointer must be version 1 with an event id`() {
        fun parse(json: String) = KanbanProtocol.parseBoardPointer(Json.parseToJsonElement(json).jsonObject)
        assertThat(parse("""{"version": 1, "event_id": "${'$'}root"}""")).isEqualTo("\$root")
        assertThat(parse("""{"version": 2, "event_id": "${'$'}root"}""")).isNull()
        assertThat(parse("""{"version": 1, "event_id": "root"}""")).isNull()
        assertThat(KanbanProtocol.parseBoardPointer(null)).isNull()
    }

    @Test
    fun `thread card ids match the web client`() {
        // SHA-256 of the UTF-8 string "$abc", as the web client computes it with crypto.subtle.digest.
        assertThat(KanbanProtocol.threadCardId("\$abc")).isEqualTo("thread-896f8c657f9fa111b8dbef8fd2b536b3b2dad5977bb9131dd920378e53a7ad7c")
    }

    @Test
    fun `the newest revision of each card wins`() {
        val newest = CardRevision("c", "New", "", TaskStatus.Done, false, null, null, null, true)
        val older = newest.copy(title = "Old", status = TaskStatus.Todo)
        assertThat(KanbanProtocol.latestRevisions(listOf(newest, older))).containsExactly(newest)
    }

    @Test
    fun `every thread projects to exactly one card named after the thread`() {
        val linked = CardRevision("thread-x", "Stale title", "Notes", TaskStatus.Doing, false, "\$t1", 5L, null, true)
        val earlier = CardRevision("c-old", "Earlier", "", TaskStatus.Todo, false, "\$t1", null, null, true)
        val standalone = CardRevision("c-2", "Standalone", "", TaskStatus.Todo, false, null, null, null, true)
        val cards = KanbanProtocol.project(
            latestCards = listOf(linked, standalone, earlier),
            threads = mapOf(
                "\$t1" to ThreadMetadata("Thread name", archived = false, archiveExplicit = false),
                "\$t2" to ThreadMetadata("New thread", archived = false, archiveExplicit = false),
            ),
        )
        assertThat(cards.map { it.title }).containsExactly("Standalone", "Thread name", "New thread").inOrder()
        val first = cards[1]
        assertThat(first.id).isEqualTo("thread-x")
        assertThat(first.status).isEqualTo(TaskStatus.Doing)
        assertThat(first.description).isEqualTo("Notes")
        assertThat(first.earlierCards).containsExactly(earlier)
        assertThat(first.hasRevision).isTrue()
        val fresh = cards[2]
        assertThat(fresh.id).isEqualTo(KanbanProtocol.threadCardId("\$t2"))
        assertThat(fresh.status).isEqualTo(TaskStatus.Todo)
        // Nothing read so far describes the new thread's card: it only shows the defaults.
        assertThat(fresh.hasRevision).isFalse()
    }

    @Test
    fun `a card unlinked from its thread gives way to the thread's card, so ids stay unique`() {
        val unlinked = CardRevision(KanbanProtocol.threadCardId("\$t"), "Unlinked", "Notes", TaskStatus.Done, false, null, null, null, true)
        val other = CardRevision("c-2", "Other", "", TaskStatus.Todo, false, null, null, null, true)
        val cards = KanbanProtocol.project(listOf(unlinked, other), mapOf("\$t" to ThreadMetadata("Thread", archived = false, archiveExplicit = false)))
        assertThat(cards.map { it.id }).containsExactly(KanbanProtocol.threadCardId("\$t"), "c-2").inOrder()
        assertThat(cards.first().title).isEqualTo("Thread")
        assertThat(cards.first().threadId).isEqualTo("\$t")
    }

    @Test
    fun `an explicit thread archive wins over the card, otherwise the card decides`() {
        val card = CardRevision("c", "t", "", TaskStatus.Todo, true, "\$t", null, null, true)
        val explicit = KanbanProtocol.project(listOf(card), mapOf("\$t" to ThreadMetadata("n", archived = false, archiveExplicit = true)))
        assertThat(explicit.single().archived).isFalse()
        val implicit = KanbanProtocol.project(listOf(card), mapOf("\$t" to ThreadMetadata("n", archived = false, archiveExplicit = false)))
        assertThat(implicit.single().archived).isTrue()
    }

    @Test
    fun `a thread with unreadable metadata keeps its card as written`() {
        val card = CardRevision("c", "As written", "", TaskStatus.Done, false, "\$t", null, null, true)
        val projected = KanbanProtocol.project(listOf(card), mapOf("\$t" to null, "\$t-unknown" to null))
        assertThat(projected.map { it.title }).containsExactly("As written")
    }
}
