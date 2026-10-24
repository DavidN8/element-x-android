/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.matcha

import androidx.compose.runtime.compositionLocalOf
import io.element.android.features.matcha.api.MatchaThreadInfo
import io.element.android.libraries.matrix.api.core.ThreadId
import kotlinx.collections.immutable.ImmutableMap

/**
 * The Matcha names of the chat's threads, read by the thread footers deep in the timeline; null while the Matcha workspaces are off.
 */
val LocalMatchaThreads = compositionLocalOf<ImmutableMap<ThreadId, MatchaThreadInfo>?> { null }
