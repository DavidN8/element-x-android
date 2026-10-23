/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.matcha.api.MatchaChatBarSelection
import io.element.android.features.matcha.impl.R
import io.element.android.features.matcha.impl.chatbar.ChatBarView
import io.element.android.features.matcha.impl.chatbar.WarningStrip
import io.element.android.features.matcha.impl.chatbar.aChatBarState
import io.element.android.features.matcha.impl.protocol.TaskStatus
import io.element.android.libraries.designsystem.components.BigIcon
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.aliasChatTitle
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.FloatingActionButton
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.designsystem.utils.hasTwoPaneWindowSize
import io.element.android.libraries.designsystem.utils.snackbar.SnackbarHost
import io.element.android.libraries.designsystem.utils.snackbar.rememberSnackbarHostState
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch

/** Boards at least this wide show the three statuses side by side. */
private val WIDE_BOARD_MIN_WIDTH = 600.dp
private val COLUMN_MIN_WIDTH = 240.dp

/** Keeps the last row clear of the New task button. */
private val FAB_CLEARANCE = 88.dp

/** ponytail: the 40dp chat top-bar avatar; switch to AvatarSize.ChatTopBar once the design system has it. */
private val TOP_BAR_AVATAR_SIZE = 40.dp

/**
 * The chat's tasks: the board under the chat's top bar and [chatBar], or its archived tasks, and the sheets on top of both.
 */
@Composable
fun TasksView(
    state: TaskBoardState,
    onBack: () -> Unit,
    onOpenRoomDetails: () -> Unit,
    modifier: Modifier = Modifier,
    chatBar: @Composable () -> Unit,
) {
    if (state.showArchived) {
        ArchivedTasksView(state = state, modifier = modifier)
    } else {
        TaskBoardView(state = state, onBack = onBack, onOpenRoomDetails = onOpenRoomDetails, modifier = modifier, chatBar = chatBar)
    }
    val board = state.content as? TaskBoardContent.Board
    val actionsTask = state.actionsSheet
    if (actionsTask != null && board != null) {
        TaskActionsSheet(task = actionsTask, board = board, eventSink = state.eventSink)
    }
    state.editor?.let { TaskEditorSheet(it) }
}

/** The board: tabs on phones, three columns when the board is wide enough. */
@Composable
private fun TaskBoardView(
    state: TaskBoardState,
    onBack: () -> Unit,
    onOpenRoomDetails: () -> Unit,
    modifier: Modifier = Modifier,
    chatBar: @Composable () -> Unit,
) {
    val snackbarHostState = rememberSnackbarHostState(snackbarMessage = state.snackbarMessage)
    val pagerState = rememberPagerState { TaskStatus.entries.size }
    val content = state.content
    val eventSink = state.eventSink
    BoxWithConstraints(modifier = modifier) {
        val isWide = maxWidth >= WIDE_BOARD_MIN_WIDTH
        val showFab = content is TaskBoardContent.Board && content.canAdd
        Scaffold(
            topBar = {
                Column {
                    TaskBoardTopBar(roomName = state.roomName, roomAvatar = state.roomAvatar, onBack = onBack, onOpenRoomDetails = onOpenRoomDetails)
                    chatBar()
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            floatingActionButton = {
                if (showFab) {
                    FloatingActionButton(
                        onClick = { eventSink(TaskBoardEvent.NewTask(if (isWide) TaskStatus.Todo else TaskStatus.entries[pagerState.currentPage])) },
                    ) {
                        Icon(imageVector = CompoundIcons.Plus(), contentDescription = stringResource(R.string.screen_matcha_tasks_new))
                    }
                }
            },
        ) { padding ->
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                when (content) {
                    TaskBoardContent.LookingForBoard -> Column {
                        TaskPlaceholders()
                        Text(
                            text = stringResource(R.string.screen_matcha_tasks_looking),
                            style = ElementTheme.typography.fontBodyMdRegular,
                            color = ElementTheme.colors.textSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                        )
                    }
                    TaskBoardContent.LoadFailed -> BoardMessage(
                        icon = BigIcon.Style.Default(CompoundIcons.Warning()),
                        title = stringResource(R.string.common_matcha_write_failed),
                        body = null,
                        action = stringResource(CommonStrings.action_retry),
                        onAction = { eventSink(TaskBoardEvent.RetryLoad) },
                    )
                    is TaskBoardContent.Setup -> BoardMessage(
                        icon = BigIcon.Style.Default(CompoundIcons.CheckCircle()),
                        title = stringResource(R.string.screen_matcha_tasks_setup_title),
                        body = stringResource(
                            if (content.canCreate) R.string.screen_matcha_tasks_setup_body else R.string.screen_matcha_tasks_setup_no_permission
                        ),
                        action = stringResource(R.string.screen_matcha_tasks_setup_action).takeIf { content.canCreate },
                        onAction = { eventSink(TaskBoardEvent.CreateBoard) },
                        isBusy = content.isCreating,
                        error = stringResource(R.string.screen_matcha_tasks_setup_failed).takeIf { content.createFailed },
                    )
                    is TaskBoardContent.Board -> {
                        val bottomPadding = if (showFab) FAB_CLEARANCE else 0.dp
                        if (isWide) {
                            WideBoard(content, bottomPadding, eventSink)
                        } else {
                            PhoneBoard(content, pagerState, bottomPadding, eventSink)
                        }
                    }
                }
            }
        }
    }
}

/** The chat's top bar: avatar, name, and "Tasks" as the subtitle; the name block opens the room's details. */
@Composable
private fun TaskBoardTopBar(roomName: String?, roomAvatar: AvatarData, onBack: () -> Unit, onOpenRoomDetails: () -> Unit) {
    TopAppBar(
        navigationIcon = {
            // In the two-pane layout the chat list is already visible next to the chat, so there is nothing to go back to.
            if (!hasTwoPaneWindowSize()) BackButton(onClick = onBack)
        },
        title = {
            Row(
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onOpenRoomDetails)
                    .semantics(mergeDescendants = true) { heading() },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(avatarData = roomAvatar, avatarType = AvatarType.Room(), forcedAvatarSize = TOP_BAR_AVATAR_SIZE)
                Column {
                    Text(
                        text = roomName ?: stringResource(CommonStrings.common_no_room_name),
                        style = ElementTheme.typography.aliasChatTitle,
                        color = ElementTheme.colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.common_matcha_tasks),
                        style = ElementTheme.typography.fontBodyMdRegular,
                        color = ElementTheme.colors.textSecondary,
                        maxLines = 1,
                    )
                }
            }
        },
    )
}

