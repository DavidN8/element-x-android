/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TextDiffTest {
    @Test
    fun `typing, deleting and replacing are one change`() {
        assertThat(TextDiff.changes("Hello", "Hello")).isEmpty()
        assertThat(TextDiff.changes("Hello", "Hello!")).containsExactly(TextChange(5, 0, "!"))
        assertThat(TextDiff.changes("Hello", "Hllo")).containsExactly(TextChange(1, 1, ""))
        assertThat(TextDiff.changes("a cat sat", "a dog sat")).containsExactly(TextChange(2, 3, "dog"))
        assertThat(TextDiff.changes("", "New")).containsExactly(TextChange(0, 0, "New"))
    }

    @Test
    fun `a wrap keeps the wrapped text`() {
        assertThat(TextDiff.changes("say hi now", "say **hi** now")).containsExactly(TextChange(4, 0, "**"), TextChange(6, 0, "**")).inOrder()
        assertThat(TextDiff.changes("link", "[link](https://)")).containsExactly(TextChange(0, 0, "["), TextChange(4, 0, "](https://)")).inOrder()
    }

    @Test
    fun `surrogate pairs are never split`() {
        val changes = TextDiff.changes("a😀b", "a😃b")
        assertThat(changes).containsExactly(TextChange(1, 2, "😃"))
        assertThat(TextDiff.apply("a😀b", changes)).isEqualTo("a😃b")
    }

    @Test
    fun `applying changes gives the edited text`() {
        listOf("" to "abc", "abc" to "", "hello world" to "hello brave new world", "x" to "**x**", "one\ntwo" to "- one\n- two").forEach { (before, after) ->
            assertThat(TextDiff.apply(before, TextDiff.changes(before, after))).isEqualTo(after)
        }
    }

    @Test
    fun `edits are moved past remote edits before them, and dropped when they overlap`() {
        val local = TextDiff.changes("Hello", "Hello!")
        assertThat(TextDiff.apply("Oh Hello", TextDiff.rebase(local, "Hello", "Oh Hello"))).isEqualTo("Oh Hello!")
        assertThat(TextDiff.rebase(local, "Hello", "Hello")).isEqualTo(local)
        val typedInside = TextDiff.changes("Hello", "Helxlo")
        assertThat(TextDiff.rebase(typedInside, "Hello", "Ho")).isEmpty()
        val atStart = TextDiff.changes("world", "Hi world")
        assertThat(TextDiff.apply("world!", TextDiff.rebase(atStart, "world", "world!"))).isEqualTo("Hi world!")
    }
}
