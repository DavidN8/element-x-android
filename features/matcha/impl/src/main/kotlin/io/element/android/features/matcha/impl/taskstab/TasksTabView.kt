/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.taskstab

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
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.collapse
import androidx.compose.ui.semantics.expand
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.matcha.impl.R
import io.element.android.features.matcha.impl.chatbar.SheetHeader
import io.element.android.features.matcha.impl.tasks.CenteredMessage
import io.element.android.features.matcha.impl.tasks.LoadOlderTasksRow
import io.element.android.features.matcha.impl.tasks.TaskActionsSheet
import io.element.android.features.matcha.impl.tasks.TaskEditorSheet
import io.element.android.features.matcha.impl.tasks.TaskPlaceholders
import io.element.android.features.matcha.impl.tasks.TaskRow
import io.element.android.libraries.designsystem.components.BigIcon
import io.element.android.libraries.designsystem.components.PillFilterChip
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarData
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.FloatingActionButton
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.SearchField
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.libraries.designsystem.R as DesignSystemR

/** Keeps the last row clear of the New task button. */
private val FAB_CLEARANCE = 88.dp

/** Chip labels this long or longer are cut, as in the chip bar. */
private val CHAT_CHIP_MAX_WIDTH = 160.dp

/**
 * The Tasks tab inside Home's scaffold: the chips, the tasks grouped by deadline and the list's footer, the New task button at the
 * bottom end of [contentPadding], and the sheets.
 */
@Composable
fun TasksTabView(
    state: TasksTabState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val eventSink = state.eventSink
    val layoutDirection = LocalLayoutDirection.current
    val startPadding = contentPadding.calculateStartPadding(layoutDirection)
    val endPadding = contentPadding.calculateEndPadding(layoutDirection)
    // The table's header and rows scroll sideways together.
    val tableScroll = rememberScrollState()
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWideTable = maxWidth - startPadding - endPadding >= WIDE_TABLE_MIN_WIDTH
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = startPadding,
                top = contentPadding.calculateTopPadding(),
                end = endPadding,
                bottom = contentPadding.calculateBottomPadding() + FAB_CLEARANCE,
            ),
        ) {
            // The chips scroll away with the list.
            item(key = "chips") { TasksTabChips(state) }
            when (val content = state.content) {
                TasksTabContent.Loading -> item(key = "loading") { TaskPlaceholders() }
                TasksTabContent.Empty -> item(key = "empty") { EmptyTasksTab(isSpace = state.spaceName != null, modifier = Modifier.fillParentMaxWidth()) }
                is TasksTabContent.Tasks -> taskGroups(content, state.filter, eventSink)
                is TasksTabContent.Table -> taskTable(content, state.filter, isWideTable, tableScroll, eventSink)
            }
            footer(state)
        }
        FloatingActionButton(
            onClick = { eventSink(TasksTabEvent.NewTask) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = endPadding + 16.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
        ) {
            Icon(imageVector = CompoundIcons.Plus(), contentDescription = stringResource(R.string.screen_matcha_tasks_new))
        }
    }
    when (val sheet = state.sheet) {
        is TasksTabSheet.ChooseChat -> ChooseChatSheet(
            sheet = sheet,
            onChoose = { eventSink(TasksTabEvent.ChooseChat(it)) },
            onDismiss = { eventSink(TasksTabEvent.DismissSheet) },
        )
        is TasksTabSheet.ChoosePerson -> ChoosePersonSheet(
            sheet = sheet,
            onChoose = { eventSink(TasksTabEvent.ChoosePerson(it)) },
            onDismiss = { eventSink(TasksTabEvent.DismissSheet) },
        )
        is TasksTabSheet.Actions -> {
            val roomId = sheet.task.chat.roomId
            val taskId = sheet.task.task.id
            TaskActionsSheet(
                task = sheet.task.task,
                canEdit = sheet.canEdit,
                canArchive = sheet.canArchive,
                // The reminder sheet belongs to the chat, so the tab offers Open chat instead.
                canRemind = false,
                onDismiss = { eventSink(TasksTabEvent.DismissSheet) },
                onMove = { eventSink(TasksTabEvent.Move(roomId, taskId, it)) },
                onArchive = { eventSink(TasksTabEvent.SetArchived(roomId, taskId, archived = true)) },
                onUnarchive = { eventSink(TasksTabEvent.SetArchived(roomId, taskId, archived = false)) },
                onOpenThread = { eventSink(TasksTabEvent.OpenTask(roomId, taskId)) },
                onRemind = {},
                onEdit = { eventSink(TasksTabEvent.EditTask(roomId, taskId)) },
                chatName = sheet.task.chat.displayName(),
                onOpenChat = { eventSink(TasksTabEvent.OpenChat(roomId)) },
            )
        }
        null -> Unit
    }
    state.editor?.let { TaskEditorSheet(it) }
}

