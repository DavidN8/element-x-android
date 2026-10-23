/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.protocol

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReminderProtocolTest {
    private val now = 1_767_225_600_000L // 2026-01-01T00:00:00Z

    @Test
    fun `relative reminders are sent in seconds`() {
        assertThat(ReminderProtocol.command(ReminderTime.In(10, ReminderUnit.Minutes), " Check build ")).isEqualTo("!remind 600s Check build")
        assertThat(ReminderProtocol.command(ReminderTime.In(2, ReminderUnit.Days), "x")).isEqualTo("!remind 172800s x")
    }

    @Test
    fun `exact reminders are sent as a UTC instant with milliseconds`() {
        assertThat(ReminderProtocol.command(ReminderTime.At(now + 9 * 3_600_000L), "Review"))
            .isEqualTo("!remind at 2026-01-01T09:00:00.000Z Review")
    }

    @Test
    fun `bounds match the bot`() {
        assertThat(ReminderProtocol.isValid(ReminderTime.In(1, ReminderUnit.Seconds), "x", now)).isTrue()
        assertThat(ReminderProtocol.isValid(ReminderTime.In(365, ReminderUnit.Days), "x", now)).isTrue()
        assertThat(ReminderProtocol.isValid(ReminderTime.In(366, ReminderUnit.Days), "x", now)).isFalse()
        assertThat(ReminderProtocol.isValid(ReminderTime.In(0, ReminderUnit.Minutes), "x", now)).isFalse()
        assertThat(ReminderProtocol.isValid(ReminderTime.At(now), "x", now)).isFalse()
        assertThat(ReminderProtocol.isValid(ReminderTime.At(now - 1), "x", now)).isFalse()
        assertThat(ReminderProtocol.isValid(ReminderTime.In(1, ReminderUnit.Hours), "   ", now)).isFalse()
        assertThat(ReminderProtocol.isValid(ReminderTime.In(1, ReminderUnit.Hours), "m".repeat(2001), now)).isFalse()
    }

    @Test
    fun `a thread link is appended once`() {
        val link = "https://matrix.to/#/!r:x/\$t"
        assertThat(ReminderProtocol.withLink("Do it", link)).isEqualTo("Do it\n$link")
        assertThat(ReminderProtocol.withLink("Do it\n$link", link)).isEqualTo("Do it\n$link")
        assertThat(ReminderProtocol.withLink("", link)).isEqualTo(link)
        assertThat(ReminderProtocol.withLink("Do it", null)).isEqualTo("Do it")
    }

    @Test
    fun `the bot acknowledgement is parsed`() {
        val ack = ReminderProtocol.parseAcknowledgement("Scheduled r-0123456789abcdef for 2026-01-01T09:00:00.000Z. Cancel with !remind cancel r-0123456789abcdef.")
        assertThat(ack).isEqualTo(ReminderAcknowledgement("r-0123456789abcdef", now + 9 * 3_600_000L))
        assertThat(ReminderProtocol.parseAcknowledgement("Reminder for @a:b: hi")).isNull()
    }
}
