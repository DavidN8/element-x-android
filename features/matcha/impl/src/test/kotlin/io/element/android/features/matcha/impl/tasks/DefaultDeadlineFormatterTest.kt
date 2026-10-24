/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.tasks

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.dateformatter.test.FakeDateFormatter
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class DefaultDeadlineFormatterTest {
    @Test
    fun `a kept formatter follows a change of language`() {
        val formatter = DefaultDeadlineFormatter(RuntimeEnvironment.getApplication(), FakeDateFormatter(), aFixedClock)
        val inThreeDays = A_NOW + 3 * 24 * 3_600_000L
        val default = Locale.getDefault()
        try {
            Locale.setDefault(Locale.UK)
            val english = formatter.format(inThreeDays)
            Locale.setDefault(Locale.FRANCE)
            val french = formatter.format(inThreeDays)
            Locale.setDefault(Locale.UK)
            assertThat(french).isNotEqualTo(english)
            assertThat(formatter.format(inThreeDays)).isEqualTo(english)
        } finally {
            Locale.setDefault(default)
        }
    }
}
