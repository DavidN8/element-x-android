/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.actionlist.model

import androidx.annotation.VisibleForTesting

class TimelineItemActionComparator : Comparator<TimelineItemAction> {
    // WhatsApp's order (WHATSAPP_DESIGN.md §3.3 "Long-press"): replies first, then sharing and copying, then changes to the message,
    // with the destructive actions last.
    @VisibleForTesting
    val orderedList = listOf(
        TimelineItemAction.EndPoll,
        TimelineItemAction.ViewInTimeline,
        TimelineItemAction.Reply,
        TimelineItemAction.ReplyInThread,
        TimelineItemAction.Forward,
        TimelineItemAction.CopyText,
        TimelineItemAction.CopyCaption,
        TimelineItemAction.CopyLink,
        TimelineItemAction.Pin,
        TimelineItemAction.Unpin,
        TimelineItemAction.Edit,
        TimelineItemAction.EditPoll,
        TimelineItemAction.AddCaption,
        TimelineItemAction.EditCaption,
        TimelineItemAction.ReadBy,
        TimelineItemAction.ViewSource,
        TimelineItemAction.RemoveCaption,
        TimelineItemAction.ReportContent,
        TimelineItemAction.Redact,
        // Not rendered in the action list, but have to be listed here for exhaustiveness.
        TimelineItemAction.RetrySending,
        TimelineItemAction.AbortSending,
    )

    override fun compare(o1: TimelineItemAction, o2: TimelineItemAction): Int {
        val index1 = orderedList.indexOf(o1)
        val index2 = orderedList.indexOf(o2)
        return index1.compareTo(index2)
    }
}
