/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs

import com.google.common.truth.Truth.assertThat
import io.element.android.features.networkmonitor.test.FakeNetworkMonitor
import io.element.android.libraries.matrix.test.A_ROOM_ID
import io.element.android.libraries.matrix.test.A_ROOM_ID_2
import io.element.android.libraries.matrix.test.FakeMatrixClient
import io.element.android.libraries.matrix.test.room.aRoomInfo
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SpaceDocsContextFactoryTest {
    @Test
    fun `a space's docs open only in a space, while a chat's earlier doc opens in the chat`() = runTest {
        val space = DocsRoomFixture(emptyList())
        val chat = DocsRoomFixture(emptyList(), roomInfo = aRoomInfo(isEncrypted = true, isSpace = false), roomId = A_ROOM_ID_2)
        val client = FakeMatrixClient().apply {
            givenGetRoomResult(A_ROOM_ID, space.room)
            givenGetRoomResult(A_ROOM_ID_2, chat.room)
        }
        val factory = SpaceDocsContextFactory(client, FakeYjsEngine(), FakeNetworkMonitor(), testCoroutineDispatchers())
        assertThat(factory.create(A_ROOM_ID, isEarlierChatDocument = false, parentScope = backgroundScope)?.isEarlierChatDocument).isFalse()
        assertThat(factory.create(A_ROOM_ID_2, isEarlierChatDocument = false, parentScope = backgroundScope)).isNull()
        chat.baseRoom.assertDestroyed()
        assertThat(factory.create(A_ROOM_ID_2, isEarlierChatDocument = true, parentScope = backgroundScope)?.isEarlierChatDocument).isTrue()
    }
}
