/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.reminders

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SheetValue
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.TimePickerDialogDefaults
import androidx.compose.material3.TimePickerDisplayMode
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.appconfig.MatchaConfig
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.matcha.impl.R
import io.element.android.features.matcha.impl.ui.SheetHeader
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.designsystem.theme.components.TextFieldValidity
import io.element.android.libraries.designsystem.theme.components.hide
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.ui.strings.CommonStrings
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.absoluteValue

/** The "Remind me" sheet. Closes itself once the reminder is requested. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoomReminderView(
    state: RoomReminderState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val coroutineScope = rememberCoroutineScope()
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(state.isRequested) {
        if (state.isRequested) {
            sheetState.hide()
            latestOnDismiss()
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        scrollable = true,
        modifier = modifier,
    ) {
        RoomReminderContent(
            state = state,
            onClose = { sheetState.hide(coroutineScope, onDismiss) },
        )
    }
}

@Composable
private fun RoomReminderContent(
    state: RoomReminderState,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val botState = state.botState
    val isCustomStep = state.step == RoomReminderStep.Custom && botState == RoomReminderBotState.Joined
    BackHandler(enabled = isCustomStep) { state.eventSink(RoomReminderEvent.CloseCustom) }
    val title = stringResource(
        when {
            botState is RoomReminderBotState.CanInvite -> R.string.screen_matcha_reminder_bot_invite_title
            isCustomStep -> R.string.screen_matcha_reminder_custom
            else -> R.string.screen_matcha_reminder_title
        }
    )
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
            .semantics { paneTitle = title },
    ) {
        SheetHeader(
            title = title,
            isBack = isCustomStep,
            onNavigationClick = { if (isCustomStep) state.eventSink(RoomReminderEvent.CloseCustom) else onClose() },
        )
        when {
            botState is RoomReminderBotState.CanInvite -> InviteBotStep(
                botState = botState,
                botUserId = state.botUserId,
                onInvite = { state.eventSink(RoomReminderEvent.InviteBot) },
                onCancel = onClose
            )
            isCustomStep -> CustomStep(state)
            else -> OptionsStep(state)
        }
    }
}

@Composable
private fun OptionsStep(state: RoomReminderState) {
    val botState = state.botState
    when (botState) {
        RoomReminderBotState.CannotSend -> InfoText(stringResource(R.string.screen_matcha_reminder_cannot_send))
        RoomReminderBotState.CannotInvite -> InfoText(stringResource(R.string.screen_matcha_reminder_bot_no_permission))
        RoomReminderBotState.UnavailableInDm -> InfoText(stringResource(R.string.screen_matcha_reminder_bot_dm_unavailable))
        else -> Column {
            Text(
                text = stringResource(R.string.screen_matcha_reminder_helper),
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            )
            when (botState) {
                RoomReminderBotState.Checking -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
                is RoomReminderBotState.Waiting -> {
                    MessageField(state)
                    WaitingForBot(isChecking = botState.isChecking, onRetry = { state.eventSink(RoomReminderEvent.CheckBotAgain) })
                }
                else -> {
                    MessageField(state)
                    QuickOptions(state)
                }
            }
        }
    }
}

/** A radio group with nothing chosen at first: choosing only fills in the confirm button, which posts. */
@Composable
private fun QuickOptions(state: RoomReminderState) {
    val formats = rememberReminderFormats()
    val today = state.now.toLocalDate()
    Column {
        if (state.hasSendFailed) ErrorLine(stringResource(R.string.screen_matcha_reminder_error_send))
        Column(modifier = Modifier.selectableGroup()) {
            state.quickOptions.forEach { option ->
                OptionRow(
                    label = when (option) {
                        is RoomReminderOption.InHours -> pluralStringResource(R.plurals.screen_matcha_reminder_in_hours, option.hours, option.hours)
                        else -> stringResource(R.string.screen_matcha_reminder_at_deadline)
                    },
                    value = formats.moment(option.moment(state), today),
                    selected = state.selected == option,
                    enabled = state.sending == null,
                    onClick = { state.eventSink(RoomReminderEvent.Select(option)) },
                )
            }
        }
        ListItem(
            trailingContent = ListItemContent.Icon(IconSource.Vector(CompoundIcons.ChevronRight())),
            enabled = state.sending == null,
            onClick = { state.eventSink(RoomReminderEvent.OpenCustom) },
        ) {
            Text(stringResource(R.string.screen_matcha_reminder_custom))
        }
        ConfirmButton(state = state, option = state.selected, enabled = true)
    }
}

