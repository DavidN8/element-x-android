/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.test

import io.element.android.features.matcha.api.MatchaRoomController
import io.element.android.features.matcha.api.MatchaThreadInfo
import io.element.android.libraries.matrix.api.core.ThreadId
import io.element.android.tests.testutils.lambda.lambdaError
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.MutableStateFlow

class FakeMatchaRoomController(
    override val hasChatBar: MutableStateFlow<Boolean> = MutableStateFlow(false),
    override val openTaskCount: MutableStateFlow<Int> = MutableStateFlow(0),
    override val canCreateThread: MutableStateFlow<Boolean> = MutableStateFlow(true),
    override val threads: MutableStateFlow<ImmutableMap<ThreadId, MatchaThreadInfo>> = MutableStateFlow(persistentMapOf()),
    private val openNewThreadLambda: () -> Unit = { lambdaError() },
    private val openNewTaskLambda: () -> Unit = { lambdaError() },
) : MatchaRoomController {
    override fun openNewThread() = openNewThreadLambda()

    override fun openNewTask() = openNewTaskLambda()
}
