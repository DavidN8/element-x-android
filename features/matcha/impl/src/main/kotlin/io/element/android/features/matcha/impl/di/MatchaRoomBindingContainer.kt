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
import io.element.android.features.matcha.impl.taskstab.TasksTabRooms
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.matrix.api.room.JoinedRoom
import kotlinx.coroutines.job

@BindingContainer
@ContributesTo(RoomScope::class)
object MatchaRoomBindingContainer {
    /**
     * One store per open room, shared by the chip bar, the board and the thread screens. A chat the Tasks tab has open reads through
     * the tab's store instead of reading the room a second time, and holds it until the room's screens are gone.
     *
     * ponytail: only a board the tab already has open is shared; a chat opened before the tab finds its board, or the tab finding
     * the board of a chat already open, still reads the room twice. Handing the chat's store to the tab would lift that.
     */
    @Provides
    @SingleIn(RoomScope::class)
    internal fun providesMatchaRoomStore(room: JoinedRoom, tasksTabRooms: TasksTabRooms, dispatchers: CoroutineDispatchers): MatchaRoomStore {
        val held = tasksTabRooms.acquire(room.roomId)
            ?: return MatchaRoomStore(room, room.roomCoroutineScope, computation = dispatchers.computation)
        // The room's scope ends when its screens are gone, as a store of its own would.
        room.roomCoroutineScope.coroutineContext.job.invokeOnCompletion { tasksTabRooms.release(held) }
        return held.store
    }
}
