/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.reminders

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import java.time.ZonedDateTime

/** The current time in the user's time zone. Reminder times are picked and checked against it; tests replace it. */
fun interface ReminderClock {
    fun now(): ZonedDateTime
}

@ContributesBinding(AppScope::class)
class DefaultReminderClock : ReminderClock {
    override fun now(): ZonedDateTime = ZonedDateTime.now()
}
