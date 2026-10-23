/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.tasks

import io.element.android.libraries.designsystem.utils.snackbar.SnackbarDispatcher
import kotlin.time.Clock
import kotlin.time.Instant

/** Now, for every test that needs a clock: 2026-10-24T12:00:00Z. */
internal const val A_NOW = 1_792_843_200_000L

internal val aFixedClock = object : Clock {
    override fun now(): Instant = Instant.fromEpochMilliseconds(A_NOW)
}

internal val aDeadlineFormatter = DeadlineFormatter { "at $it" }

internal fun aTaskEditorPresenterFactory(snackbarDispatcher: SnackbarDispatcher = SnackbarDispatcher()) =
    TaskEditorPresenter.Factory { room, store, writeScope ->
        TaskEditorPresenter(
            room = room,
            store = store,
            writeScope = writeScope,
            deadlineFormatter = aDeadlineFormatter,
            snackbarDispatcher = snackbarDispatcher,
            clock = aFixedClock,
        )
    }
