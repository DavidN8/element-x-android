/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.tasks

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.matcha.impl.R
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.aliasScreenTitle
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarHost
import io.element.android.libraries.designsystem.utils.snackbar.rememberSnackbarHostState
import io.element.android.libraries.ui.strings.CommonStrings

/** The chat's archived tasks; back returns to the board. */
@Composable
internal fun ArchivedTasksView(state: TaskBoardState, modifier: Modifier = Modifier) {
    val eventSink = state.eventSink
    val snackbarHostState = rememberSnackbarHostState(snackbarMessage = state.snackbarMessage)
    BackHandler { eventSink(TaskBoardEvent.HideArchived) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onClick = { eventSink(TaskBoardEvent.HideArchived) }) },
                title = {
                    Column(modifier = Modifier.semantics(mergeDescendants = true) { heading() }) {
                        Text(text = stringResource(R.string.screen_matcha_tasks_archived_title), style = ElementTheme.typography.aliasScreenTitle)
                        Text(
                            text = state.roomName ?: stringResource(CommonStrings.common_no_room_name),
                            style = ElementTheme.typography.fontBodyMdRegular,
                            color = ElementTheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            val board = state.content as? TaskBoardContent.Board
            when {
                board == null || board.isLoading -> TaskPlaceholders()
                board.archived.isEmpty() -> CenteredMessage(
                    title = stringResource(R.string.screen_matcha_tasks_archived_empty),
                    body = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 64.dp),
                )
                else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(board.archived, key = { it.id }) { task ->
                        TaskRow(
                            task = task,
                            onClick = { eventSink(TaskBoardEvent.OpenTask(task.id)) },
                            onLongClick = { eventSink(TaskBoardEvent.ShowActions(task.id)) },
                        )
                    }
                    loadOlderRow(board, eventSink)
                }
            }
        }
    }
}

@PreviewsDayNight
@Composable
internal fun ArchivedTasksViewPreview() = ElementPreview {
    ArchivedTasksView(state = aTaskBoardState(showArchived = true))
}

@PreviewsDayNight
@Composable
internal fun ArchivedTasksViewEmptyPreview() = ElementPreview {
    ArchivedTasksView(state = aTaskBoardState(content = aBoardContent(archived = emptyList()), showArchived = true))
}