/** All · To do · In progress · Done · Archived, then Chat ▾ and People ▾, and the list or table switch at the end. */
@Composable
private fun TasksTabChips(state: TasksTabState) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        FilterChips(state, modifier = Modifier.weight(1f))
        val description = stringResource(if (state.isTable) R.string.a11y_matcha_tasks_show_list else R.string.a11y_matcha_tasks_show_table)
        IconButton(onClick = { state.eventSink(TasksTabEvent.ToggleTable) }, modifier = Modifier.padding(end = 4.dp)) {
            Icon(imageVector = if (state.isTable) CompoundIcons.ListView() else CompoundIcons.Grid(), contentDescription = description)
        }
    }
}

@Composable
private fun FilterChips(state: TasksTabState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .selectableGroup()
            .padding(start = 16.dp, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TasksTabFilter.entries.forEach { filter ->
            PillFilterChip(
                label = filter.label(),
                selected = state.filter == filter,
                onClick = { state.eventSink(TasksTabEvent.SetFilter(filter)) },
            )
        }
        val chat = state.chatFilter
        PillFilterChip(
            label = chat?.displayName() ?: stringResource(R.string.screen_matcha_tasks_filter_chat),
            selected = chat != null,
            // Tapping the chosen chat shows every chat again, like the other chips.
            contentDescription = chat?.let { stringResource(CommonStrings.action_clear) },
            maxLabelWidth = CHAT_CHIP_MAX_WIDTH,
            role = Role.Button,
            trailingIcon = CompoundIcons.ChevronDown(),
            onClick = { state.eventSink(TasksTabEvent.ToggleChatFilter) },
        )
        val person = state.personFilter
        if (state.hasPeople || person != null) {
            PillFilterChip(
                label = person?.name ?: stringResource(R.string.screen_matcha_tasks_filter_people),
                selected = person != null,
                // Tapping the chosen person shows everyone's tasks again, like the chat chip.
                contentDescription = person?.let { stringResource(CommonStrings.action_clear) },
                maxLabelWidth = CHAT_CHIP_MAX_WIDTH,
                role = Role.Button,
                trailingIcon = CompoundIcons.ChevronDown(),
                onClick = { state.eventSink(TasksTabEvent.TogglePersonFilter) },
            )
        }
    }
}

