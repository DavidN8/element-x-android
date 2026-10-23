/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs.engine

/**
 * Turns a text field's edits into the smallest Yjs text changes, so characters other people may be editing keep their identity.
 *
 * ponytail: a common prefix and suffix, plus the split of a wrap (text kept between two insertions, as the formatting buttons do).
 * Edits that touch several separate places at once (prefixing many list lines) become one replacement; a character diff
 * would keep those identities too, if concurrent edits of reformatted lines turn out to matter.
 */
object TextDiff {
    fun changes(before: String, after: String): List<TextChange> {
        if (before == after) return emptyList()
        var prefix = 0
        val maxPrefix = minOf(before.length, after.length)
        while (prefix < maxPrefix && before[prefix] == after[prefix]) prefix++
        var suffix = 0
        val maxSuffix = minOf(before.length, after.length) - prefix
        while (suffix < maxSuffix && before[before.length - 1 - suffix] == after[after.length - 1 - suffix]) suffix++
        // Never split a surrogate pair.
        if (prefix > 0 && Character.isHighSurrogate(before[prefix - 1])) prefix--
        if (suffix > 0 && Character.isLowSurrogate(before[before.length - suffix])) suffix--
        val removed = before.substring(prefix, before.length - suffix)
        val inserted = after.substring(prefix, after.length - suffix)
        val kept = if (removed.isEmpty()) -1 else inserted.indexOf(removed)
        if (kept >= 0) {
            // A wrap: insert around the existing text instead of replacing it.
            return listOfNotNull(
                inserted.substring(0, kept).takeIf { it.isNotEmpty() }?.let { TextChange(prefix, 0, it) },
                inserted.substring(kept + removed.length).takeIf { it.isNotEmpty() }?.let { TextChange(prefix + removed.length, 0, it) },
            )
        }
        return listOf(TextChange(prefix, removed.length, inserted))
    }

    /** Applies non-overlapping changes made against [text]. */
    fun apply(text: String, changes: List<TextChange>): String {
        val builder = StringBuilder(text)
        changes.sortedByDescending { it.index }.forEach { builder.replace(it.index, it.index + it.remove, it.insert) }
        return builder.toString()
    }

    /**
     * Moves changes the user made against [base] onto [current], which also contains edits that arrived meanwhile. A change that
     * overlaps those edits is dropped: the remote edit wins and the field shows the merged text.
     */
    fun rebase(changes: List<TextChange>, base: String, current: String): List<TextChange> {
        if (base == current) return changes
        val remote = changes(base, current)
        return changes.mapNotNull { change ->
            var index = change.index
            for (other in remote) {
                when {
                    change.index + change.remove <= other.index -> Unit
                    change.index >= other.index + other.remove -> index += other.insert.length - other.remove
                    else -> return@mapNotNull null
                }
            }
            change.copy(index = index)
        }
    }
}
