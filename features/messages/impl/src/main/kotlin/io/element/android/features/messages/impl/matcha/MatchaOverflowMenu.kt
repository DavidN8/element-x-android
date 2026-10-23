/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.matcha

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.DropdownMenu
import io.element.android.libraries.designsystem.theme.components.DropdownMenuItem
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings

/**
 * The chat's ⋮ while it has no chip bar (WHATSAPP_DESIGN.md §3.3 "Actions"): the chip bar's entries, so the Matcha features
 * can be started in a chat that doesn't use them yet.
 */
@Composable
fun MatchaOverflowMenu(
    canCreateThread: Boolean,
    hasThreads: Boolean,
    canRemind: Boolean,
    canOpenSpaceDocs: Boolean,
    onNewThreadClick: () -> Unit,
    onTasksClick: () -> Unit,
    onAllThreadsClick: () -> Unit,
    onRemindMeClick: () -> Unit,
    onSpaceDocsClick: () -> Unit,
    modifier: Modifier = Modifier,
    expandedByDefault: Boolean = false,
) {
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
            if (canCreateThread) {
                MatchaMenuItem(CommonStrings.common_matcha_new_thread, CompoundIcons.Plus()) { select(onNewThreadClick) }
            }
            MatchaMenuItem(CommonStrings.common_matcha_tasks, CompoundIcons.CheckCircle()) { select(onTasksClick) }
            if (hasThreads) {
                MatchaMenuItem(CommonStrings.common_matcha_all_threads, CompoundIcons.Threads()) { select(onAllThreadsClick) }
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
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        onClick = onClick,
        text = { Text(text = stringResource(titleRes), color = ElementTheme.colors.textPrimary) },
        leadingIcon = { Icon(imageVector = icon, contentDescription = null, tint = ElementTheme.colors.iconSecondary) },
    )
}

@PreviewsDayNight
@Composable
internal fun MatchaOverflowMenuPreview() = ElementPreview {
    MatchaOverflowMenu(
        canCreateThread = true,
        hasThreads = true,
        canRemind = true,
        canOpenSpaceDocs = true,
        onNewThreadClick = {},
        onTasksClick = {},
        onAllThreadsClick = {},
        onRemindMeClick = {},
        onSpaceDocsClick = {},
        expandedByDefault = true,
    )
}
