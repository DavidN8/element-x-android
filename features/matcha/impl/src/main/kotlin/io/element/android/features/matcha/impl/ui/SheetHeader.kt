/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.ui.strings.CommonStrings

/**
 * The 56 dp header of the Matcha sheets: a close or back button at the start and the centred title.
 * ponytail: replace with the shared designsystem SheetHeader once the re-skin adds it.
 */
@Composable
internal fun SheetHeader(
    title: String,
    isBack: Boolean,
    onNavigationClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        BackButton(
            onClick = onNavigationClick,
            imageVector = if (isBack) CompoundIcons.ArrowLeft() else CompoundIcons.Close(),
            contentDescription = stringResource(if (isBack) CommonStrings.action_back else CommonStrings.action_close),
            modifier = Modifier.align(Alignment.CenterStart),
        )
        Text(
            text = title,
            style = ElementTheme.typography.fontHeadingMdRegular,
            color = ElementTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 52.dp)
                .semantics { heading() },
        )
    }
}
