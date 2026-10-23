/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.chatbar

import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.features.matcha.api.MatchaRoomController
import io.element.android.features.matcha.api.MatchaThreadInfo
import io.element.android.features.matcha.impl.protocol.ThreadNameSource
import io.element.android.features.matcha.impl.store.MatchaRoomSnapshot
import io.element.android.features.matcha.impl.store.MatchaRoomStore
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.di.annotations.RoomCoroutineScope
import io.element.android.libraries.matrix.api.core.ThreadId
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** A sheet the chat screens asked the chip bar renderer to show. */
enum class ChatBarRequest {
    NewThread,
    NewTask,
}

@SingleIn(RoomScope::class)
@ContributesBinding(RoomScope::class)
class DefaultMatchaRoomController(
    store: MatchaRoomStore,
    @RoomCoroutineScope coroutineScope: CoroutineScope,
) : MatchaRoomController {
    override val hasChatBar: StateFlow<Boolean> = store.snapshot
        .map { it.isLoaded && it.hasChatBar() }
        .distinctUntilChanged()
        .stateIn(coroutineScope, SharingStarted.Eagerly, false)

    override val threads: StateFlow<ImmutableMap<ThreadId, MatchaThreadInfo>> = store.snapshot
        .map { snapshot ->
            snapshot.threads.associate { thread ->
                thread.id to MatchaThreadInfo(
                    name = thread.metadata.name.takeIf { thread.metadata.nameSource != ThreadNameSource.EventId },
                    isArchived = thread.metadata.archived,
                    hiddenReplies = if (thread.hasHiddenFirstReply) 1 else 0,
                )
            }.toImmutableMap()
        }
        .distinctUntilChanged()
        .stateIn(coroutineScope, SharingStarted.Eagerly, persistentMapOf())

    private val _requests = MutableStateFlow<ChatBarRequest?>(null)

    /** The sheet asked for and not yet shown by the chip bar renderer. */
    val requests: StateFlow<ChatBarRequest?> = _requests.asStateFlow()

    override fun openNewThread() {
        _requests.value = ChatBarRequest.NewThread
    }

    override fun openNewTask() {
        _requests.value = ChatBarRequest.NewTask
    }

    /** The renderer showed [request]. */
    fun consume(request: ChatBarRequest) {
        _requests.compareAndSet(request, null)
    }
}

/** The chat uses the workspace features: it has a board, or an active thread someone named. */
internal fun MatchaRoomSnapshot.hasChatBar(): Boolean =
    board != null || threads.any { !it.metadata.archived && it.metadata.nameSource == ThreadNameSource.Explicit }
