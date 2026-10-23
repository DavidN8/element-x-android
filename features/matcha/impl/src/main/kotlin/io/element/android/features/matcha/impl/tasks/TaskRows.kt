/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.tasks

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.matcha.impl.R
import io.element.android.features.matcha.impl.protocol.TaskStatus
import io.element.android.libraries.designsystem.atomic.atoms.PlaceholderAtom
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.placeholderBackground
import io.element.android.libraries.ui.strings.CommonPlurals
import io.element.android.libraries.designsystem.R as DesignSystemR

/** A task as a chat-style row: status tile, name and deadline, then the description or the replies. Shared with the Tasks tab. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TaskRow(
    task: TaskItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TaskStatusTile(task.status)
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = task.title ?: stringResource(R.string.common_matcha_thread_untitled),
                    style = ElementTheme.typography.fontBodyLgMedium,
                    // An unnamed thread reads as untidy rather than broken.
                    color = if (task.title != null) ElementTheme.colors.textPrimary else ElementTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (task.deadline != null) {
                    Spacer(Modifier.width(8.dp))
                    TaskDeadline(task.deadline, task.isOverdue)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = task.secondLine(),
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (task.hasReminder) {
                    Spacer(Modifier.width(8.dp))
                    ReminderIcon(size = 16.dp)
                }
            }
        }
    }
}

/** A task as a card in a board column (wide layout). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun TaskCard(
    task: TaskItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = stringResource(R.string.a11y_matcha_task_status, task.status.label())
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(ElementTheme.colors.bgCanvasDefault)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .heightIn(min = 56.dp)
            .padding(12.dp)
            .semantics(mergeDescendants = true) { stateDescription = status },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = task.title ?: stringResource(R.string.common_matcha_thread_untitled),
            style = ElementTheme.typography.fontBodyLgMedium,
            color = if (task.title != null) ElementTheme.colors.textPrimary else ElementTheme.colors.textSecondary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (task.deadline != null || task.hasReminder || task.replyCount != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (task.deadline != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = CompoundIcons.Calendar(),
                            contentDescription = null,
                            tint = if (task.isOverdue) ElementTheme.colors.iconCriticalPrimary else ElementTheme.colors.iconSecondary,
                            modifier = Modifier.size(14.dp),
                        )
                        TaskDeadline(task.deadline, task.isOverdue)
                    }
                }
                if (task.hasReminder) ReminderIcon(size = 14.dp)
                if (task.replyCount != null) {
                    val replies = pluralStringResource(CommonPlurals.common_replies, task.replyCount, task.replyCount)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = CompoundIcons.Threads(),
                            contentDescription = null,
                            tint = ElementTheme.colors.iconSecondary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = task.replyCount.toString(),
                            style = ElementTheme.typography.fontBodySmRegular,
                            color = ElementTheme.colors.textSecondary,
                            modifier = Modifier.semantics { contentDescription = replies },
                        )
                    }
                }
            }
        }
    }
}

/** The "Archived" row at the top of the board, opening the archived tasks. */
@Composable
internal fun ArchivedTasksRow(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon = ImageVector.vectorResource(DesignSystemR.drawable.ic_matcha_archive), tint = ElementTheme.colors.iconSecondary)
        Text(
            text = stringResource(R.string.common_matcha_archived),
            style = ElementTheme.typography.fontBodyLgMedium,
            color = ElementTheme.colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            style = ElementTheme.typography.fontBodySmMedium,
            color = ElementTheme.colors.textActionAccent,
        )
    }
}

/** A full-width green text row, e.g. "Load older tasks", with a progress indicator while loading. */
@Composable
internal fun LoadOlderTasksRow(isLoading: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = !isLoading, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
        } else {
            Text(
                text = stringResource(R.string.screen_matcha_tasks_load_older),
                style = ElementTheme.typography.fontBodyMdMedium,
                color = ElementTheme.colors.textActionAccent,
            )
        }
    }
}