/** The green pill that posts the reminder, "Set reminder · Today 14:05"; disabled until a time is chosen. */
@Composable
private fun ConfirmButton(state: RoomReminderState, option: RoomReminderOption?, enabled: Boolean) {
    val formats = rememberReminderFormats()
    Button(
        text = if (option == null) {
            stringResource(R.string.screen_matcha_reminder_set)
        } else {
            stringResource(R.string.screen_matcha_reminder_confirm, formats.moment(option.moment(state), state.now.toLocalDate()))
        },
        onClick = { option?.let { state.eventSink(RoomReminderEvent.Send(it)) } },
        enabled = enabled && option != null && state.sending == null,
        showProgress = option != null && state.sending == option,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp),
    )
}

/** When the option reminds, on the wall clock of [RoomReminderState.now]. */
private fun RoomReminderOption.moment(state: RoomReminderState): LocalDateTime = when (this) {
    is RoomReminderOption.InHours -> state.now.plusHours(hours.toLong()).toLocalDateTime()
    is RoomReminderOption.AtDeadline -> Instant.ofEpochMilli(at).atZone(state.now.zone).toLocalDateTime()
    RoomReminderOption.Custom -> LocalDateTime.of(state.customDate, state.customTime)
}

@Composable
private fun MessageField(state: RoomReminderState) {
    val errorText = state.messageError?.text(state.maxMessageLength)
    val footnote = when (state.link) {
        RoomReminderLink.Message -> stringResource(R.string.screen_matcha_reminder_link_message)
        RoomReminderLink.Thread -> stringResource(R.string.screen_matcha_reminder_link_thread)
        null -> null
    }
    TextField(
        value = state.message,
        onValueChange = { state.eventSink(RoomReminderEvent.UpdateMessage(it)) },
        placeholder = stringResource(R.string.screen_matcha_reminder_message_hint),
        supportingText = errorText ?: footnote,
        validity = if (errorText != null) TextFieldValidity.Invalid else TextFieldValidity.None,
        maxLines = 4,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            .heightIn(min = 56.dp)
            .semantics { if (errorText != null) error(errorText) },
    )
}

