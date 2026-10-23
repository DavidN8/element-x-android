/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.chatbar

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.features.matcha.api.MatchaChatBarSelection
import io.element.android.libraries.matrix.api.core.ThreadId
import kotlinx.collections.immutable.toImmutableList

open class ChatBarStatePreviewParam : PreviewParameterProvider<ChatBarState> {
    override val values: Sequence<ChatBarState>
        get() = sequenceOf(
            // Loading: General and Tasks only.
            aChatBarState(threads = emptyList(), openTaskCount = 0),
            aChatBarState(),
            aChatBarState(selection = MatchaChatBarSelection.Thread(ThreadId("\$t2"))),
            // An archived thread opened from a link, without permission to create threads.
            aChatBarState(
                selection = MatchaChatBarSelection.Thread(ThreadId("\$old")),
                threads = previewThreads + ChatBarThread(ThreadId("\$old"), "Venue shortlist", isArchived = true),
                canCreateThread = false,
                canManageThreads = false,
            ),
            aChatBarState(
                selection = MatchaChatBarSelection.Tasks,
                threads = listOf(ChatBarThread(ThreadId("\$t1"), "Launch plan", isArchived = false), ChatBarThread(ThreadId("\$t5"), null, isArchived = false)),
                showUnreadableNamesWarning = true,
            ),
            aChatBarState(selection = MatchaChatBarSelection.AllThreads, canRemind = false, canOpenSpaceDocs = false),
            aChatBarState(sheet = ChatBarSheet.ThreadName(threadId = null, name = "", error = null, isSending = false)),
            aChatBarState(sheet = ChatBarSheet.ThreadName(threadId = null, name = "", error = ThreadNameError.Empty, isSending = false)),
            aChatBarState(sheet = ChatBarSheet.ThreadName(threadId = null, name = "A".repeat(104), error = ThreadNameError.TooLong, isSending = false)),
            aChatBarState(sheet = ChatBarSheet.ThreadName(threadId = null, name = "Budget review", error = ThreadNameError.CreateFailed, isSending = false)),
            aChatBarState(sheet = ChatBarSheet.ThreadName(threadId = ThreadId("\$t1"), name = "Launch plan v2", error = null, isSending = true)),
            aChatBarState(sheet = ChatBarSheet.ThreadActions(previewThreads.first())),
            aChatBarState(sheet = ChatBarSheet.ThreadActions(previewThreads.first()), canManageThreads = false, canCreateThread = false),
        )
}

private val previewThreads = listOf(
    ChatBarThread(ThreadId("\$t1"), "Launch plan", isArchived = false),
    ChatBarThread(ThreadId("\$t2"), "Design review for the onboarding flow and the new home screen", isArchived = false),
    ChatBarThread(ThreadId("\$t3"), "Bugs", isArchived = false),
    ChatBarThread(ThreadId("\$t4"), "Hiring", isArchived = false),
)

internal fun aChatBarState(
    selection: MatchaChatBarSelection = MatchaChatBarSelection.General,
    openTaskCount: Int = 3,
    threads: List<ChatBarThread> = previewThreads,
    canCreateThread: Boolean = true,
    canManageThreads: Boolean = true,
    canRemind: Boolean = true,
    canOpenSpaceDocs: Boolean = true,
    showUnreadableNamesWarning: Boolean = false,
    sheet: ChatBarSheet? = null,
    eventSink: (ChatBarEvent) -> Unit = {},
) = ChatBarState(
    isVisible = true,
    selection = selection,
    openTaskCount = openTaskCount,
    threads = threads.toImmutableList(),
    canCreateThread = canCreateThread,
    canManageThreads = canManageThreads,
    canRemind = canRemind,
    canOpenSpaceDocs = canOpenSpaceDocs,
    showUnreadableNamesWarning = showUnreadableNamesWarning,
    sheet = sheet,
    reminderTarget = null,
    eventSink = eventSink,
)
