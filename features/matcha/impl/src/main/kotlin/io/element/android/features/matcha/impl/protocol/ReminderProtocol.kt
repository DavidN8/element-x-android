/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.protocol

import io.element.android.appconfig.MatchaConfig
import io.element.android.libraries.core.extensions.runCatchingExceptions
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** When a reminder should fire: after a delay, or at an exact instant. */
sealed interface ReminderTime {
    data class In(val amount: Long, val unit: ReminderUnit) : ReminderTime
    data class At(val epochMillis: Long) : ReminderTime
}

enum class ReminderUnit(val seconds: Long) {
    Seconds(1),
    Minutes(60),
    Hours(3_600),
    Days(86_400),
}

/** The bot's reply to a scheduling command. */
data class ReminderAcknowledgement(
    val reminderId: String,
    val dueAt: Long,
)

/**
 * Text commands understood by the Matcha reminder bot, identical to what the web client sends:
 * `!remind <seconds>s <message>` or `!remind at <UTC ISO instant with milliseconds> <message>`.
 */
object ReminderProtocol {
    private val maxDelayMillis = MatchaConfig.REMINDER_MAX_DELAY_DAYS * 86_400_000L
    private val isoFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'").withZone(ZoneOffset.UTC)
    private val acknowledgementRegex = Regex("^Scheduled (r-[a-f0-9]{16}) for (\\S+)\\.")

    /** The instant the reminder fires, given the time the command is sent. */
    fun dueAt(time: ReminderTime, nowMillis: Long): Long = when (time) {
        is ReminderTime.In -> nowMillis + time.amount * time.unit.seconds * 1000
        is ReminderTime.At -> time.epochMillis
    }

    fun isValidMessage(message: String): Boolean = message.trim().length in 1..MatchaConfig.REMINDER_MESSAGE_MAX_LENGTH

    /** Checks the same bounds as the bot: in the future, at most a year away, with a 1 to 2000 character message. */
    fun isValid(time: ReminderTime, message: String, nowMillis: Long): Boolean {
        if (time is ReminderTime.In && (time.amount <= 0 || time.amount > 999_999_999)) return false
        val dueAt = dueAt(time, nowMillis)
        return dueAt > nowMillis && dueAt - nowMillis <= maxDelayMillis && isValidMessage(message)
    }

    /** The command to send as a plain text message. Delays are sent in seconds, like the web client does. */
    fun command(time: ReminderTime, message: String): String {
        val whenPart = when (time) {
            is ReminderTime.In -> "${time.amount * time.unit.seconds}s"
            is ReminderTime.At -> "at ${formatInstant(time.epochMillis)}"
        }
        return "!remind $whenPart ${message.trim()}"
    }

    fun formatInstant(epochMillis: Long): String = isoFormatter.format(Instant.ofEpochMilli(epochMillis))

    /** Appends a link to the thread the reminder is about, once. */
    fun withLink(message: String, link: String?): String = when {
        link == null || message.contains(link) -> message
        message.isBlank() -> link
        else -> "$message\n$link"
    }

    /** Parses the bot's `Scheduled r-… for <instant>.` notice. */
    fun parseAcknowledgement(body: String?): ReminderAcknowledgement? {
        val match = acknowledgementRegex.find(body ?: return null) ?: return null
        val dueAt = runCatchingExceptions { Instant.parse(match.groupValues[2]).toEpochMilli() }.getOrNull() ?: return null
        return ReminderAcknowledgement(reminderId = match.groupValues[1], dueAt = dueAt)
    }
}
