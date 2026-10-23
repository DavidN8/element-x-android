/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.chatbar

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.matcha.api.MatchaChatBarSelection
import io.element.android.features.matcha.impl.R
import io.element.android.features.matcha.impl.store.MatchaWarning
import io.element.android.features.matcha.impl.tasks.TaskEditorSheet
import io.element.android.libraries.designsystem.components.PillFilterChip
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.DropdownMenu
import io.element.android.libraries.designsystem.theme.components.DropdownMenuItem
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.noticePillBackground
import io.element.android.libraries.designsystem.theme.noticePillText
import io.element.android.libraries.designsystem.utils.hasCompactHeightWindowSize
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.libraries.designsystem.R as DesignSystemR

/** The chip bar under a chat's top bar, its warning strip, and its sheets. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChatBarView(
    state: ChatBarState,
    modifier: Modifier = Modifier,
) {
    // The bar gives way to the content on a short window, and to the keyboard on a narrow one.
    val windowWidth = with(LocalDensity.current) { LocalWindowInfo.current.containerSize.width.toDp() }
    val isHiddenByWindow = hasCompactHeightWindowSize() || WindowInsets.isImeVisible && windowWidth < COMPACT_WIDTH
    if (state.showBar && !isHiddenByWindow) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .background(ElementTheme.colors.bgCanvasDefault)
        ) {
            ChipRow(state)
            state.warning?.let { WarningStrip(stringResource(it.messageResId())) }
        }
    }
    when (val sheet = state.sheet) {
        is ChatBarSheet.ThreadName -> ThreadNameSheet(sheet = sheet, eventSink = state.eventSink)
        is ChatBarSheet.ThreadActions -> ThreadActionsSheet(
            thread = sheet.thread,
            canManage = state.canManageThreads,
            canRemind = state.canRemind,
            eventSink = state.eventSink,
        )
        null -> Unit
    }
    state.editor?.let { TaskEditorSheet(it) }
}

private val COMPACT_WIDTH = 600.dp

private fun MatchaWarning.messageResId() = when (this) {
    MatchaWarning.Undecryptable -> R.string.screen_matcha_threads_warning_undecryptable
    MatchaWarning.HistoryUnavailable -> R.string.screen_matcha_threads_warning_history
    MatchaWarning.NoAccess -> R.string.screen_matcha_threads_warning_access
    MatchaWarning.Network -> R.string.screen_matcha_threads_warning_network
}

@Composable
private fun ChipRow(state: ChatBarState) {
    val eventSink = state.eventSink
    val listState = rememberLazyListState()
    val selectedIndex = when (val selection = state.selection) {
        MatchaChatBarSelection.General -> 0
        MatchaChatBarSelection.Tasks -> 1
        is MatchaChatBarSelection.Thread -> state.threads.indexOfFirst { it.id == selection.threadId }.takeIf { it >= 0 }?.plus(2)
    }
    LaunchedEffect(selectedIndex) {
        if (selectedIndex == null) return@LaunchedEffect
        val layout = listState.layoutInfo
        val item = layout.visibleItemsInfo.firstOrNull { it.index == selectedIndex }
        val isFullyVisible = item != null && item.offset >= layout.viewportStartOffset && item.offset + item.size <= layout.viewportEndOffset
        if (!isFullyVisible) listState.animateScrollToItem(selectedIndex)
    }
    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .selectableGroup(),
    ) {
        item(key = "general") {
            PillFilterChip(
                label = stringResource(R.string.screen_matcha_threads_general),
                selected = state.selection == MatchaChatBarSelection.General,
                onClick = { eventSink(ChatBarEvent.Select(MatchaChatBarSelection.General)) },
            )
        }
        item(key = "tasks") {
            val label = stringResource(CommonStrings.common_matcha_tasks)
            val count = state.openTaskCount
            val description = if (count > 0) "$label, ${pluralStringResource(R.plurals.a11y_matcha_open_tasks, count, count)}" else null
            PillFilterChip(
                label = label,
                selected = state.selection == MatchaChatBarSelection.Tasks,
                trailingText = count.takeIf { it > 0 }?.toString(),
                onClick = { eventSink(ChatBarEvent.Select(MatchaChatBarSelection.Tasks)) },
                modifier = if (description != null) Modifier.semantics { contentDescription = description } else Modifier,
            )
        }
        items(state.threads.size, key = { state.threads[it].id.value }) { index ->
            ThreadChip(
                thread = state.threads[index],
                selected = state.selection == MatchaChatBarSelection.Thread(state.threads[index].id),
                eventSink = eventSink,
            )
        }
        if (state.canCreateThread) {
            item(key = "new") {
                PillFilterChip(
                    label = stringResource(CommonStrings.common_matcha_new_thread),
                    selected = false,
                    leadingIcon = CompoundIcons.Plus(),
                    onClick = { eventSink(ChatBarEvent.NewThread) },
                )
            }
        }
        item(key = "all") {
            PillFilterChip(
                label = stringResource(CommonStrings.common_matcha_all_threads),
                selected = false,
                leadingIcon = CompoundIcons.Threads(),
                onClick = { eventSink(ChatBarEvent.OpenAllThreads) },
            )
        }
        if (state.showMore) {
            item(key = "more") {
                MoreChip(state)
            }
        }
    }
}

@Composable
private fun ThreadChip(
    thread: ChatBarThread,
    selected: Boolean,
    eventSink: (ChatBarEvent) -> Unit,
) {
    val name = thread.name ?: stringResource(CommonStrings.common_matcha_thread_untitled)
    val archivedDescription = stringResource(R.string.a11y_matcha_thread_archived)
    PillFilterChip(
        label = name,
        selected = selected,
        leadingIcon = if (thread.isArchived) ImageVector.vectorResource(DesignSystemR.drawable.ic_matcha_archive) else null,
        maxLabelWidth = 160.dp,
        onClick = { eventSink(ChatBarEvent.Select(MatchaChatBarSelection.Thread(thread.id))) },
        onLongClick = { eventSink(ChatBarEvent.ShowThreadActions(thread.id)) },
        modifier = if (thread.isArchived) Modifier.semantics { contentDescription = "$name, $archivedDescription" } else Modifier,
    )
}

@Composable
private fun MoreChip(state: ChatBarState) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        PillFilterChip(
            label = null,
            selected = false,
            leadingIcon = CompoundIcons.OverflowHorizontal(),
            contentDescription = stringResource(CommonStrings.common_matcha_more),
            onClick = { expanded = true },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            if (state.canRemind) {
                MoreMenuItem(
                    text = stringResource(CommonStrings.action_matcha_remind_me),
                    icon = CompoundIcons.Notifications(),
                    onClick = {
                        expanded = false
                        state.eventSink(ChatBarEvent.RemindMe(threadId = null))
                    },
                )
            }
            if (state.canOpenSpaceDocs) {
                MoreMenuItem(
                    text = stringResource(CommonStrings.common_matcha_space_docs),
                    icon = CompoundIcons.Document(),
                    onClick = {
                        expanded = false
                        state.eventSink(ChatBarEvent.OpenSpaceDocs)
                    },
                )
            }
        }
    }
}

@Composable
private fun MoreMenuItem(text: String, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(text = text) },
        leadingIcon = { Icon(imageVector = icon, contentDescription = null) },
        onClick = onClick,
        modifier = Modifier.heightIn(min = 48.dp),
    )
}

/** A notice strip under a bar, shared with the task board. */
@Composable
internal fun WarningStrip(text: String, modifier: Modifier = Modifier, maxLines: Int = 1) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 32.dp)
            .background(ElementTheme.colors.noticePillBackground)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = CompoundIcons.Warning(),
            contentDescription = null,
            tint = ElementTheme.colors.noticePillText,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = text,
            style = ElementTheme.typography.fontBodySmRegular,
            color = ElementTheme.colors.noticePillText,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@PreviewsDayNight
@Composable
internal fun ChatBarViewPreview(@PreviewParameter(ChatBarStatePreviewParam::class) state: ChatBarState) = ElementPreview {
    ChatBarView(state = state)
}
