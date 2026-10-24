/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.matcha

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.timeline.components.CallMenuItem
import io.element.android.features.roomcall.api.RoomCallState
import io.element.android.features.roomcall.api.aStandByCallState
import io.element.android.libraries.designsystem.atomic.atoms.CounterAtom
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.DropdownMenu
import io.element.android.libraries.designsystem.theme.components.DropdownMenuItem
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonPlurals
import io.element.android.libraries.ui.strings.CommonStrings

/**
 * The chat's top bar actions with the Matcha workspaces (WHATSAPP_DESIGN.md §3.3): All threads, the task board with its open count, and
 * ⋮. Starting a call moves into ⋮; an ongoing call keeps its Join button in the bar, since it can't wait in a menu.
 */
@Composable
internal fun RowScope.MatchaMenuActions(
    openTaskCount: Int,
    roomCallState: RoomCallState,
    canCreateThread: Boolean,
    canRemind: Boolean,
    canOpenSpaceDocs: Boolean,
    onJoinCallClick: (isAudioCall: Boolean) -> Unit,
    onAllThreadsClick: () -> Unit,
    onTasksClick: () -> Unit,
    onNewThreadClick: () -> Unit,
    onRemindMeClick: () -> Unit,
    onSpaceDocsClick: () -> Unit,
) {
    IconButton(onClick = onAllThreadsClick) {
        Icon(
            imageVector = CompoundIcons.Threads(),
            contentDescription = stringResource(CommonStrings.common_matcha_all_threads),
        )
    }
    val tasksLabel = stringResource(CommonStrings.common_matcha_tasks)
    val tasksDescription = if (openTaskCount > 0) {
        "$tasksLabel, ${pluralStringResource(CommonPlurals.a11y_matcha_open_tasks, openTaskCount, openTaskCount)}"
    } else {
        tasksLabel
    }
    Box {
        IconButton(
            onClick = onTasksClick,
            modifier = Modifier.semantics { contentDescription = tasksDescription },
        ) {
            Icon(imageVector = CompoundIcons.CheckCircle(), contentDescription = null)
        }
        // The Chats tab's green count badge, drawn outside the button: its round clip would cut a count of 10 or more.
        if (openTaskCount > 0) {
            CounterAtom(
                count = openTaskCount,
                textStyle = ElementTheme.typography.fontBodySmMedium,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .clearAndSetSemantics {},
            )
        }
    }
    if (roomCallState is RoomCallState.OnGoing) {
        CallMenuItem(roomCallState = roomCallState, onJoinCallClick = onJoinCallClick)
    }
    MatchaOverflowMenu(
        roomCallState = roomCallState,
        canCreateThread = canCreateThread,
        canRemind = canRemind,
        canOpenSpaceDocs = canOpenSpaceDocs,
        onJoinCallClick = onJoinCallClick,
        onNewThreadClick = onNewThreadClick,
        onRemindMeClick = onRemindMeClick,
        onSpaceDocsClick = onSpaceDocsClick,
    )
    Spacer(Modifier.width(8.dp))
}

/**
 * The chat's ⋮: starting a call, with the rules of the call buttons it replaces, then, while the chat has no chip bar, the chip bar's
 * entries, so the Matcha features can be started in a chat that doesn't use them yet. Nothing is drawn when it would be empty.
 */
@Composable
fun MatchaOverflowMenu(
    roomCallState: RoomCallState,
    canCreateThread: Boolean,
    canRemind: Boolean,
    canOpenSpaceDocs: Boolean,
    onJoinCallClick: (isAudioCall: Boolean) -> Unit,
    onNewThreadClick: () -> Unit,
    onRemindMeClick: () -> Unit,
    onSpaceDocsClick: () -> Unit,
    modifier: Modifier = Modifier,
    expandedByDefault: Boolean = false,
) {
    // A call can be started only while none is ongoing; an ongoing one has its Join button in the bar.
    val standByCall = roomCallState as? RoomCallState.StandBy
    val hasChatBarEntries = canCreateThread || canRemind || canOpenSpaceDocs
    if (standByCall == null && !hasChatBarEntries) return
    var isExpanded by remember { mutableStateOf(expandedByDefault) }
    Box(modifier = modifier) {
        IconButton(onClick = { isExpanded = true }) {
            Icon(
                imageVector = CompoundIcons.OverflowVertical(),
                contentDescription = stringResource(CommonStrings.common_matcha_more),
            )
        }
        DropdownMenu(
            expanded = isExpanded,
            onDismissRequest = { isExpanded = false },
        ) {
            fun select(onClick: () -> Unit) {
                isExpanded = false
                onClick()
            }
            if (standByCall != null) {
                // Voice calls are only offered in DMs.
                if (standByCall.isDM) {
                    MatchaMenuItem(CommonStrings.action_voice_call, CompoundIcons.VoiceCall(), enabled = standByCall.canStartCall) {
                        select { onJoinCallClick(true) }
                    }
                }
                MatchaMenuItem(CommonStrings.action_video_call, CompoundIcons.VideoCall(), enabled = standByCall.canStartCall) {
                    select { onJoinCallClick(false) }
                }
            }
            if (canCreateThread) {
                MatchaMenuItem(CommonStrings.common_matcha_new_thread, CompoundIcons.Plus()) { select(onNewThreadClick) }
            }
            if (canRemind) {
                MatchaMenuItem(CommonStrings.action_matcha_remind_me, CompoundIcons.Notifications()) { select(onRemindMeClick) }
            }
            if (canOpenSpaceDocs) {
                MatchaMenuItem(CommonStrings.common_matcha_space_docs, CompoundIcons.Document()) { select(onSpaceDocsClick) }
            }
        }
    }
}

@Composable
private fun MatchaMenuItem(
    @StringRes titleRes: Int,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        onClick = onClick,
        enabled = enabled,
        text = {
            Text(text = stringResource(titleRes), color = if (enabled) ElementTheme.colors.textPrimary else ElementTheme.colors.textDisabled)
        },
        leadingIcon = {
            Icon(imageVector = icon, contentDescription = null, tint = if (enabled) ElementTheme.colors.iconSecondary else ElementTheme.colors.iconDisabled)
        },
    )
}

@PreviewsDayNight
@Composable
internal fun MatchaOverflowMenuPreview() = ElementPreview {
    MatchaOverflowMenu(
        roomCallState = aStandByCallState(isDM = true),
        canCreateThread = true,
        canRemind = true,
        canOpenSpaceDocs = true,
        onJoinCallClick = {},
        onNewThreadClick = {},
        onRemindMeClick = {},
        onSpaceDocsClick = {},
        expandedByDefault = true,
    )
}
