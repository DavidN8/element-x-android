/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.element.android.compound.tokens.generated.TypographyTokens

/*
 * This file contains aliases for TypographyTokens.
 */

val TypographyTokens.aliasScreenTitle: TextStyle
    get() = fontHeadingSmMedium

val TypographyTokens.aliasButtonText: TextStyle
    get() = fontBodyLgMedium

/** Home tab titles (the app name on Chats, then Tasks and Spaces): 22sp, weight 500. */
val TypographyTokens.aliasAppTitle: TextStyle
    get() = fontHeadingMdRegular.copy(fontWeight = FontWeight.Medium)

/** The chat name in a chat's top bar: 17sp, weight 500. */
val TypographyTokens.aliasChatTitle: TextStyle
    get() = fontBodyLgMedium.copy(fontSize = 17.sp)
