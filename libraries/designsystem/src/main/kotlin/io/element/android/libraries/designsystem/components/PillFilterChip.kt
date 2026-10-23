/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.chipOutline
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Surface
import io.element.android.libraries.designsystem.theme.components.Text

/**
 * The WhatsApp-style filter chip shared by the chat list, the chat chip bar, search and the Tasks tab:
 * a 32dp pill, tonal green when selected, outlined when not, no checkmark.
 *
 * @param label the chip text, or null for an icon-only chip (then [contentDescription] is required).
 * @param selected whether the chip is the selected one; announced as a selected state.
 * @param onClick called when the chip is tapped.
 * @param modifier the [Modifier] to apply to the chip.
 * @param leadingIcon an optional 18dp icon before the label.
 * @param trailingText optional trailing text, e.g. a count.
 * @param contentDescription the accessibility label, required for an icon-only chip.
 * @param maxLabelWidth caps long labels (thread names) with an ellipsis.
 * @param onLongClick optional long-press action, e.g. a thread's actions.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PillFilterChip(
    label: String?,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
    trailingText: String? = null,
    contentDescription: String? = null,
    maxLabelWidth: Dp = Dp.Unspecified,
    onLongClick: (() -> Unit)? = null,
) {
    val container = animateColorAsState(
        targetValue = if (selected) ElementTheme.colors.bgAccentSubtle else Color.Transparent,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "chip container",
    )
    val content = animateColorAsState(
        targetValue = if (selected) ElementTheme.colors.textBadgeAccent else ElementTheme.colors.textSecondary,
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "chip content",
    )
    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .height(32.dp)
            .clip(CircleShape)
            .semantics { this.selected = selected }
            .combinedClickable(
                role = Role.Tab,
                onClick = onClick,
                onLongClick = onLongClick,
                onClickLabel = contentDescription,
            ),
        shape = CircleShape,
        color = container.value,
        border = if (selected) null else BorderStroke(1.dp, ElementTheme.colors.chipOutline),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = if (label == null) 8.dp else 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = if (label == null) contentDescription else null,
                    tint = content.value,
                    modifier = Modifier.size(18.dp),
                )
            }
            if (label != null) {
                Text(
                    text = label,
                    style = ElementTheme.typography.fontBodyMdMedium,
                    color = content.value,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = if (maxLabelWidth != Dp.Unspecified) Modifier.widthIn(max = maxLabelWidth) else Modifier,
                )
            }
            if (trailingText != null) {
                Text(
                    text = trailingText,
                    style = ElementTheme.typography.fontBodyMdMedium,
                    color = content.value,
                    maxLines = 1,
                )
            }
        }
    }
}

@PreviewsDayNight
@Composable
internal fun PillFilterChipPreview() = ElementPreview {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(16.dp)) {
        PillFilterChip(label = "All", selected = true, onClick = {})
        PillFilterChip(label = "Tasks", selected = false, trailingText = "3", onClick = {})
        PillFilterChip(label = "New thread", selected = false, leadingIcon = CompoundIcons.Plus(), onClick = {})
        PillFilterChip(label = null, selected = false, leadingIcon = CompoundIcons.OverflowHorizontal(), contentDescription = "More", onClick = {})
    }
}
