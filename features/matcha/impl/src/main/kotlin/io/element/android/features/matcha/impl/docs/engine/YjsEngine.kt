/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs.engine

import kotlinx.serialization.json.JsonObject

/** Runs the Yjs documents of Matcha docs. Documents live in memory only. */
interface YjsEngine {
    /** Opens a new, empty document. Throws [YjsEngineException] when the engine can't run. */
    suspend fun open(): YjsDocument
}

/** One Yjs document, with its `Y.Text` "markdown" and its `Y.Map` "documents" (used by the catalog). */
interface YjsDocument {
    /** Applies a remote update after checking it on a copy; false when the update is malformed and was not applied. */
    suspend fun apply(update: ByteArray): Boolean

    /** Applies remote updates in order, each checked like [apply], and stops at the first malformed one; returns how many applied. */
    suspend fun applyAll(updates: List<ByteArray>): Int

    /** The current content, and whether updates are waiting for causal predecessors that have not arrived. */
    suspend fun snapshot(): YjsSnapshot

    /** Applies local text changes and returns the update they produced, or null when nothing changed. */
    suspend fun edit(changes: List<TextChange>): ByteArray?

    /** Sets an entry of the "documents" map and returns the update it produced, or null when nothing changed. */
    suspend fun setEntry(key: String, value: JsonObject): ByteArray?

    /** Merges updates into one. */
    suspend fun merge(updates: List<ByteArray>): ByteArray

    fun close()
}

data class YjsSnapshot(
    val text: String,
    val entries: JsonObject,
    val isMissingUpdates: Boolean,
)

/** Replaces [remove] UTF-16 units at [index] with [insert]. */
data class TextChange(
    val index: Int,
    val remove: Int,
    val insert: String,
)

class YjsEngineException(message: String, cause: Throwable? = null) : Exception(message, cause)
