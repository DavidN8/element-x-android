/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.chatWallpaper
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.composerSurface

private val ComposerSlotPillShape = RoundedCornerShape(24.dp)

/**
 * A 48 dp pill with the composer's metrics, shown in the composer slot instead of the composer when the user cannot write.
 */
@Composable
internal fun ComposerSlotPill(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(6.dp)
            .then(if (ElementTheme.colors.isLight) Modifier.shadow(1.dp, ComposerSlotPillShape) else Modifier)
            .background(ElementTheme.colors.composerSurface, ComposerSlotPillShape)
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        content = content,
    )
}

/**
 * The composer slot while a verification violation blocks sending. The identity change banner above it says why.
 */
@Composable
internal fun DisabledComposerView(
    modifier: Modifier = Modifier,
) {
    ComposerSlotPill(modifier = modifier) {
        Icon(
            modifier = Modifier.size(16.dp),
            imageVector = CompoundIcons.ErrorSolid(),
            contentDescription = null,
            tint = ElementTheme.colors.iconCriticalPrimary,
        )
    }
}

@PreviewsDayNight
@Composable
internal fun DisabledComposerViewPreview() = ElementPreview {
    Column(modifier = Modifier.background(ElementTheme.colors.chatWallpaper)) {
        DisabledComposerView()
    }
}
