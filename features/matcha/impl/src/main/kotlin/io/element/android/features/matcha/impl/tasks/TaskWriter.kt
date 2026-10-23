/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.tasks

import io.element.android.appconfig.MatchaConfig
import io.element.android.features.matcha.impl.protocol.CardRevision
import io.element.android.features.matcha.impl.protocol.KanbanProtocol
import io.element.android.features.matcha.impl.protocol.ProjectedCard
import io.element.android.features.matcha.impl.protocol.ThreadMetadataProtocol
import io.element.android.features.matcha.impl.store.MatchaRoomStore
import io.element.android.libraries.core.extensions.runCatchingExceptions
import timber.log.Timber

/** Why a card write did not happen. */
enum class TaskWriteError {
    /** The room now points at another board than the one the user saw. */
    BoardChanged,

    /** Another card already belongs to the thread. */
    ThreadHasTask,

    /** The send failed or was not allowed. */
    Failed,
}

enum class CreateBoardResult {
    Created,

    /** Someone else created the board meanwhile; it is shown instead. */
    AlreadyExists,
    Failed,
}

/**
 * Writes a chat's task board the way the web client does: card content only travels in timeline events, every write is a complete
 * revision, and a linked card's thread is renamed or archived before the revision that reflects it.
 */
class TaskWriter(private val store: MatchaRoomStore) {
    /**
     * Creates the board, after re-reading the room state so a board created elsewhere (whose cards may not be loaded yet) is never
     * replaced: sends the root, then the state pointing at it.
     */
    suspend fun createBoard(): CreateBoardResult {
        val existing = store.readBoardPointer().getOrElse { return CreateBoardResult.Failed }
        if (existing != null) return CreateBoardResult.AlreadyExists
        val root = store.send(MatchaConfig.EVENT_KANBAN_BOARD, KanbanProtocol.boardRootContent()).getOrElse {
            Timber.w(it, "Could not send the board root")
            return CreateBoardResult.Failed
        }
        store.sendState(MatchaConfig.STATE_KANBAN, "", KanbanProtocol.boardPointerContent(root.value)).onFailure {
            Timber.w(it, "Could not point the room at its board")
            return CreateBoardResult.Failed
        }
        store.rememberCreatedBoard(root.value)
        return CreateBoardResult.Created
    }

    /**
     * Sends a complete revision of a card. Returns null once sent.
     *
     * @param boardRootId the board the user was looking at; the write is refused when the room now points elsewhere.
     * @param card the whole card as it should be.
     * @param renameThread true to rename the card's thread to the card title first.
     * @param archiveThread true to archive or restore the card's thread like the card first.
     */
    suspend fun write(
        boardRootId: String,
        card: CardRevision,
        renameThread: Boolean = false,
        archiveThread: Boolean = false,
    ): TaskWriteError? {
        val snapshot = store.snapshot.value
        val board = snapshot.board
        // ponytail: checks the pointer the store last read, where the web client re-reads room state; the SDK only caches it anyway.
        if (board?.rootId != boardRootId) return refuse(TaskWriteError.BoardChanged)
        if (!snapshot.permissions.canWriteCards) return refuse(TaskWriteError.Failed)
        val content = runCatchingExceptions { KanbanProtocol.cardContent(card, boardRootId) }.getOrElse { return refuse(TaskWriteError.Failed) }
        val threadId = card.threadId
        if (threadId != null) {
            if (board.cards.any { it.threadId == threadId && it.id != card.cardId }) return refuse(TaskWriteError.ThreadHasTask)
            if (renameThread || archiveThread) {
                val patch = runCatchingExceptions {
                    ThreadMetadataProtocol.patchContent(
                        rootId = threadId,
                        name = card.title.takeIf { renameThread },
                        archived = card.archived.takeIf { archiveThread },
                    )
                }.getOrElse { return refuse(TaskWriteError.Failed) }
                store.send(MatchaConfig.EVENT_THREAD_METADATA, patch).onFailure {
                    Timber.w(it, "Could not update thread %s of card %s", threadId, card.cardId)
                    return TaskWriteError.Failed
                }
            }
        }
        return store.send(MatchaConfig.EVENT_KANBAN_CARD, content).fold(
            onSuccess = { null },
            onFailure = {
                Timber.w(it, "Could not save card %s", card.cardId)
                TaskWriteError.Failed
            },
        )
    }

    private fun refuse(error: TaskWriteError): TaskWriteError {
        Timber.w("Card write refused: %s", error)
        return error
    }
}

/** The complete revision that reproduces this card as shown, to be changed and written back. */
internal fun ProjectedCard.toRevision() = CardRevision(
    cardId = id,
    title = title,
    description = description,
    status = status,
    archived = archived,
    threadId = threadId,
    dueAt = dueAt,
    reminderRequested = reminderRequested,
    showToRoom = showToRoom,
)
