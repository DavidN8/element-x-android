/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs.editor

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class EditEchoesTest {
    @Test
    fun `echoes of earlier keystrokes never roll the field back while newer ones are pending`() {
        val echoes = EditEchoes("")
        listOf("a", "ab", "abc").forEach(echoes::sent)
        assertThat(echoes.isBehindField("a")).isTrue()
        assertThat(echoes.isBehindField("a")).isTrue()
        assertThat(echoes.isBehindField("ab")).isTrue()
        assertThat(echoes.isBehindField("abc")).isFalse()
    }

    @Test
    fun `a change made elsewhere is not an echo`() {
        val echoes = EditEchoes("x")
        assertThat(echoes.isBehindField("x by bob")).isFalse()
        echoes.sent("xy")
        assertThat(echoes.isBehindField("x by bob")).isFalse()
        echoes.reset("x by bob")
        assertThat(echoes.isBehindField("x by bob")).isFalse()
    }

    @Test
    fun `a text typed twice is matched in order`() {
        val echoes = EditEchoes("x")
        listOf("xa", "x", "xb").forEach(echoes::sent)
        assertThat(echoes.isBehindField("x")).isTrue()
        assertThat(echoes.isBehindField("xb")).isFalse()
    }
}
