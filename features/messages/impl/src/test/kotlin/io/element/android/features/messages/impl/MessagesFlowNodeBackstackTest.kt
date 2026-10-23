/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl

import com.bumble.appyx.navmodel.backstack.BackStack
import com.bumble.appyx.navmodel.backstack.operation.pop
import com.bumble.appyx.navmodel.backstack.operation.push
import com.google.common.truth.Truth.assertThat
import io.element.android.features.messages.impl.MessagesFlowNode.NavTarget
import io.element.android.libraries.matrix.test.A_THREAD_ID
import io.element.android.libraries.matrix.test.A_THREAD_ID_2
import org.junit.Test

class MessagesFlowNodeBackstackTest {
    private val messages = NavTarget.Messages(focusedEventId = null)

    @Test
    fun `a thread picked in All threads opened from a thread is the only screen above the chat`() {
        val backStack = BackStack<NavTarget>(initialElement = messages, savedStateMap = null)
        backStack.push(NavTarget.Thread(A_THREAD_ID, focusedEventId = null))
        backStack.push(NavTarget.ThreadsList)
        backStack.openThreadOverGeneral(A_THREAD_ID_2)
        assertThat(backStack.navTargets()).containsExactly(messages, NavTarget.Thread(A_THREAD_ID_2, focusedEventId = null)).inOrder()
        // Back goes to General.
        backStack.pop()
        assertThat(backStack.navTargets()).containsExactly(messages)
    }

    @Test
    fun `a thread picked in All threads opened from the board closes the board`() {
        val backStack = BackStack<NavTarget>(initialElement = messages, savedStateMap = null)
        backStack.push(NavTarget.Tasks(initialThreadId = null))
        backStack.push(NavTarget.ThreadsList)
        backStack.openThreadOverGeneral(A_THREAD_ID)
        assertThat(backStack.navTargets()).containsExactly(messages, NavTarget.Thread(A_THREAD_ID, focusedEventId = null)).inOrder()
    }

    @Test
    fun `a thread picked in All threads when the flow started elsewhere opens over a new chat root`() {
        val backStack = BackStack<NavTarget>(initialElement = NavTarget.PinnedMessagesList, savedStateMap = null)
        backStack.push(NavTarget.ThreadsList)
        backStack.openThreadOverGeneral(A_THREAD_ID)
        assertThat(backStack.navTargets()).containsExactly(messages, NavTarget.Thread(A_THREAD_ID, focusedEventId = null)).inOrder()
    }

    private fun BackStack<NavTarget>.navTargets() = elements.value
        .filter { it.targetState != BackStack.State.DESTROYED }
        .map { it.key.navTarget }
}