/** A skeleton row with the metrics of a task row. */
@Composable
internal fun TaskPlaceholderRow(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(AvatarSize.RoomListItem.dp)
                .background(color = ElementTheme.colors.placeholderBackground, shape = CircleShape)
        )
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PlaceholderAtom(width = 120.dp, height = 7.dp)
            PlaceholderAtom(width = 180.dp, height = 6.dp)
        }
    }
}

/** The 52dp circle carrying a task's status icon. */
@Composable
internal fun TaskStatusTile(status: TaskStatus, modifier: Modifier = Modifier) {
    IconTile(
        icon = status.icon(),
        tint = if (status == TaskStatus.Todo) ElementTheme.colors.iconSecondary else ElementTheme.colors.iconAccentPrimary,
        contentDescription = stringResource(R.string.a11y_matcha_task_status, status.label()),
        modifier = modifier,
    )
}

@Composable
private fun IconTile(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, contentDescription: String? = null) {
    Box(
        modifier = modifier
            .size(AvatarSize.RoomListItem.dp)
            .background(color = ElementTheme.colors.bgSubtleSecondary, shape = CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(24.dp))
    }
}

/** The deadline, red and with the word "Overdue" once it has passed; announced as "Due …". */
@Composable
private fun TaskDeadline(deadline: String, isOverdue: Boolean) {
    val color = if (isOverdue) ElementTheme.colors.textCriticalPrimary else ElementTheme.colors.textSecondary
    val description = stringResource(R.string.a11y_matcha_task_due, deadline)
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (isOverdue) {
            Text(
                text = stringResource(R.string.screen_matcha_tasks_overdue),
                style = ElementTheme.typography.fontBodySmMedium,
                color = color,
                maxLines = 1,
            )
        }
        Text(
            text = deadline,
            style = ElementTheme.typography.fontBodySmRegular,
            color = color,
            maxLines = 1,
            modifier = Modifier.semantics { contentDescription = description },
        )
    }
}

@Composable
private fun ReminderIcon(size: Dp) {
    Icon(
        imageVector = CompoundIcons.NotificationsSolid(),
        contentDescription = stringResource(R.string.a11y_matcha_task_reminder),
        tint = ElementTheme.colors.iconSecondary,
        modifier = Modifier.size(size),
    )
}

/** The description's first line, or the replies when there is no description. */
@Composable
private fun TaskItem.secondLine(): String = when {
    summary.isNotEmpty() -> summary
    replyCount == null -> ""
    replyCount > 0 -> pluralStringResource(CommonPlurals.common_replies, replyCount, replyCount)
    else -> stringResource(R.string.screen_matcha_tasks_no_replies)
}

@Composable
internal fun TaskStatus.label(): String = stringResource(
    when (this) {
        TaskStatus.Todo -> R.string.screen_matcha_tasks_status_todo
        TaskStatus.Doing -> R.string.screen_matcha_tasks_status_doing
        TaskStatus.Done -> R.string.screen_matcha_tasks_status_done
    }
)

@Composable
internal fun TaskStatus.icon(): ImageVector = when (this) {
    TaskStatus.Todo -> CompoundIcons.Circle()
    TaskStatus.Doing -> CompoundIcons.Time()
    TaskStatus.Done -> CompoundIcons.CheckCircleSolid()
}

@PreviewsDayNight
@Composable
internal fun TaskRowsPreview() = ElementPreview {
    Column {
        aTaskItemList().forEach { TaskRow(task = it, onClick = {}, onLongClick = {}) }
        ArchivedTasksRow(count = 3, onClick = {})
        LoadOlderTasksRow(isLoading = false, onClick = {})
        LoadOlderTasksRow(isLoading = true, onClick = {})
        TaskPlaceholderRow()
        Column(
            modifier = Modifier
                .background(ElementTheme.colors.bgSubtleSecondary)
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            aTaskItemList().forEach { TaskCard(task = it, onClick = {}, onLongClick = {}) }
        }
    }
}
