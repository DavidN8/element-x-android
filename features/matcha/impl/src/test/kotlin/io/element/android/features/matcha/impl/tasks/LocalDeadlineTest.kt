/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.tasks

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class LocalDeadlineTest {
    private val london = ZoneId.of("Europe/London")

    @Test
    fun `a local time round-trips to its instant`() {
        val deadline = LocalDateTime.of(2026, 11, 3, 17, 0).toDeadline(london)
        assertThat(deadline).isEqualTo(LocalDeadline.Valid(1_793_725_200_000L))
    }

    @Test
    fun `a time skipped by the clocks going forward is refused`() {
        assertThat(LocalDateTime.of(2026, 3, 29, 1, 30).toDeadline(london)).isEqualTo(LocalDeadline.Skipped)
    }

    @Test
    fun `a repeated time when the clocks go back is the earlier one`() {
        val deadline = LocalDateTime.of(2026, 10, 25, 1, 30).toDeadline(london)
        assertThat(deadline).isEqualTo(LocalDeadline.Valid(1_792_888_200_000L))
    }

    @Test
    fun `times outside the protocol's range are refused`() {
        assertThat(LocalDateTime.of(10_000, 1, 1, 0, 0).toDeadline(london)).isEqualTo(LocalDeadline.OutOfRange)
        assertThat(LocalDateTime.of(1969, 12, 31, 0, 0).toDeadline(london)).isEqualTo(LocalDeadline.OutOfRange)
    }
}
