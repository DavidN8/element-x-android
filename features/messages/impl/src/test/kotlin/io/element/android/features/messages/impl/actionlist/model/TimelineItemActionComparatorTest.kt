/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.actionlist.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TimelineItemActionComparatorTest {
    @Test
    fun `check that the list in the comparator only contain each item once`() {
        val sut = TimelineItemActionComparator()
        sut.orderedList.forEach {
            require(sut.orderedList.count { item -> item == it } == 1, { "Duplicate ${it::class.java}.$it" })
        }
    }

    @Test
    fun `check that the list in the comparator contains all the items`() {
        val sut = TimelineItemActionComparator()
        TimelineItemAction.entries.forEach {
            require(it in sut.orderedList, { "Missing ${it::class.simpleName}.$it in orderedList" })
        }
    }

    @Test
    fun `the destructive actions come last`() {
        val sut = TimelineItemActionComparator()
        val rendered = sut.orderedList - listOf(TimelineItemAction.RetrySending, TimelineItemAction.AbortSending)
        val firstDestructive = rendered.indexOfFirst { it.destructive }
        assertThat(rendered.drop(firstDestructive).all { it.destructive }).isTrue()
    }

    @Test
    fun `seen by comes after the edit actions and before view source`() {
        val sut = TimelineItemActionComparator()
        val sorted = listOf(
            TimelineItemAction.ViewSource,
            TimelineItemAction.ReadBy,
            TimelineItemAction.Edit,
            TimelineItemAction.Reply,
            TimelineItemAction.CopyText,
        ).sortedWith(sut)
        assertThat(sorted).containsExactly(
            TimelineItemAction.Reply,
            TimelineItemAction.CopyText,
            TimelineItemAction.Edit,
            TimelineItemAction.ReadBy,
            TimelineItemAction.ViewSource,
        ).inOrder()
    }

    @Test
    fun `remind me comes right after pin and unpin, before the edits`() {
        val sut = TimelineItemActionComparator()
        val sorted = listOf(
            TimelineItemAction.Edit,
            TimelineItemAction.RemindMe,
            TimelineItemAction.Unpin,
            TimelineItemAction.Pin,
        ).sortedWith(sut)
        assertThat(sorted).containsExactly(
            TimelineItemAction.Pin,
            TimelineItemAction.Unpin,
            TimelineItemAction.RemindMe,
            TimelineItemAction.Edit,
        ).inOrder()
    }
}