@Composable
private fun PhoneBoard(
    board: TaskBoardContent.Board,
    pagerState: PagerState,
    bottomPadding: Dp,
    eventSink: (TaskBoardEvent) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxSize()) {
        // Scrollable, so the tabs survive large text.
        PrimaryScrollableTabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = ElementTheme.colors.bgCanvasDefault,
            edgePadding = 16.dp,
            indicator = {
                TabRowDefaults.PrimaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(pagerState.currentPage, matchContentSize = true),
                    width = Dp.Unspecified,
                    color = ElementTheme.colors.bgAccentRest,
                )
            },
            divider = {},
        ) {
            TaskStatus.entries.forEachIndexed { index, status ->
                val selected = pagerState.currentPage == index
                Tab(
                    selected = selected,
                    onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                    modifier = Modifier.heightIn(min = 48.dp),
                    text = {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = status.label(),
                                style = ElementTheme.typography.fontBodyMdMedium,
                                color = if (selected) ElementTheme.colors.textActionAccent else ElementTheme.colors.textSecondary,
                                maxLines = 1,
                            )
                            Text(
                                text = board.tasks(status).size.toString(),
                                style = ElementTheme.typography.fontBodyMdRegular,
                                color = ElementTheme.colors.textSecondary,
                                maxLines = 1,
                            )
                        }
                    },
                )
            }
        }
        BoardNotices(board)
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
            val tasks = board.tasks(TaskStatus.entries[page])
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomPadding),
            ) {
                archivedRow(board, eventSink)
                when {
                    board.isLoading -> item(key = "loading") { TaskPlaceholders() }
                    tasks.isEmpty() -> item(key = "empty") { EmptyBoard(canAdd = board.canAdd, modifier = Modifier.fillParentMaxWidth()) }
                    else -> items(tasks, key = { it.id }) { task ->
                        TaskRow(
                            task = task,
                            onClick = { eventSink(TaskBoardEvent.OpenTask(task.id)) },
                            onLongClick = { eventSink(TaskBoardEvent.ShowActions(task.id)) },
                        )
                    }
                }
                loadOlderRow(board, eventSink)
            }
        }
    }
}

@Composable
private fun WideBoard(
    board: TaskBoardContent.Board,
    bottomPadding: Dp,
    eventSink: (TaskBoardEvent) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        BoardNotices(board)
        if (board.archived.isNotEmpty()) {
            ArchivedTasksRow(count = board.archived.size, onClick = { eventSink(TaskBoardEvent.ShowArchived) })
        }
        when {
            board.isLoading -> TaskPlaceholders()
            board.todo.isEmpty() && board.doing.isEmpty() && board.done.isEmpty() -> EmptyBoard(
                canAdd = board.canAdd,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
            else -> BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                val columnWidth = maxOf(COLUMN_MIN_WIDTH, (maxWidth - 16.dp * 2 - 8.dp * 2) / TaskStatus.entries.size)
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .horizontalScroll(rememberScrollState())
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TaskStatus.entries.forEach { status ->
                        BoardColumn(
                            status = status,
                            tasks = board.tasks(status),
                            bottomPadding = bottomPadding,
                            eventSink = eventSink,
                            modifier = Modifier.width(columnWidth),
                        )
                    }
                }
            }
        }
        if (board.hasMore) LoadOlderTasksRow(isLoading = board.isLoadingMore, onClick = { eventSink(TaskBoardEvent.LoadMore) })
    }
}

