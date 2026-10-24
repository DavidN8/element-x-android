/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.taskstab

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.features.matcha.impl.R
import io.element.android.features.matcha.impl.docs.list.SpaceScreenTitle
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarHost
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarMessage
import io.element.android.libraries.designsystem.utils.snackbar.rememberSnackbarHostState

/**
 * All boards of a space: the Tasks tab's list under a top bar with the space's name, as the space's notes have. It shows the
 * snackbars itself, such as Archive's Undo: on a phone, Home isn't drawn under it.
 */
@Composable
fun SpaceBoardsView(
    state: TasksTabState,
    snackbarMessage: SnackbarMessage?,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onClick = onBackClick) },
                title = {
                    SpaceScreenTitle(title = stringResource(R.string.screen_matcha_space_boards_title), spaceName = state.spaceName?.ifEmpty { null })
                },
            )
        },
        snackbarHost = { SnackbarHost(rememberSnackbarHostState(snackbarMessage)) },
    ) { padding ->
        TasksTabView(state = state, contentPadding = padding)
    }
}

/** The Tasks tab's states of a space. */
class SpaceBoardsTasksTabStatePreviewParam : PreviewParameterProvider<TasksTabState> {
    override val values: Sequence<TasksTabState>
        get() = TasksTabStatePreviewParam().values.filter { it.spaceName != null }
}

@PreviewsDayNight
@Composable
internal fun SpaceBoardsViewPreview(@PreviewParameter(SpaceBoardsTasksTabStatePreviewParam::class) state: TasksTabState) = ElementPreview {
    SpaceBoardsView(state = state, snackbarMessage = null, onBackClick = {})
}
