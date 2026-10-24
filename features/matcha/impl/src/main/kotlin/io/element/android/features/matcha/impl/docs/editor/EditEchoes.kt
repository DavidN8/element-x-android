/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs.editor

/**
 * Tells the editor's own edits coming back from the document apart from changes made elsewhere.
 * The editor sees the document a frame late, so while someone types fast the document text lags behind the field.
 */
internal class EditEchoes(private var confirmed: String) {
    private val pending = ArrayDeque<String>()

    /** The field now shows [text] and sent the edit that leads to it. */
    fun sent(text: String) = pending.addLast(text)

    /** True when [documentText] only confirms edits the field already shows, while newer ones are still on their way. */
    fun isBehindField(documentText: String): Boolean {
        val index = pending.indexOf(documentText)
        if (index >= 0) {
            repeat(index + 1) { pending.removeFirst() }
            confirmed = documentText
        }
        return pending.isNotEmpty() && documentText == confirmed
    }

    /** The field takes the document text as it is. */
    fun reset(documentText: String) {
        pending.clear()
        confirmed = documentText
    }
}
