/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.protocol

import io.element.android.libraries.matrix.api.core.MatrixPatterns

/**
 * Who a task involves, as on the web client: a thread's task involves the thread's root sender and everyone who replied in it; an
 * older standalone card involves the authors of its revisions. In a direct chat its members are involved in every task.
 */
object TaskParticipants {
    /** The senders of the thread replies among [events], by thread root id, in the order read. Redacted replies don't count. */
    fun repliers(events: List<RawEvent>): Map<String, Set<String>> = events
        .mapNotNull { event -> event.threadRootId()?.let { root -> event.validSender()?.let { root to it } } }
        .groupByToSets()

    /** The authors of each card's revisions that have no thread, by card id: an older standalone card's participants. */
    fun standaloneAuthors(events: List<RawEvent>, rootId: String): Map<String, Set<String>> = events
        .mapNotNull { event ->
            val card = KanbanProtocol.parseCard(event, rootId)?.takeIf { it.threadId == null }
            card?.let { event.validSender()?.let { card.cardId to it } }
        }
        .groupByToSets()

    /**
     * The participants of [card].
     *
     * @param card a card of the board.
     * @param threadParticipants each thread's root sender and repliers, by thread root id.
     * @param standaloneAuthors see [standaloneAuthors].
     * @param directMembers the current user and the other members of a direct chat, or empty in any other chat.
     */
    fun of(
        card: ProjectedCard,
        threadParticipants: Map<String, Set<String>>,
        standaloneAuthors: Map<String, Set<String>>,
        directMembers: Set<String>,
    ): Set<String> {
        val own = if (card.threadId != null) threadParticipants[card.threadId] else standaloneAuthors[card.id]
        return own.orEmpty() + directMembers
    }

    private fun RawEvent.validSender(): String? = senderId?.takeIf(MatrixPatterns::isUserId)

    /** The root of the thread this event replies in. A relation stays readable in an event that can't be decrypted. */
    private fun RawEvent.threadRootId(): String? = content.takeUnless { isRedacted }?.relationTarget(REL_TYPE_THREAD)

    private fun List<Pair<String, String>>.groupByToSets(): Map<String, Set<String>> = groupBy({ it.first }, { it.second }).mapValues { it.value.toSet() }
}

/** On a chat's board, a task shared with the chat is shown to everyone, any other only to the people it involves. */
fun ProjectedCard.isShownOnBoard(userId: String): Boolean = showToRoom || userId in participants