@Composable
private fun WaitingForBot(isChecking: Boolean, onRetry: () -> Unit) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = stringResource(R.string.screen_matcha_reminder_bot_waiting),
                style = ElementTheme.typography.fontBodyLgRegular,
                color = ElementTheme.colors.textPrimary,
            )
        }
        TextButton(
            text = stringResource(CommonStrings.action_retry),
            onClick = onRetry,
            showProgress = isChecking,
            enabled = !isChecking,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun InviteBotStep(
    botState: RoomReminderBotState.CanInvite,
    botUserId: UserId,
    onInvite: () -> Unit,
    onCancel: () -> Unit,
) {
    Column {
        InfoText(stringResource(R.string.screen_matcha_reminder_bot_invite_body, botUserId.value))
        if (botState.hasFailed) ErrorLine(stringResource(R.string.screen_matcha_reminder_bot_invite_error))
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp),
        ) {
            Button(
                text = stringResource(R.string.screen_matcha_reminder_bot_invite_action),
                onClick = onInvite,
                showProgress = botState.isInviting,
                enabled = !botState.isInviting,
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton(
                text = stringResource(CommonStrings.action_cancel),
                onClick = onCancel,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun CustomStep(state: RoomReminderState) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val formats = rememberReminderFormats()
    val today = state.now.toLocalDate()
    val picked = LocalDateTime.of(state.customDate, state.customTime)
    Column {
        OptionRow(
            label = stringResource(R.string.screen_matcha_reminder_date),
            value = formats.date(state.customDate, today),
            enabled = state.sending == null,
            onClick = { showDatePicker = true },
        )
        OptionRow(
            label = stringResource(R.string.screen_matcha_reminder_time),
            value = formats.time.format(state.customTime),
            enabled = state.sending == null,
            onClick = { showTimePicker = true },
        )
        Text(
            text = stringResource(R.string.screen_matcha_reminder_timezone, state.now.zone.id, gmtLabel(state.now.zone.rules.getOffset(picked))),
            style = ElementTheme.typography.fontBodySmRegular,
            color = ElementTheme.colors.textSecondary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        when {
            state.customError != null -> ErrorLine(state.customError.text(state.maxMessageLength))
            state.hasSendFailed -> ErrorLine(stringResource(R.string.screen_matcha_reminder_error_send))
        }
        ConfirmButton(state = state, option = RoomReminderOption.Custom, enabled = state.customError == null)
        if (showDatePicker) {
            ReminderDatePickerDialog(
                date = state.customDate,
                today = today,
                onPick = {
                    showDatePicker = false
                    state.eventSink(RoomReminderEvent.SetDate(it))
                },
                onDismiss = { showDatePicker = false },
            )
        }
        if (showTimePicker) {
            ReminderTimePickerDialog(
                time = state.customTime,
                onPick = {
                    showTimePicker = false
                    state.eventSink(RoomReminderEvent.SetTime(it))
                },
                onDismiss = { showTimePicker = false },
            )
        }
    }
}

/** A 56 dp row with its value at the end; with [selected], one choice of a radio group. */
@Composable
private fun OptionRow(
    label: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit,
    selected: Boolean? = null,
) {
    ListItem(
        modifier = if (selected != null) {
            Modifier.selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
        } else {
            Modifier
        },
        leadingContent = selected?.let { ListItemContent.RadioButton(selected = it) },
        trailingContent = ListItemContent.Custom { Text(text = value, color = ElementTheme.colors.textSecondary, maxLines = 1) },
        enabled = enabled,
        onClick = onClick.takeIf { selected == null },
    ) {
        Text(label)
    }
}

@Composable
private fun InfoText(text: String) {
    Text(
        text = text,
        style = ElementTheme.typography.fontBodyLgRegular,
        color = ElementTheme.colors.textSecondary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun ErrorLine(text: String) {
    Text(
        text = text,
        style = ElementTheme.typography.fontBodyMdRegular,
        color = ElementTheme.colors.textCriticalPrimary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderDatePickerDialog(
    date: LocalDate,
    today: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val lastDay = today.plusDays(MatchaConfig.REMINDER_MAX_DELAY_DAYS.toLong())
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = date.toUtcMillis(),
        yearRange = today.year..lastDay.year,
        selectableDates = remember(today) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis.toUtcDate() in today..lastDay
                override fun isSelectableYear(year: Int) = year in today.year..lastDay.year
            }
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                text = stringResource(CommonStrings.action_done),
                onClick = { pickerState.selectedDateMillis?.let { onPick(it.toUtcDate()) } ?: onDismiss() },
            )
        },
        dismissButton = {
            TextButton(text = stringResource(CommonStrings.action_cancel), onClick = onDismiss)
        },
    ) {
        DatePicker(state = pickerState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimePickerDialog(
    time: LocalTime,
    onPick: (LocalTime) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState = rememberTimePickerState(
        initialHour = time.hour,
        initialMinute = time.minute,
        is24Hour = DateFormat.is24HourFormat(LocalContext.current),
    )
    TimePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                text = stringResource(CommonStrings.action_done),
                onClick = { onPick(LocalTime.of(pickerState.hour, pickerState.minute)) },
            )
        },
        title = { TimePickerDialogDefaults.Title(displayMode = TimePickerDisplayMode.Picker) },
        dismissButton = {
            TextButton(text = stringResource(CommonStrings.action_cancel), onClick = onDismiss)
        },
    ) {
        TimePicker(state = pickerState)
    }
}

@Composable
private fun RoomReminderError.text(maxMessageLength: Int): String = when (this) {
    RoomReminderError.EmptyMessage -> stringResource(R.string.screen_matcha_reminder_error_empty)
    RoomReminderError.MessageTooLong -> stringResource(
        R.string.screen_matcha_reminder_error_too_long,
        NumberFormat.getIntegerInstance(LocalConfiguration.current.locales[0]).format(maxMessageLength),
    )
    RoomReminderError.NotInFuture -> stringResource(R.string.screen_matcha_reminder_error_past)
    RoomReminderError.TooFar -> stringResource(R.string.screen_matcha_reminder_error_too_far)
    RoomReminderError.NonexistentTime -> stringResource(R.string.screen_matcha_reminder_error_dst)
}

/** Short, locale-aware date and time labels in the WhatsApp style: "Today 14:05", "Tue 17:00", "Tue 3 Nov". */
private class ReminderFormats(locale: Locale, is24Hour: Boolean) {
    private val timeSkeleton = if (is24Hour) "Hmm" else "hmma"
    private val pattern = { skeleton: String -> DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, skeleton), locale) }
    val time: DateTimeFormatter = pattern(timeSkeleton)
    private val dayMonth = pattern("EEEdMMM")
    private val dayMonthYear = pattern("EEEdMMMy")
    private val weekdayTime = pattern("EEE$timeSkeleton")
    private val dayMonthTime = pattern("EEEdMMM$timeSkeleton")
    private val dayMonthYearTime = pattern("EEEdMMMy$timeSkeleton")

    fun date(date: LocalDate, today: LocalDate): String = (if (date.year == today.year) dayMonth else dayMonthYear).format(date)

    @Composable
    fun moment(moment: LocalDateTime, today: LocalDate): String {
        val date = moment.toLocalDate()
        return when {
            date == today -> stringResource(R.string.screen_matcha_reminder_today_at, time.format(moment))
            date == today.plusDays(1) -> stringResource(R.string.screen_matcha_reminder_tomorrow_at, time.format(moment))
            date < today.plusDays(7) -> weekdayTime.format(moment)
            date.year == today.year -> dayMonthTime.format(moment)
            else -> dayMonthYearTime.format(moment)
        }
    }
}

@Composable
private fun rememberReminderFormats(): ReminderFormats {
    val locale = LocalConfiguration.current.locales[0]
    val is24Hour = DateFormat.is24HourFormat(LocalContext.current)
    return remember(locale, is24Hour) { ReminderFormats(locale, is24Hour) }
}

/** "GMT+7", "GMT+5:30", "GMT-3" or "GMT". */
private fun gmtLabel(offset: ZoneOffset): String {
    val seconds = offset.totalSeconds
    if (seconds == 0) return "GMT"
    val sign = if (seconds < 0) "-" else "+"
    val hours = seconds.absoluteValue / 3600
    val minutes = seconds.absoluteValue % 3600 / 60
    return if (minutes == 0) "GMT$sign$hours" else "GMT$sign$hours:" + minutes.toString().padStart(2, '0')
}

/** The material date picker works in UTC midnights. */
private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toUtcDate(): LocalDate = Instant.ofEpochMilli(this).atOffset(ZoneOffset.UTC).toLocalDate()

@PreviewsDayNight
@Composable
internal fun RoomReminderContentPreview(@PreviewParameter(RoomReminderStatePreviewParam::class) state: RoomReminderState) = ElementPreview {
    RoomReminderContent(state = state, onClose = {})
}
