/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.protocol

import io.element.android.appconfig.MatchaConfig
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.security.MessageDigest

/** The three board columns; the wire values are shared with the web client. */
enum class TaskStatus(val wireValue: String) {
    Todo("todo"),
    Doing("doing"),
    Done("done");

    companion object {
        fun fromWire(value: String?): TaskStatus? = entries.firstOrNull { it.wireValue == value }
    }
}

/** A reminder command sent for a card; only the bot's reply confirms that it is scheduled. */
data class ReminderRequestRef(
    val eventId: String,
    val at: Long,
)

/** One complete card revision. The newest revision of a card id wins. */
data class CardRevision(
    val cardId: String,
    val title: String,
    val description: String,
    val status: TaskStatus,
    val archived: Boolean,
    val threadId: String?,
    val dueAt: Long?,
    val reminderRequested: ReminderRequestRef?,
    val showToRoom: Boolean,
)

/** A card as shown on a board: a thread's card (one per thread) or an older standalone card. */
data class ProjectedCard(
    val id: String,
    val title: String,
    val description: String,
    val status: TaskStatus,
    val archived: Boolean,
    val threadId: String?,
    val dueAt: Long?,
    val reminderRequested: ReminderRequestRef?,
    val showToRoom: Boolean,
    /** Older cards that were linked to the same thread under a different card id; kept, never merged. */
    val earlierCards: List<CardRevision>,
)

/**
 * Per-room boards shared with the web client: a state pointer, a root timeline event, and card revisions related to the root.
 * Card content only ever travels in timeline events, which are encrypted in encrypted rooms.
 */
object KanbanProtocol {
    private const val MAX_TIMESTAMP = 253_402_300_799_999L
    private val cardIdRegex = Regex("^[a-zA-Z0-9-]{1,80}$")
    private val eventIdRegex = Regex("^\\$\\S{1,511}$")

    fun isValidTimestamp(value: Long?): Boolean = value != null && value in 1..MAX_TIMESTAMP

    fun isValidTitle(title: String): Boolean = title.isNotBlank() && title.length <= MatchaConfig.CARD_TITLE_MAX_LENGTH

    fun isValidDescription(description: String): Boolean = description.length <= MatchaConfig.CARD_DESCRIPTION_MAX_LENGTH

    /** The board root event id from the `com.matchahoneylatte.kanban` state content, or null. */
    fun parseBoardPointer(content: JsonObject?): String? {
        if (content == null || content.longOrNull("version") != 1L) return null
        return content.stringOrNull("event_id")?.takeIf { it.startsWith("$") }
    }

    fun boardPointerContent(rootId: String): String = buildJsonObject {
        put("version", 1)
        put("event_id", rootId)
    }.toString()

    fun boardRootContent(): String = buildJsonObject { put("version", 1) }.toString()

    /** Reads a card revision related to [rootId]; invalid revisions are dropped, exactly like the web client. */
    fun parseCard(event: RawEvent, rootId: String): CardRevision? {
        if (event.isRedacted || event.type != MatchaConfig.EVENT_KANBAN_CARD) return null
        val content = event.content
        if (content.longOrNull("version") != 1L || content.referenceTarget() != rootId) return null
        val cardId = content.stringOrNull("card_id")?.takeIf { cardIdRegex.matches(it) } ?: return null
        val title = content.stringOrNull("title")?.takeIf(::isValidTitle) ?: return null
        val description = content.stringOrNull("description")?.takeIf(::isValidDescription) ?: return null
        val status = TaskStatus.fromWire(content.stringOrNull("column")) ?: return null
        val archived = content.booleanOrNull("archived") ?: return null
        val showToRoom = if (content.containsKey("show_to_room")) {
            content.booleanOrNull("show_to_room") ?: return null
        } else {
            true
        }
        val reminder = content.objectOrNull("reminder_requested")?.let { request ->
            val eventId = request.stringOrNull("event_id")?.takeIf { eventIdRegex.matches(it) }
            val at = request.longOrNull("at")?.takeIf(::isValidTimestamp)
            if (eventId != null && at != null) ReminderRequestRef(eventId, at) else null
        }
        return CardRevision(
            cardId = cardId,
            title = title,
            description = description,
            status = status,
            archived = archived,
            threadId = content.stringOrNull("thread_id")?.takeIf { eventIdRegex.matches(it) },
            dueAt = content.longOrNull("due_at")?.takeIf(::isValidTimestamp),
            reminderRequested = reminder,
            showToRoom = showToRoom,
        )
    }

