/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.matcha

import io.element.android.features.matcha.api.MatchaChatBarActions
import io.element.android.libraries.matrix.api.core.ThreadId

/** Chip bar actions that go nowhere, for previews. */
object NoopMatchaChatBarActions : MatchaChatBarActions {
    override fun openGeneral() = Unit

    override fun openTasks() = Unit

    override fun openThread(threadId: ThreadId) = Unit

    override fun openAllThreads() = Unit

    override val openSpaceDocs: (() -> Unit)? = null
}