private fun LazyListScope.taskGroups(content: TasksTabContent.Tasks, filter: TasksTabFilter, eventSink: (TasksTabEvent) -> Unit) {
    if (content.groups.isEmpty() && content.done.isEmpty()) {
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
    content.groups.forEach { group ->
        if (group.section != null) item(key = "section-${group.section}") { SectionHeader(group.section.label()) }
        items(group.tasks, key = { it.key() }) { TabTaskRow(it, eventSink) }
    }
    if (content.done.isNotEmpty()) {
        item(key = "done") { DoneRow(count = content.done.size, isExpanded = content.isDoneExpanded, onClick = { eventSink(TasksTabEvent.ToggleDone) }) }
        if (content.isDoneExpanded) items(content.done, key = { it.key() }) { TabTaskRow(it, eventSink) }
    }
}

private fun LazyListScope.footer(state: TasksTabState) {
    val isLoading = state.content == TasksTabContent.Loading
    if (state.hasMore && !isLoading) {
        item(key = "load-older") { LoadOlderTasksRow(isLoading = state.isLoadingMore, onClick = { state.eventSink(TasksTabEvent.LoadMore) }) }
    }
    if (state.checkingCount > 0 && !isLoading) {
        item(key = "checking") { FooterNote(pluralStringResource(R.plurals.screen_matcha_tasks_checking_chats, state.checkingCount, state.checkingCount)) }
    }
    if (state.failedCount > 0) {
        item(key = "failed") { CheckFailedRow(count = state.failedCount, onRetry = { state.eventSink(TasksTabEvent.Retry) }) }
    }
    if (state.isUnreadable) {
        item(key = "unreadable") { FooterNote(stringResource(R.string.screen_matcha_tasks_tab_unreadable)) }
    }
}

@Composable
private fun TabTaskRow(task: TabTask, eventSink: (TasksTabEvent) -> Unit) {
    TaskRow(
        task = task.task,
        onClick = { eventSink(TasksTabEvent.OpenTask(task.chat.roomId, task.task.id)) },
        onLongClick = { eventSink(TasksTabEvent.ShowActions(task.chat.roomId, task.task.id)) },
        chatName = task.chat.displayName(),
        chatAvatar = task.chat.avatar,
        isSelected = task.isSelected,
        people = task.peopleLine(),
    )
}

/** "With Bob, Carol", or "Just you". */
@Composable
private fun TabTask.peopleLine(): String = when {
    people.isEmpty() -> stringResource(R.string.screen_matcha_tasks_just_you)
    else -> stringResource(R.string.screen_matcha_tasks_with_people, people.joinToString())
}

/** The task's other people in a table cell: "Bob, Carol", or "Just you". */
@Composable
internal fun TabTask.peopleCell(): String = when {
    people.isEmpty() -> stringResource(R.string.screen_matcha_tasks_just_you)
    else -> people.joinToString()
}

@Composable
private fun SectionHeader(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            text = text,
            style = ElementTheme.typography.fontBodyMdMedium,
            color = ElementTheme.colors.textSecondary,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/** "Done · N ›": the done tasks, collapsed under All until tapped. */
@Composable
private fun DoneRow(count: Int, isExpanded: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp)
            .semantics(mergeDescendants = true) {
                heading()
                if (isExpanded) {
                    collapse {
                        onClick()
                        true
                    }
                } else {
                    expand {
                        onClick()
                        true
                    }
                }
            },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = pluralStringResource(R.plurals.screen_matcha_tasks_done_section, count, count),
            style = ElementTheme.typography.fontBodyMdMedium,
            color = ElementTheme.colors.textSecondary,
        )
        Icon(
            imageVector = if (isExpanded) CompoundIcons.ChevronDown() else CompoundIcons.ChevronRight(),
            contentDescription = null,
            tint = ElementTheme.colors.iconSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun FooterNote(text: String) {
    Text(
        text = text,
        style = ElementTheme.typography.fontBodyMdRegular,
        color = ElementTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    )
}

/** "Couldn't check N chats" with Retry. */
@Composable
private fun CheckFailedRow(count: Int, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = pluralStringResource(R.plurals.screen_matcha_tasks_check_failed, count, count),
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        TextButton(text = stringResource(CommonStrings.action_retry), onClick = onRetry)
    }
}

/** No task yet, on the Tasks tab or in a space's boards ([isSpace]). */
@Composable
private fun EmptyTasksTab(isSpace: Boolean, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BigIcon(style = BigIcon.Style.Default(ImageVector.vectorResource(DesignSystemR.drawable.ic_matcha_board)))
        Spacer(Modifier.height(16.dp))
        CenteredMessage(
            title = stringResource(R.string.screen_matcha_tasks_tab_empty_title),
            body = stringResource(if (isSpace) R.string.screen_matcha_space_boards_empty_body else R.string.screen_matcha_tasks_tab_empty_body),
        )
    }
}