    /** Content of a complete card revision. */
    fun cardContent(card: CardRevision, rootId: String): String {
        require(isValidTitle(card.title) && isValidDescription(card.description)) { "Invalid card" }
        require(cardIdRegex.matches(card.cardId)) { "Invalid card id" }
        return buildJsonObject {
            put("version", 1)
            put("card_id", card.cardId)
            put("title", card.title.trim())
            put("description", card.description)
            put("column", card.status.wireValue)
            put("archived", card.archived)
            put("show_to_room", card.showToRoom)
            card.threadId?.let { put("thread_id", it) }
            card.dueAt?.takeIf(::isValidTimestamp)?.let { put("due_at", it) }
            card.reminderRequested?.let { request ->
                putJsonObject("reminder_requested") {
                    put("event_id", request.eventId)
                    put("at", request.at)
                }
            }
            putJsonObject("m.relates_to") {
                put("rel_type", REL_TYPE_REFERENCE)
                put("event_id", rootId)
            }
        }.toString()
    }

    /** Stable card id for a thread, identical to the web client's: `thread-` + SHA-256 hex of the thread root id. */
    fun threadCardId(threadId: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(threadId.toByteArray(Charsets.UTF_8))
        return "thread-" + digest.joinToString("") { "%02x".format(it) }
    }

    /** Keeps the newest revision of each card id, from revisions in server order, newest first. */
    fun latestRevisions(revisionsNewestFirst: List<CardRevision>): List<CardRevision> {
        val seen = LinkedHashMap<String, CardRevision>()
        for (revision in revisionsNewestFirst) seen.putIfAbsent(revision.cardId, revision)
        return seen.values.toList()
    }

    /**
     * Projects every thread onto exactly one card, keeping standalone cards.
     *
     * @param latestCards the newest revision of each card, newest card first (see [latestRevisions]).
     * @param threads every thread known for the room (thread list, threads referenced by cards, the thread the user came from),
     * with its resolved metadata, or null when the metadata could not be read.
     */
    fun project(latestCards: List<CardRevision>, threads: Map<String, ThreadMetadata?>): List<ProjectedCard> {
        val standalone = latestCards.filter { it.threadId == null }
        val linked = latestCards.filter { it.threadId != null }.groupBy { it.threadId!! }
        val threadIds = LinkedHashSet<String>().apply {
            addAll(threads.keys)
            addAll(linked.keys)
        }
        val projected = standalone.map { it.toProjected(earlierCards = emptyList()) }.toMutableList()
        for (threadId in threadIds) {
            val revisions = linked[threadId].orEmpty()
            val existing = revisions.firstOrNull()
            val metadata = threads[threadId]
            if (metadata == null) {
                // Unreadable metadata: keep what the card itself says rather than hiding it.
                if (existing != null) projected += existing.toProjected(earlierCards = revisions.drop(1))
                continue
            }
            projected += ProjectedCard(
                id = existing?.cardId ?: threadCardId(threadId),
                title = metadata.name,
                description = existing?.description.orEmpty(),
                status = existing?.status ?: TaskStatus.Todo,
                archived = if (metadata.archiveExplicit) metadata.archived else existing?.archived ?: metadata.archived,
                threadId = threadId,
                dueAt = existing?.dueAt,
                reminderRequested = existing?.reminderRequested,
                showToRoom = existing?.showToRoom ?: true,
                earlierCards = revisions.drop(1),
            )
        }
        return projected
    }

    private fun CardRevision.toProjected(earlierCards: List<CardRevision>) = ProjectedCard(
        id = cardId,
        title = title,
        description = description,
        status = status,
        archived = archived,
        threadId = threadId,
        dueAt = dueAt,
        reminderRequested = reminderRequested,
        showToRoom = showToRoom,
        earlierCards = earlierCards,
    )
}
