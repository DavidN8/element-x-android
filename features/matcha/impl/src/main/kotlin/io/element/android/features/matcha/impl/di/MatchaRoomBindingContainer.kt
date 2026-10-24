/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.di

import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.SingleIn
import io.element.android.features.matcha.impl.store.MatchaRoomStore
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.matrix.api.room.JoinedRoom

@BindingContainer
@ContributesTo(RoomScope::class)
object MatchaRoomBindingContainer {
    /** One store per open room, shared by the chip bar, the board and the thread screens. */
    @Provides
    @SingleIn(RoomScope::class)
    fun providesMatchaRoomStore(room: JoinedRoom, dispatchers: CoroutineDispatchers): MatchaRoomStore =
        MatchaRoomStore(room, room.roomCoroutineScope, computation = dispatchers.computation)
}
