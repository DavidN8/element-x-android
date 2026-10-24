/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.taskstab

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.matcha.impl.R
import io.element.android.features.matcha.impl.tasks.CenteredMessage
import io.element.android.features.matcha.impl.tasks.SharedWithChatIcon
import io.element.android.features.matcha.impl.tasks.icon
import io.element.android.features.matcha.impl.tasks.label
import io.element.android.features.matcha.impl.tasks.tint
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.rowSelected
import io.element.android.libraries.ui.strings.CommonStrings

/** From this width the columns share the table's width; below it the Task column stays put and the others scroll sideways. */
internal val WIDE_TABLE_MIN_WIDTH = 600.dp

/** The width of each column when the table scrolls sideways, and its share of the width when it doesn't. */
private val TaskTableColumn.width: Dp
    get() = when (this) {
        TaskTableColumn.Task -> 168.dp
        TaskTableColumn.Chat -> 136.dp
        TaskTableColumn.Status -> 128.dp
        TaskTableColumn.Due -> 128.dp
        TaskTableColumn.People -> 160.dp
    }

private val TaskTableColumn.weight: Float get() = width.value

/**
 * The tasks as a table, Task · Chat · Status · Due · People, under a header that sorts by the tapped column. A row opens the task
 * like a list row; its Status cell, like a long-press, opens the task's actions.
 */
@OptIn(ExperimentalFoundationApi::class)
internal fun LazyListScope.taskTable(
    content: TasksTabContent.Table,
    filter: TasksTabFilter,
    isWide: Boolean,
    scrollState: ScrollState,
    eventSink: (TasksTabEvent) -> Unit,
) {
    if (content.rows.isEmpty()) {
        item(key = "no-match") {
            CenteredMessage(
                title = stringResource(
                    if (filter == TasksTabFilter.Archived) R.string.screen_matcha_tasks_archived_empty else R.string.screen_matcha_tasks_board_empty_title
                ),
                body = null,
                modifier = Modifier
                    .fillParentMaxWidth()
                    .padding(vertical = 64.dp),
            )
        }
        return
    }
    stickyHeader(key = "table-header") { TableHeader(content.sort, isWide, scrollState, eventSink) }
    items(content.rows, key = { "row/${it.chat.roomId.value}/${it.task.id}" }) { task -> TableRow(task, isWide, scrollState, eventSink) }
}

/** Lays [cell]s out as columns: weighted when [isWide], otherwise the first one fixed and the rest scrolling with [scrollState]. */
@Suppress("ContentSlotReused") // Each call draws another column, so no call has state to carry over to another.
@Composable
private fun TableCells(
    isWide: Boolean,
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
    cell: @Composable (TaskTableColumn, Modifier) -> Unit,
) {
    val (first, others) = TaskTableColumn.entries.let { it.first() to it.drop(1) }
    Row(modifier = modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        cell(first, (if (isWide) Modifier.weight(first.weight) else Modifier.width(first.width)).padding(end = 12.dp))
        Row(
            modifier = if (isWide) Modifier.weight(others.sumOf { it.weight.toDouble() }.toFloat()) else Modifier.weight(1f).horizontalScroll(scrollState),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            others.forEach { cell(it, (if (isWide) Modifier.weight(it.weight) else Modifier.width(it.width)).padding(end = 12.dp)) }
        }
    }
}