/** Chat ▾ and New task's Choose a chat: a search pill over chat rows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChooseChatSheet(sheet: TasksTabSheet.ChooseChat, onChoose: (RoomId) -> Unit, onDismiss: () -> Unit) {
    val title = stringResource(
        when (sheet.purpose) {
            ChooseChatPurpose.Filter -> R.string.screen_matcha_tasks_filter_chat
            ChooseChatPurpose.NewTask -> R.string.screen_matcha_tasks_pick_chat_title
        }
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        scrollable = false,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        modifier = Modifier.semantics { paneTitle = title },
    ) {
        SheetHeader(title = title, onClose = onDismiss)
        if (sheet.chats.isEmpty()) {
            SheetNote(stringResource(R.string.screen_matcha_tasks_pick_chat_empty))
        } else {
            SearchField(
                state = sheet.searchQuery,
                placeholder = stringResource(CommonStrings.action_search),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
            )
            val chats = sheet.visibleChats
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .imePadding(),
            ) {
                if (chats.isEmpty()) item(key = "no-results") { SheetNote(stringResource(CommonStrings.common_no_results)) }
                items(chats, key = { it.roomId.value }) { chat -> ChatRow(chat = chat, onClick = { onChoose(chat.roomId) }) }
            }
        }
    }
}

/** People ▾: everyone the user works with, then each of them. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoosePersonSheet(sheet: TasksTabSheet.ChoosePerson, onChoose: (String?) -> Unit, onDismiss: () -> Unit) {
    val title = stringResource(R.string.screen_matcha_tasks_filter_people_title)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        scrollable = false,
        sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded)),
        modifier = Modifier.semantics { paneTitle = title },
    ) {
        SheetHeader(title = title, onClose = onDismiss)
        LazyColumn(modifier = Modifier.fillMaxWidth().selectableGroup()) {
            item(key = "everyone") {
                PersonRow(
                    name = stringResource(R.string.screen_matcha_tasks_all_people),
                    avatar = null,
                    isSelected = sheet.selectedUserId == null,
                    onClick = { onChoose(null) },
                )
            }
            items(sheet.people, key = { it.userId }) { person ->
                PersonRow(
                    name = person.name,
                    avatar = AvatarData(id = person.userId, name = person.name, size = AvatarSize.RoomListItem),
                    isSelected = sheet.selectedUserId == person.userId,
                    onClick = { onChoose(person.userId) },
                )
            }
        }
    }
}

/** A person to filter by, or everyone when [avatar] is null; the chosen one is ticked. */
@Composable
private fun PersonRow(name: String, avatar: AvatarData?, isSelected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .selectable(selected = isSelected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (avatar != null) {
            Avatar(avatarData = avatar, avatarType = AvatarType.User)
        } else {
            Box(
                modifier = Modifier
                    .size(AvatarSize.RoomListItem.dp)
                    .background(color = ElementTheme.colors.bgSubtleSecondary, shape = CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = CompoundIcons.Group(), contentDescription = null, tint = ElementTheme.colors.iconSecondary)
            }
        }
        Text(
            text = name,
            style = ElementTheme.typography.fontBodyLgMedium,
            color = ElementTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (isSelected) Icon(imageVector = CompoundIcons.Check(), contentDescription = null, tint = ElementTheme.colors.iconAccentPrimary)
    }
}

@Composable
private fun SheetNote(text: String) {
    Text(
        text = text,
        style = ElementTheme.typography.fontBodyMdRegular,
        color = ElementTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 32.dp),
    )
}

@Composable
private fun ChatRow(chat: TasksTabChat, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(avatarData = chat.avatar, avatarType = AvatarType.Room())
        Text(
            text = chat.displayName(),
            style = ElementTheme.typography.fontBodyLgMedium,
            color = ElementTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun TasksTabChat.displayName(): String = name ?: stringResource(CommonStrings.common_no_room_name)

private fun TabTask.key() = "${chat.roomId.value}/${task.id}"

@Composable
private fun TasksTabFilter.label(): String = stringResource(
    when (this) {
        TasksTabFilter.All -> CommonStrings.common_matcha_all
        TasksTabFilter.Todo -> R.string.screen_matcha_tasks_status_todo
        TasksTabFilter.Doing -> R.string.screen_matcha_tasks_status_doing
        TasksTabFilter.Done -> R.string.screen_matcha_tasks_status_done
        TasksTabFilter.Archived -> CommonStrings.common_matcha_archived
    }
)

@Composable
private fun TaskSection.label(): String = when (this) {
    TaskSection.Overdue -> stringResource(R.string.screen_matcha_tasks_overdue)
    TaskSection.Today -> stringResource(R.string.screen_matcha_tasks_section_today)
    TaskSection.Tomorrow -> stringResource(R.string.screen_matcha_tasks_section_tomorrow)
    TaskSection.Upcoming -> stringResource(R.string.screen_matcha_tasks_section_upcoming)
    TaskSection.NoDeadline -> stringResource(R.string.screen_matcha_tasks_section_no_deadline)
}

@PreviewsDayNight
@Composable
internal fun TasksTabViewPreview(@PreviewParameter(TasksTabStatePreviewParam::class) state: TasksTabState) = ElementPreview {
    TasksTabView(state = state, contentPadding = PaddingValues())
}

/** The Tasks tab on a screen wide enough for the table to fit. */
@Composable
private fun WideTasksTabView(state: TasksTabState) {
    Box(modifier = Modifier.width(720.dp)) {
        TasksTabView(state = state, contentPadding = PaddingValues())
    }
}

@PreviewsDayNight
@Composable
internal fun WideTasksTabViewPreview() = ElementPreview {
    WideTasksTabView(aTasksTabState(isTable = true, content = aTableContent()))
}