@Composable
private fun BoardColumn(
    status: TaskStatus,
    tasks: ImmutableList<TaskItem>,
    bottomPadding: Dp,
    eventSink: (TaskBoardEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(16.dp))
            .background(ElementTheme.colors.bgSubtleSecondary)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .padding(horizontal = 12.dp)
                .semantics(mergeDescendants = true) { heading() },
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = status.label(),
                style = ElementTheme.typography.fontBodyMdMedium,
                color = ElementTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(text = tasks.size.toString(), style = ElementTheme.typography.fontBodyMdMedium, color = ElementTheme.colors.textSecondary)
        }
        LazyColumn(
            contentPadding = PaddingValues(start = 8.dp, end = 8.dp, bottom = 8.dp + bottomPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(tasks, key = { it.id }) { task ->
                TaskCard(
                    task = task,
                    onClick = { eventSink(TaskBoardEvent.OpenTask(task.id)) },
                    onLongClick = { eventSink(TaskBoardEvent.ShowActions(task.id)) },
                )
            }
        }
    }
}

private fun LazyListScope.archivedRow(board: TaskBoardContent.Board, eventSink: (TaskBoardEvent) -> Unit) {
    if (board.archived.isNotEmpty()) {
        item(key = "archived") { ArchivedTasksRow(count = board.archived.size, onClick = { eventSink(TaskBoardEvent.ShowArchived) }) }
    }
}

internal fun LazyListScope.loadOlderRow(board: TaskBoardContent.Board, eventSink: (TaskBoardEvent) -> Unit) {
    if (board.hasMore) {
        item(key = "load-older") { LoadOlderTasksRow(isLoading = board.isLoadingMore, onClick = { eventSink(TaskBoardEvent.LoadMore) }) }
    }
}

/** The strips under the tabs: task updates that can't be decrypted, and a read-only board. */
@Composable
private fun BoardNotices(board: TaskBoardContent.Board) {
    if (board.isUnreadable) WarningStrip(text = stringResource(R.string.screen_matcha_tasks_unreadable), maxLines = 3)
    if (!board.canEdit) WarningStrip(text = stringResource(R.string.screen_matcha_tasks_read_only), maxLines = 3)
}

@Composable
internal fun TaskPlaceholders(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        repeat(3) { TaskPlaceholderRow() }
    }
}

@Composable
private fun EmptyBoard(canAdd: Boolean, modifier: Modifier = Modifier) {
    CenteredMessage(
        title = stringResource(R.string.screen_matcha_tasks_board_empty_title),
        body = if (canAdd) stringResource(R.string.screen_matcha_tasks_board_empty_body) else null,
        modifier = modifier.padding(vertical = 64.dp),
    )
}

/** A centred state: a big icon, a title and a body, an optional error, and an optional green pill. */
@Composable
private fun BoardMessage(
    icon: BigIcon.Style,
    title: String,
    body: String?,
    action: String?,
    onAction: () -> Unit,
    isBusy: Boolean = false,
    error: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BigIcon(style = icon)
        Spacer(Modifier.height(16.dp))
        CenteredMessage(title = title, body = body)
        if (action != null) {
            Spacer(Modifier.height(24.dp))
            if (error != null) {
                Text(
                    text = error,
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textCriticalPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Button(
                text = action,
                onClick = { if (!isBusy) onAction() },
                showProgress = isBusy,
            )
        }
    }
}

@Composable
internal fun CenteredMessage(title: String, body: String?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = title,
            style = ElementTheme.typography.fontBodyLgMedium,
            color = ElementTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        if (body != null) {
            Text(
                text = body,
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@PreviewsDayNight
@Composable
internal fun TasksViewPreview(@PreviewParameter(TaskBoardStatePreviewParam::class) state: TaskBoardState) = ElementPreview {
    TasksView(state = state, onBack = {}, onOpenRoomDetails = {}) {
        ChatBarView(aChatBarState(selection = MatchaChatBarSelection.Tasks))
    }
}

@PreviewsDayNight
@Composable
internal fun TasksViewWidePreview() = ElementPreview {
    Box(modifier = Modifier.width(720.dp)) {
        TasksView(state = aTaskBoardState(), onBack = {}, onOpenRoomDetails = {}) {
            ChatBarView(aChatBarState(selection = MatchaChatBarSelection.Tasks))
        }
    }
}
