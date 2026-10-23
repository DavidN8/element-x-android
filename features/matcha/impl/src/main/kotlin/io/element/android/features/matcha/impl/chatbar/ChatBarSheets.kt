/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.chatbar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.error
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.appconfig.MatchaConfig
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.matcha.impl.R
import io.element.android.libraries.designsystem.components.list.ListItemContent
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.IconSource
import io.element.android.libraries.designsystem.theme.components.ListItem
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.designsystem.theme.components.TextFieldValidity
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.libraries.designsystem.R as DesignSystemR

/** The counter appears once a name gets close to the limit. */
private const val NAME_COUNTER_FROM = 80

/** New thread, or Rename thread when [ChatBarSheet.ThreadName.threadId] is set. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThreadNameSheet(
    sheet: ChatBarSheet.ThreadName,
    eventSink: (ChatBarEvent) -> Unit,
) {
    val isRename = sheet.threadId != null
    val title = stringResource(if (isRename) R.string.screen_matcha_threads_rename_title else R.string.screen_matcha_threads_new)
    ModalBottomSheet(
        onDismissRequest = { eventSink(ChatBarEvent.DismissSheet) },
        scrollable = true,
        modifier = Modifier.semantics { paneTitle = title },
    ) {
        val errorText = when (sheet.error) {
            ThreadNameError.Empty -> stringResource(R.string.common_matcha_error_name_empty)
            ThreadNameError.TooLong -> stringResource(R.string.common_matcha_error_max_chars, MatchaConfig.THREAD_NAME_MAX_LENGTH.toString())
            ThreadNameError.CreateFailed -> stringResource(R.string.screen_matcha_threads_create_failed)
            ThreadNameError.RenameFailed -> stringResource(R.string.screen_matcha_threads_update_failed)
            null -> null
        }
        val focusRequester = remember { FocusRequester() }
        if (!LocalInspectionMode.current) {
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SheetTitle(title)
            TextField(
                value = sheet.name,
                onValueChange = { eventSink(ChatBarEvent.UpdateThreadName(it)) },
                placeholder = stringResource(R.string.screen_matcha_threads_name_hint),
                supportingText = errorText ?: stringResource(R.string.screen_matcha_threads_name_helper),
                validity = if (errorText != null) TextFieldValidity.Invalid else TextFieldValidity.None,
                enabled = !sheet.isSending,
                singleLine = true,
                trailingIcon = if (sheet.name.length >= NAME_COUNTER_FROM) {
                    {
                        Text(
                            text = "${sheet.name.length}/${MatchaConfig.THREAD_NAME_MAX_LENGTH}",
                            style = ElementTheme.typography.fontBodySmRegular,
                            color = if (sheet.error == ThreadNameError.TooLong) ElementTheme.colors.textCriticalPrimary else ElementTheme.colors.textSecondary,
                        )
                    }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { eventSink(ChatBarEvent.SubmitThreadName) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .semantics { if (errorText != null) error(errorText) },
            )
            Button(
                text = stringResource(if (isRename) CommonStrings.action_save else R.string.screen_matcha_threads_create),
                onClick = { eventSink(ChatBarEvent.SubmitThreadName) },
                showProgress = sheet.isSending,
                enabled = !sheet.isSending,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** A thread chip's long-press actions; without [canManage] only Remind me and View task are offered. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThreadActionsSheet(
    thread: ChatBarThread,
    canManage: Boolean,
    canRemind: Boolean,
    eventSink: (ChatBarEvent) -> Unit,
) {
    val title = thread.name ?: stringResource(R.string.screen_matcha_threads_fallback_name)
    ModalBottomSheet(
        onDismissRequest = { eventSink(ChatBarEvent.DismissSheet) },
        scrollable = true,
        modifier = Modifier.semantics { paneTitle = title },
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
            SheetTitle(
                text = title,
                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
            )
            if (canManage) {
                ActionRow(
                    text = stringResource(R.string.screen_matcha_threads_rename_title),
                    icon = CompoundIcons.Edit(),
                    onClick = { eventSink(ChatBarEvent.RenameThread(thread.id)) },
                )
                ActionRow(
                    text = stringResource(if (thread.isArchived) R.string.action_matcha_unarchive else R.string.action_matcha_archive),
                    icon = ImageVector.vectorResource(DesignSystemR.drawable.ic_matcha_archive),
                    onClick = { eventSink(ChatBarEvent.SetArchived(thread.id, archived = !thread.isArchived)) },
                )
            }
            if (canRemind) {
                ActionRow(
                    text = stringResource(R.string.action_matcha_remind_me),
                    icon = CompoundIcons.Notifications(),
                    onClick = { eventSink(ChatBarEvent.RemindMe(thread.id)) },
                )
            }
            ActionRow(
                text = stringResource(R.string.screen_matcha_threads_view_task),
                icon = CompoundIcons.CheckCircle(),
                onClick = { eventSink(ChatBarEvent.ViewTask(thread.id)) },
            )
        }
    }
}

@Composable
private fun SheetTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = ElementTheme.typography.fontHeadingMdRegular,
        color = ElementTheme.colors.textPrimary,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .fillMaxWidth()
            .semantics { heading() },
    )
}

@Composable
private fun ActionRow(text: String, icon: ImageVector, onClick: () -> Unit) {
    ListItem(
        leadingContent = ListItemContent.Icon(IconSource.Vector(icon)),
        onClick = onClick,
    ) {
        Text(text = text)
    }
}
