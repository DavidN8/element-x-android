/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.tasks

import io.element.android.features.matcha.impl.protocol.KanbanProtocol
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** The outcome of turning a picked local date and time into a deadline. */
sealed interface LocalDeadline {
    data class Valid(val epochMillis: Long) : LocalDeadline

    /** The time doesn't exist in [ZoneId], because the clocks skip it. */
    data object Skipped : LocalDeadline

    /** The time is outside what the protocol accepts (before 1970, or after the year 9999). */
    data object OutOfRange : LocalDeadline
}

/** A deadline must round-trip: a local time skipped by a clock change is refused rather than silently moved. */
fun LocalDateTime.toDeadline(zone: ZoneId): LocalDeadline {
    val zoned = ZonedDateTime.ofLocal(this, zone, null)
    if (zoned.toLocalDateTime() != this) return LocalDeadline.Skipped
    val millis = zoned.toInstant().toEpochMilli()
    return if (KanbanProtocol.isValidTimestamp(millis)) LocalDeadline.Valid(millis) else LocalDeadline.OutOfRange
}
