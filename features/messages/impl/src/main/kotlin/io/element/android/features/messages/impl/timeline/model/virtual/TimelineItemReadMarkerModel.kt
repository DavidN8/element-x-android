/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.model.virtual

/**
 * The unread band.
 *
 * @property unreadCount the number of messages from others newer than the read marker, filled in by
 * [io.element.android.features.messages.impl.timeline.factories.withReadStates].
 */
data class TimelineItemReadMarkerModel(
    val unreadCount: Int = 0,
) : TimelineItemVirtualModel {
    override val type: String = "TimelineItemReadMarkerModel"
}