@Composable
private fun TableHeader(sort: TaskTableSort, isWide: Boolean, scrollState: ScrollState, eventSink: (TasksTabEvent) -> Unit) {
    val ascending = stringResource(R.string.a11y_matcha_tasks_sorted_ascending)
    val descending = stringResource(R.string.a11y_matcha_tasks_sorted_descending)
    Column(modifier = Modifier.background(ElementTheme.colors.bgCanvasDefault)) {
        TableCells(
            isWide = isWide,
            scrollState = scrollState,
            modifier = Modifier.fillMaxWidth(),
        ) { column, modifier ->
            val isSorted = sort.column == column
            Row(
                modifier = modifier
                    .heightIn(min = 48.dp)
                    .clickable(role = Role.Button) { eventSink(TasksTabEvent.SortTable(column)) }
                    .semantics(mergeDescendants = true) {
                        if (isSorted) stateDescription = if (sort.isAscending) ascending else descending
                    },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = column.label(),
                    style = ElementTheme.typography.fontBodySmMedium,
                    color = if (isSorted) ElementTheme.colors.textPrimary else ElementTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isSorted) {
                    Icon(
                        imageVector = if (sort.isAscending) CompoundIcons.ArrowUp() else CompoundIcons.ArrowDown(),
                        contentDescription = null,
                        tint = ElementTheme.colors.iconPrimary,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        HorizontalDivider()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TableRow(task: TabTask, isWide: Boolean, scrollState: ScrollState, eventSink: (TasksTabEvent) -> Unit) {
    val roomId = task.chat.roomId
    val taskId = task.task.id
    val showActions = { eventSink(TasksTabEvent.ShowActions(roomId, taskId)) }
    TableCells(
        isWide = isWide,
        scrollState = scrollState,
        // One item for screen readers: the cells are read in order, and long-press offers the actions the Status cell opens.
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(if (task.isSelected) ElementTheme.colors.rowSelected else Color.Transparent)
            .combinedClickable(onClick = { eventSink(TasksTabEvent.OpenTask(roomId, taskId)) }, onLongClick = showActions)
            .semantics(mergeDescendants = true) { if (task.isSelected) selected = true },
    ) { column, modifier ->
        when (column) {
            TaskTableColumn.Task -> Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = task.task.title ?: stringResource(CommonStrings.common_matcha_thread_untitled),
                    style = ElementTheme.typography.fontBodyMdMedium,
                    color = if (task.task.title != null) ElementTheme.colors.textPrimary else ElementTheme.colors.textSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (task.task.isSharedWithChat) SharedWithChatIcon(size = 14.dp, modifier = Modifier.padding(start = 4.dp))
            }
            TaskTableColumn.Chat -> CellText(task.chat.name ?: stringResource(CommonStrings.common_no_room_name), modifier)
            // A tap here opens the actions; it adds nothing for screen readers, which reach them by long-pressing the row.
            TaskTableColumn.Status -> Row(
                modifier = modifier
                    .heightIn(min = 48.dp)
                    .pointerInput(showActions) { detectTapGestures { showActions() } },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(imageVector = task.task.status.icon(), contentDescription = null, tint = task.task.status.tint(), modifier = Modifier.size(16.dp))
                CellText(task.task.status.label(), Modifier.weight(1f, fill = false))
                Icon(
                    imageVector = CompoundIcons.ChevronDown(),
                    contentDescription = null,
                    tint = ElementTheme.colors.iconSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
            TaskTableColumn.Due -> {
                val deadline = task.task.deadline
                val description = deadline?.let { stringResource(R.string.a11y_matcha_task_due, it) }
                CellText(
                    text = deadline.orEmpty(),
                    modifier = modifier.semantics { if (description != null) contentDescription = description },
                    color = if (task.task.isOverdue) ElementTheme.colors.textCriticalPrimary else ElementTheme.colors.textSecondary,
                )
            }
            TaskTableColumn.People -> CellText(task.peopleCell(), modifier)
        }
    }
}

@Composable
private fun CellText(text: String, modifier: Modifier = Modifier, color: Color = ElementTheme.colors.textSecondary) {
    Text(
        text = text,
        style = ElementTheme.typography.fontBodySmRegular,
        color = color,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun TaskTableColumn.label(): String = stringResource(
    when (this) {
        TaskTableColumn.Task -> R.string.screen_matcha_tasks_table_task
        TaskTableColumn.Chat -> R.string.screen_matcha_tasks_table_chat
        TaskTableColumn.Status -> R.string.screen_matcha_tasks_status_label
        TaskTableColumn.Due -> R.string.screen_matcha_tasks_table_due
        TaskTableColumn.People -> R.string.screen_matcha_tasks_table_people
    }
)
