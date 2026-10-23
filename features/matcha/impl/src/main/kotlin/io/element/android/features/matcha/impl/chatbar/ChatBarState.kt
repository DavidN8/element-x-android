/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.chatbar

import androidx.compose.runtime.Immutable
import io.element.android.features.matcha.api.MatchaChatBarSelection
import io.element.android.features.matcha.api.MatchaReminderTarget
import io.element.android.libraries.matrix.api.core.ThreadId
import kotlinx.collections.immutable.ImmutableList

data class ChatBarState(
    /** False when the Matcha workspaces feature is off: nothing is drawn. */
    val isVisible: Boolean,
    val selection: MatchaChatBarSelection,
    /** To do and In progress tasks on the chat's board; 0 hides the count. */
    val openTaskCount: Int,
    /** Up to four active threads, newest root first, then the open thread when it is not one of them. Empty until loaded. */
    val threads: ImmutableList<ChatBarThread>,
    val canCreateThread: Boolean,
    val canManageThreads: Boolean,
    val canRemind: Boolean,
    val canOpenSpaceDocs: Boolean,
    val showUnreadableNamesWarning: Boolean,
    val sheet: ChatBarSheet?,
    /** The reminder sheet is shown while this is set. */
    val reminderTarget: MatchaReminderTarget?,
    val eventSink: (ChatBarEvent) -> Unit,
) {
    val showMore: Boolean get() = canRemind || canOpenSpaceDocs
}

@Immutable
data class ChatBarThread(
    val id: ThreadId,
    /** The shared name, or null when there is none to show; drawn as "Thread". */
    val name: String?,
    val isArchived: Boolean,
)

@Immutable
sealed interface ChatBarSheet {
    /** The New thread sheet, or the Rename thread sheet when [threadId] is set. */
    data class ThreadName(
        val threadId: ThreadId?,
        val name: String,
        val error: ThreadNameError?,
        val isSending: Boolean,
    ) : ChatBarSheet

    /** The long-press actions of a thread chip. */
    data class ThreadActions(val thread: ChatBarThread) : ChatBarSheet
}

enum class ThreadNameError {
    Empty,
    TooLong,
    CreateFailed,
    RenameFailed,
}
