/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.tasks

import android.content.Context
import android.text.format.DateFormat
import android.text.format.DateUtils
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.matcha.impl.R
import io.element.android.libraries.dateformatter.api.DateFormatter
import io.element.android.libraries.dateformatter.api.DateFormatterMode
import io.element.android.libraries.di.annotations.ApplicationContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.time.Clock

/** Formats a task deadline or a reminder time for display. */
fun interface DeadlineFormatter {
    fun format(epochMillis: Long): String
}

/**
 * Future times read "Today 14:05", "Tomorrow 09:00", "Tue 17:00" within six days, else "3 Nov, 17:00" with the year when it
 * differs; past times keep the chat list's format.
 *
 * ponytail: stands in for DateFormatterMode.Upcoming (WHATSAPP_DESIGN.md §5) until libraries/dateformatter has it.
 */
@ContributesBinding(AppScope::class)
class DefaultDeadlineFormatter(
    @ApplicationContext private val context: Context,
    private val dateFormatter: DateFormatter,
    private val clock: Clock,
) : DeadlineFormatter {
    override fun format(epochMillis: Long): String {
        val nowMillis = clock.now().toEpochMilliseconds()
        if (epochMillis < nowMillis) return dateFormatter.format(epochMillis, DateFormatterMode.TimeOrDate, useRelative = true)
        val zone = ZoneId.systemDefault()
        val locale = Locale.getDefault()
        val date = Instant.ofEpochMilli(epochMillis).atZone(zone)
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone)
        val hours = if (DateFormat.is24HourFormat(context)) "HHmm" else "hmma"
        fun pattern(skeleton: String) = DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale).format(date)
        return when (ChronoUnit.DAYS.between(today.toLocalDate(), date.toLocalDate())) {
            0L, 1L -> context.getString(
                R.string.common_matcha_day_and_time,
                DateUtils.getRelativeTimeSpanString(epochMillis, nowMillis, DateUtils.DAY_IN_MILLIS).toString(),
                pattern(hours),
            )
            in 2L..6L -> pattern("EEE$hours")
            else -> pattern(if (date.year == today.year) "dMMM$hours" else "dMMMyyyy$hours")
        }
    }
}
