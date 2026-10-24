/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.protocol

import com.google.common.truth.Truth.assertThat
import io.element.android.appconfig.MatchaConfig
import org.junit.Test

class TaskParticipantsTest {
    private val root = "\$board"

    private fun event(type: String, sender: String?, content: String, redacted: Boolean = false): RawEvent {
        val senderField = sender?.let { """"sender": "$it", """ }.orEmpty()
        val unsigned = if (redacted) """, "unsigned": {"redacted_because": {}}""" else ""
        return RawEvent.parse("""{"type": "$type", "event_id": "${'$'}e", $senderField"content": $content$unsigned}""")!!
    }

    private fun reply(sender: String?, thread: String, relation: String = REL_TYPE_THREAD, redacted: Boolean = false) =
        event("m.room.message", sender, """{"m.relates_to": {"rel_type": "$relation", "event_id": "$thread"}}""", redacted)

    private fun revision(sender: String, cardId: String, threadId: String?) = event(
        MatchaConfig.EVENT_KANBAN_CARD,
        sender,
        KanbanProtocol.cardContent(CardRevision(cardId, "Title", "", TaskStatus.Todo, false, threadId, null, null, false), root),
    )

    private fun aCard(id: String, threadId: String?, showToRoom: Boolean = false, participants: Set<String> = emptySet()) = ProjectedCard(
        id = id,
        title = "Title",
        description = "",
        status = TaskStatus.Todo,
        archived = false,
        threadId = threadId,
        dueAt = null,
        reminderRequested = null,
        showToRoom = showToRoom,
        earlierCards = emptyList(),
        hasRevision = true,
        participants = participants,
    )

    @Test
    fun `repliers are the senders of the thread replies, not of redacted ones or other relations`() {
        val repliers = TaskParticipants.repliers(
            listOf(
                reply("@bob:x", "\$t1"),
                reply("@carol:x", "\$t1"),
                reply("@bob:x", "\$t1"),
                reply("@dan:x", "\$t2"),
                reply("@redacted:x", "\$t1", redacted = true),
                reply("@reactor:x", "\$t1", relation = "m.annotation"),
                reply("not a user id", "\$t1"),
                reply(null, "\$t1"),
            )
        )
        assertThat(repliers).containsExactly("\$t1", setOf("@bob:x", "@carol:x"), "\$t2", setOf("@dan:x"))
        assertThat(repliers.getValue("\$t1")).containsExactly("@bob:x", "@carol:x").inOrder()
    }

    @Test
    fun `an older card's participants are the authors of its revisions without a thread`() {
        val authors = TaskParticipants.standaloneAuthors(
            listOf(
                revision("@bob:x", "legacy", threadId = null),
                revision("@carol:x", "legacy", threadId = null),
                revision("@dan:x", "legacy", threadId = "\$t1"),
                revision("@erin:x", "other", threadId = null),
            ),
            root,
        )
        assertThat(authors).containsExactly("legacy", setOf("@bob:x", "@carol:x"), "other", setOf("@erin:x"))
    }

    @Test
    fun `a task involves its thread's people or its authors, and in a direct chat its members`() {
        val threads = mapOf("\$t1" to setOf("@bob:x", "@carol:x"))
        val authors = mapOf("legacy" to setOf("@dan:x"), "thread-card" to setOf("@writer:x"))
        assertThat(TaskParticipants.of(aCard("thread-card", "\$t1"), threads, authors, emptySet())).containsExactly("@bob:x", "@carol:x")
        assertThat(TaskParticipants.of(aCard("legacy", null), threads, authors, emptySet())).containsExactly("@dan:x")
        assertThat(TaskParticipants.of(aCard("unknown", "\$t9"), threads, authors, emptySet())).isEmpty()
        assertThat(TaskParticipants.of(aCard("unknown", "\$t9"), threads, authors, setOf("@me:x", "@bob:x"))).containsExactly("@me:x", "@bob:x")
    }

    @Test
    fun `a task is on the board of everyone when shared with the chat, otherwise of the people it involves`() {
        assertThat(aCard("c", null, showToRoom = true).isShownOnBoard("@me:x")).isTrue()
        assertThat(aCard("c", null, showToRoom = false, participants = setOf("@me:x")).isShownOnBoard("@me:x")).isTrue()
        assertThat(aCard("c", null, showToRoom = false, participants = setOf("@bob:x")).isShownOnBoard("@me:x")).isFalse()
    }
}
