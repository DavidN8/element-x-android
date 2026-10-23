/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.tasks

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.appconfig.MatchaConfig
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.matcha.impl.R
import io.element.android.features.matcha.impl.chatbar.SheetHeader
import io.element.android.features.matcha.impl.protocol.TaskStatus
import io.element.android.libraries.designsystem.components.PillFilterChip
import io.element.android.libraries.designsystem.components.dialogs.ConfirmationDialog
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.ui.strings.CommonStrings
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** The counters appear once a field gets close to its limit. */
private const val TITLE_COUNTER_FROM = 80
private const val DESCRIPTION_COUNTER_FROM = 3_800
private val DEFAULT_DEADLINE_TIME: LocalTime = LocalTime.of(17, 0)

/** The task editor: one full-height sheet with the name, the description, then status, deadline, reminder and thread rows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskEditorSheet(state: TaskEditorState) {
    val eventSink = state.eventSink
    val currentState by rememberUpdatedState(state)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        // Swiping down with changes asks first instead of dropping them.
        confirmValueChange = { value ->
            if (value == SheetValue.Hidden && currentState.hasChanges) {
                currentState.eventSink(TaskEditorEvent.Close)
                false
            } else {
                true
            }
        },
    )
    val title = stringResource(if (state.isNew) R.string.screen_matcha_tasks_new else R.string.screen_matcha_tasks_edit)
    ModalBottomSheet(
        onDismissRequest = { eventSink(TaskEditorEvent.Close) },
        scrollable = false,
        sheetState = sheetState,
        modifier = Modifier.semantics { paneTitle = title },
    ) {
        TaskEditorContent(state = state, title = title)
    }
    if (state.showDiscardDialog) {
        ConfirmationDialog(
            content = stringResource(R.string.screen_matcha_tasks_discard_title),
            submitText = stringResource(CommonStrings.action_discard),
            cancelText = stringResource(CommonStrings.action_matcha_keep_editing),
            destructiveSubmit = true,
            onSubmitClick = { eventSink(TaskEditorEvent.Discard) },
            onDismiss = { eventSink(TaskEditorEvent.KeepEditing) },
        )
    }
}

@Composable
private fun ColumnScope.TaskEditorContent(state: TaskEditorState, title: String) {
    val eventSink = state.eventSink
    val titleFocus = remember { FocusRequester() }
    val descriptionFocus = remember { FocusRequester() }
    // Saving with errors moves the focus to the first one.
    LaunchedEffect(state.titleError, state.descriptionError) {
        when {
            state.titleError != null -> titleFocus.requestFocus()
            state.descriptionError != null -> descriptionFocus.requestFocus()
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .imePadding()
    ) {
        SheetHeader(title = title, onClose = { eventSink(TaskEditorEvent.Close) })
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            BorderlessField(
                value = state.title,
                onValueChange = { eventSink(TaskEditorEvent.SetTitle(it)) },
                hint = stringResource(R.string.screen_matcha_tasks_name_hint),
                style = ElementTheme.typography.fontHeadingMdRegular,
                maxLines = 2,
                enabled = state.canRename && !state.isSaving,
                error = state.titleError?.message(),
                helper = when {
                    !state.canRename -> stringResource(R.string.screen_matcha_tasks_rename_not_allowed)
                    !state.isNew && state.isLinked -> stringResource(R.string.screen_matcha_tasks_thread_note_linked)
                    else -> null
                },
                counter = counter(state.title.trim().length, state.titleMaxLength, TITLE_COUNTER_FROM),
                focusRequester = titleFocus,
            )
            BorderlessField(
                value = state.description,
                onValueChange = { eventSink(TaskEditorEvent.SetDescription(it)) },
                hint = stringResource(R.string.screen_matcha_tasks_description_hint),
                style = ElementTheme.typography.fontBodyLgRegular,
                maxLines = Int.MAX_VALUE,
                enabled = !state.isSaving,
                error = state.descriptionError?.message(),
                helper = null,
                counter = counter(state.description.length, MatchaConfig.CARD_DESCRIPTION_MAX_LENGTH, DESCRIPTION_COUNTER_FROM),
                focusRequester = descriptionFocus,
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            StatusRow(state)
            DeadlineRow(state)
            if (state.canRemind) ReminderRow(state)
            ThreadRow(state)
            if (state.earlierCards.isNotEmpty()) EarlierCardsRow(state)
        }
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.saveError?.let { error ->
                Text(
                    text = stringResource(error.messageResId()),
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textCriticalPrimary,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            Button(
                text = stringResource(if (state.isNew) R.string.screen_matcha_tasks_add else CommonStrings.action_save),
                onClick = { eventSink(TaskEditorEvent.Save) },
                showProgress = state.isSaving,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun BorderlessField(
    value: String,
    onValueChange: (String) -> Unit,
    hint: String,
    style: TextStyle,
    maxLines: Int,
    enabled: Boolean,
    error: String?,
    helper: String?,
    counter: String?,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val textColor = if (enabled) ElementTheme.colors.textPrimary else ElementTheme.colors.textSecondary
    Column(modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            textStyle = style.copy(color = textColor),
            maxLines = maxLines,
            cursorBrush = SolidColor(ElementTheme.colors.iconAccentPrimary),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .focusRequester(focusRequester)
                .semantics {
                    contentDescription = hint
                    if (error != null) this.error(error)
                },
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) Text(text = hint, style = style, color = ElementTheme.colors.textSecondary)
                    innerTextField()
                }
            },
        )
        if (error != null || helper != null || counter != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = error ?: helper.orEmpty(),
                    style = ElementTheme.typography.fontBodySmRegular,
                    color = if (error != null) ElementTheme.colors.textCriticalPrimary else ElementTheme.colors.textSecondary,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { if (error != null) liveRegion = LiveRegionMode.Polite },
                )
                if (counter != null) {
                    Text(
                        text = counter,
                        style = ElementTheme.typography.fontBodySmRegular,
                        color = if (error != null) ElementTheme.colors.textCriticalPrimary else ElementTheme.colors.textSecondary,
                    )
                }
            }
        }
    }
}

/** A 56dp row: a 24dp icon at 16dp and the content at 72dp. */
@Composable
private fun IconRow(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = ElementTheme.colors.iconSecondary, modifier = Modifier.size(24.dp))
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 32.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
            content = content,
        )
        trailing?.invoke()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StatusRow(state: TaskEditorState) {
    val label = stringResource(R.string.screen_matcha_tasks_status_label)
    IconRow(icon = CompoundIcons.CheckCircle()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .selectableGroup()
                .semantics { contentDescription = label },
        ) {
            TaskStatus.entries.forEach { status ->
                PillFilterChip(
                    label = status.label(),
                    selected = state.status == status,
                    onClick = { state.eventSink(TaskEditorEvent.SetStatus(status)) },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DeadlineRow(state: TaskEditorState) {
    var picking by rememberSaveable { mutableStateOf<DeadlinePick?>(null) }
    var pickedDate by rememberSaveable { mutableStateOf<LocalDate?>(null) }
    val current = state.dueAt?.let { Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDateTime() }
    IconRow(
        icon = CompoundIcons.Calendar(),
        onClick = { picking = DeadlinePick.Date },
        trailing = state.deadline?.let {
            {
                IconButton(onClick = { state.eventSink(TaskEditorEvent.ClearDeadline) }) {
                    Icon(imageVector = CompoundIcons.Close(), contentDescription = stringResource(R.string.a11y_matcha_clear_deadline))
                }
            }
        },
    ) {
        Text(
            text = state.deadline ?: stringResource(R.string.screen_matcha_tasks_deadline_add),
            style = ElementTheme.typography.fontBodyLgRegular,
            color = if (state.deadline != null) ElementTheme.colors.textPrimary else ElementTheme.colors.textActionAccent,
            modifier = Modifier.semantics {
                if (state.deadline != null) contentDescription = state.deadline
            },
        )
        state.deadlineError?.let { error ->
            Text(
                text = stringResource(error.messageResId()),
                style = ElementTheme.typography.fontBodySmRegular,
                color = if (error == TaskDeadlineError.Past) ElementTheme.colors.textSecondary else ElementTheme.colors.textCriticalPrimary,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
    }
    when (picking) {
        DeadlinePick.Date -> {
            val datePickerState = rememberDatePickerState(
                initialSelectedDateMillis = (current?.toLocalDate() ?: LocalDate.now()).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            )
            DatePickerDialog(
                onDismissRequest = { picking = null },
                confirmButton = {
                    TextButton(
                        text = stringResource(CommonStrings.action_next),
                        enabled = datePickerState.selectedDateMillis != null,
                        onClick = {
                            // The picker's dates are midnights in UTC.
                            pickedDate = datePickerState.selectedDateMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                            picking = DeadlinePick.Time
                        },
                    )
                },
                dismissButton = { TextButton(text = stringResource(CommonStrings.action_cancel), onClick = { picking = null }) },
            ) {
                DatePicker(state = datePickerState)
            }
        }
        DeadlinePick.Time -> {
            val time = current?.toLocalTime() ?: DEFAULT_DEADLINE_TIME
            val timePickerState = rememberTimePickerState(
                initialHour = time.hour,
                initialMinute = time.minute,
                is24Hour = DateFormat.is24HourFormat(LocalContext.current),
            )
            TimePickerDialog(
                onDismissRequest = { picking = null },
                title = { Text(text = stringResource(R.string.screen_matcha_tasks_deadline_label)) },
                confirmButton = {
                    TextButton(
                        text = stringResource(CommonStrings.action_done),
                        onClick = {
                            pickedDate?.let { date ->
                                state.eventSink(TaskEditorEvent.SetDeadline(LocalDateTime.of(date, LocalTime.of(timePickerState.hour, timePickerState.minute))))
                            }
                            picking = null
                        },
                    )
                },
                dismissButton = { TextButton(text = stringResource(CommonStrings.action_cancel), onClick = { picking = null }) },
            ) {
                TimePicker(state = timePickerState)
            }
        }
        null -> Unit
    }
}

private enum class DeadlinePick { Date, Time }

@Composable
private fun ReminderRow(state: TaskEditorState) {
    IconRow(icon = CompoundIcons.Notifications(), onClick = { state.eventSink(TaskEditorEvent.RemindMe) }) {
        Text(
            text = stringResource(CommonStrings.action_matcha_remind_me),
            style = ElementTheme.typography.fontBodyLgRegular,
            color = ElementTheme.colors.textActionAccent,
        )
        state.reminderRequestedAt?.let { at ->
            Text(
                text = stringResource(R.string.screen_matcha_tasks_reminder_requested, at),
                style = ElementTheme.typography.fontBodySmRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
    }
}

@Composable
private fun ThreadRow(state: TaskEditorState) {
    when {
        state.isNew -> IconRow(icon = CompoundIcons.Threads()) {
            Text(
                text = stringResource(R.string.screen_matcha_tasks_thread_note_new),
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
            )
        }
        state.isLinked -> IconRow(icon = CompoundIcons.Threads(), onClick = { state.eventSink(TaskEditorEvent.OpenThread) }) {
            Text(
                text = stringResource(R.string.screen_matcha_tasks_open_thread),
                style = ElementTheme.typography.fontBodyLgRegular,
                color = ElementTheme.colors.textActionAccent,
            )
        }
    }
}

@Composable
private fun EarlierCardsRow(state: TaskEditorState) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    IconRow(icon = CompoundIcons.History(), onClick = { expanded = !expanded }) {
        Text(
            text = pluralStringResource(R.plurals.screen_matcha_tasks_earlier_cards, state.earlierCards.size, state.earlierCards.size),
            style = ElementTheme.typography.fontBodyLgRegular,
            color = ElementTheme.colors.textPrimary,
        )
        if (expanded) {
            state.earlierCards.forEach { name ->
                Text(text = name, style = ElementTheme.typography.fontBodyMdRegular, color = ElementTheme.colors.textSecondary)
            }
        }
    }
}

@Composable
private fun TaskFieldError.message(): String = when (this) {
    TaskFieldError.Empty -> stringResource(CommonStrings.common_matcha_error_name_empty)
    is TaskFieldError.TooLong -> stringResource(CommonStrings.common_matcha_error_max_chars, NumberFormat.getIntegerInstance().format(maxLength))
}

private fun TaskDeadlineError.messageResId() = when (this) {
    TaskDeadlineError.Past -> R.string.screen_matcha_tasks_deadline_past
    TaskDeadlineError.Skipped -> R.string.screen_matcha_tasks_deadline_skipped
    TaskDeadlineError.OutOfRange -> R.string.screen_matcha_tasks_deadline_range
}

internal fun TaskWriteError.messageResId() = when (this) {
    TaskWriteError.BoardChanged -> R.string.screen_matcha_tasks_board_changed
    TaskWriteError.ThreadHasTask -> R.string.screen_matcha_tasks_thread_has_task
    TaskWriteError.HistoryIncomplete -> R.string.screen_matcha_tasks_history_incomplete
    TaskWriteError.ThreadSetupFailed -> R.string.screen_matcha_threads_setup_failed
    TaskWriteError.Failed -> CommonStrings.common_matcha_write_failed
}

/** "84/100" once [length] reaches [from]. */
private fun counter(length: Int, max: Int, from: Int): String? = if (length >= from) "$length/$max" else null

@PreviewsDayNight
@Composable
internal fun TaskEditorSheetPreview(@PreviewParameter(TaskEditorStatePreviewParam::class) state: TaskEditorState) = ElementPreview {
    TaskEditorSheet(state = state)
}
