/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.tasks

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.matcha.impl.R
import io.element.android.features.matcha.impl.chatbar.ActionRow
import io.element.android.features.matcha.impl.chatbar.SheetHeader
import io.element.android.features.matcha.impl.protocol.TaskStatus
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.libraries.designsystem.R as DesignSystemR

/**
 * A task's long-press actions. An active task can be moved, opened, reminded about, edited and archived; an archived one can be
 * unarchived, opened and edited. Without [canEdit] only Open thread and Remind me are offered. Across chats (the Tasks tab),
 * [chatName] names the task's chat under the title and [onOpenChat] adds Open chat.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskActionsSheet(
    task: TaskItem,
    canEdit: Boolean,
    canArchive: Boolean,
    canRemind: Boolean,
    onDismiss: () -> Unit,
    onMove: (TaskStatus) -> Unit,
    onArchive: () -> Unit,
    onUnarchive: () -> Unit,
    onOpenThread: () -> Unit,
    onRemind: () -> Unit,
    onEdit: () -> Unit,
    chatName: String? = null,
    onOpenChat: (() -> Unit)? = null,
) {
    val title = task.title ?: stringResource(CommonStrings.common_matcha_thread_untitled)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        scrollable = true,
        modifier = Modifier.semantics { paneTitle = title },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            SheetHeader(
                title = title,
                subtitle = chatName?.let { stringResource(R.string.screen_matcha_tasks_in_chat, it) },
                modifier = Modifier.padding(bottom = 8.dp),
            )
            if (canEdit && !task.isArchived) {
                Text(
                    text = stringResource(R.string.screen_matcha_tasks_move_to),
                    style = ElementTheme.typography.fontBodyMdMedium,
                    color = ElementTheme.colors.textSecondary,
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .semantics { heading() },
                )
                Column(modifier = Modifier.selectableGroup()) {
                    TaskStatus.entries.forEach { status ->
                        ListItem(
                            leadingContent = ListItemContent.RadioButton(selected = task.status == status),
                            onClick = { onMove(status) },
                        ) {
                            Text(text = status.label())
                        }
                    }
                }
            }
            if (canArchive && task.isArchived) {
                ActionRow(
                    text = stringResource(CommonStrings.action_matcha_unarchive),
                    icon = ImageVector.vectorResource(DesignSystemR.drawable.ic_matcha_archive),
                    onClick = onUnarchive,
                )
            }
            if (task.threadId != null) {
                ActionRow(
                    text = stringResource(R.string.screen_matcha_tasks_open_thread),
                    icon = CompoundIcons.Threads(),
                    onClick = onOpenThread,
                )
            }
            if (onOpenChat != null) {
                ActionRow(
                    text = stringResource(R.string.screen_matcha_tasks_open_chat),
                    icon = CompoundIcons.Chat(),
                    onClick = onOpenChat,
                )
            }
            if (canRemind && !task.isArchived) {
                ActionRow(
                    text = stringResource(CommonStrings.action_matcha_remind_me),
                    icon = CompoundIcons.Notifications(),
                    onClick = onRemind,
                )
            }
            if (canEdit) {
                ActionRow(
                    text = stringResource(R.string.screen_matcha_tasks_edit),
                    icon = CompoundIcons.Edit(),
                    onClick = onEdit,
                )
            }
            if (canArchive && !task.isArchived) {
                ActionRow(
                    text = stringResource(CommonStrings.action_matcha_archive),
                    icon = ImageVector.vectorResource(DesignSystemR.drawable.ic_matcha_archive),
                    onClick = onArchive,
                )
            }
        }
    }
}

/** The actions sheet of a task on its chat's board. */
@Composable
internal fun TaskActionsSheet(task: TaskItem, board: TaskBoardContent.Board, eventSink: (TaskBoardEvent) -> Unit) {
    TaskActionsSheet(
        task = task,
        canEdit = board.canEdit,
        canArchive = board.canArchive(task),
        canRemind = board.canRemind,
        onDismiss = { eventSink(TaskBoardEvent.DismissActions) },
        onMove = { eventSink(TaskBoardEvent.Move(task.id, it)) },
        onArchive = { eventSink(TaskBoardEvent.SetArchived(task.id, archived = true)) },
        onUnarchive = { eventSink(TaskBoardEvent.SetArchived(task.id, archived = false)) },
        onOpenThread = { eventSink(TaskBoardEvent.OpenTask(task.id)) },
        onRemind = { eventSink(TaskBoardEvent.RemindMe(task.id)) },
        onEdit = { eventSink(TaskBoardEvent.EditTask(task.id)) },
    )
}

@PreviewsDayNight
@Composable
internal fun TaskActionsSheetPreview() = ElementPreview {
    TaskActionsSheet(task = aTaskItemList().first(), board = aBoardContent(), eventSink = {})
}

@PreviewsDayNight
@Composable
internal fun TaskActionsSheetReadOnlyPreview() = ElementPreview {
    TaskActionsSheet(task = aTaskItemList().first(), board = aBoardContent(canEdit = false), eventSink = {})
}

@PreviewsDayNight
@Composable
internal fun TaskActionsSheetArchivedPreview() = ElementPreview {
    TaskActionsSheet(task = aTaskItemList().first().copy(isArchived = true), board = aBoardContent(), eventSink = {})
}
