/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.chatbar

import io.element.android.features.matcha.api.MatchaChatBarSelection
import io.element.android.libraries.matrix.api.core.ThreadId

sealed interface ChatBarEvent {
    /** A chip was tapped; tapping the selected one does nothing. */
    data class Select(val target: MatchaChatBarSelection) : ChatBarEvent

    data object OpenSpaceDocs : ChatBarEvent

    /** Opens the reminder sheet about [threadId], or about the open thread when null. */
    data class RemindMe(val threadId: ThreadId?) : ChatBarEvent
    data object DismissReminder : ChatBarEvent
    data object NewThread : ChatBarEvent
    data class ShowThreadActions(val threadId: ThreadId) : ChatBarEvent
    data class RenameThread(val threadId: ThreadId) : ChatBarEvent
    data class UpdateThreadName(val name: String) : ChatBarEvent
    data object SubmitThreadName : ChatBarEvent
    data class SetArchived(val threadId: ThreadId, val archived: Boolean) : ChatBarEvent
    data class ViewTask(val threadId: ThreadId) : ChatBarEvent
    data object DismissSheet : ChatBarEvent
}
