/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import io.element.android.compound.annotations.CoreColorToken
import io.element.android.compound.previews.ColorListPreview
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.SemanticColors
import io.element.android.compound.tokens.generated.internal.DarkColorTokens
import io.element.android.compound.tokens.generated.internal.LightColorTokens
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import kotlinx.collections.immutable.persistentMapOf

/**
 * Room list.
 */
val SemanticColors.roomListRoomName
    get() = textPrimary

val SemanticColors.roomListRoomMessage
    get() = textSecondary

val SemanticColors.roomListRoomMessageDate
    get() = textSecondary

val SemanticColors.placeholderBackground
    get() = bgSubtleSecondary

// Matcha chat surfaces (WhatsApp-style): your own bubble is tinted green, others are on the plain surface.
val SemanticColors.messageFromMeBackground
    get() = if (isLight) Color(0xFFD9FDD3) else Color(0xFF144D37)

val SemanticColors.messageFromOtherBackground
    get() = if (isLight) bgCanvasDefault else bgSubtleSecondary

/** Plain wallpaper behind the chat and thread timelines. */
val SemanticColors.chatWallpaper
    get() = if (isLight) Color(0xFFF5F1EB) else bgCanvasDefault

/** Time, "Edited" and ticks inside a bubble. */
val SemanticColors.bubbleMetaText
    get() = if (isLight) Color(0x99000000) else Color(0x99FFFFFF)

/** All other secondary text inside a bubble: quote bodies, reply counts, file sizes, removed and undecryptable messages. */
val SemanticColors.bubbleSecondaryText
    get() = if (isLight) Color(0x99000000) else Color(0xB3FFFFFF)

/** The double tick once a message has been read. */
val SemanticColors.readTick
    get() = if (isLight) Color(0xFF007BFC) else Color(0xFF53BDEB)

/** Day separators, the floating date, system events and the unread band. */
val SemanticColors.systemPillBackground
    get() = if (isLight) Color(0xE6FFFFFF) else bgSubtleSecondary

/** Encryption and beginning-of-chat notices, and warning strips. */
val SemanticColors.noticePillBackground
    get() = if (isLight) Color(0xFFFFF0D4) else bgSubtleSecondary

val SemanticColors.noticePillText
    get() = if (isLight) Color(0x99000000) else Color(0xFFFFD279)

/** The composer pill, the scroll-to-bottom button and the reaction pill. */
val SemanticColors.composerSurface
    get() = if (isLight) bgCanvasDefault else bgSubtleSecondary

/** Reply quotes, link previews and file blocks inside bubbles. */
val SemanticColors.quoteOverlay
    get() = if (isLight) Color(0x0D000000) else Color(0x0DFFFFFF)

/** Bottom sheets, menus, dialogs and the dark navigation bar. */
val SemanticColors.raisedSurface
    get() = bgCanvasDefaultLevel1

/** The open chat in the two-pane list, and pressed rows. */
val SemanticColors.rowSelected
    get() = if (isLight) Color(0x26C2BDB8) else Color(0x1AFFFFFF)

/** The border of an unselected chip. */
val SemanticColors.chipOutline
    get() = if (isLight) Color(0x33111B21) else Color(0x1AFFFFFF)

/** The unread count of a muted chat; its digits use textSecondary. */
val SemanticColors.mutedBadge
    get() = if (isLight) Color(0x0D000000) else Color(0x1AFFFFFF)

/** The neutral tint behind the message the user jumped to. */
val SemanticColors.focusHighlight
    get() = if (isLight) Color(0x1F0A1014) else Color(0x1FFFFFFF)

// This color is not present in Semantic color, so put hard-coded value for now
@OptIn(CoreColorToken::class)
val SemanticColors.progressIndicatorTrackColor
    get() = if (isLight) LightColorTokens.colorAlphaGray500 else DarkColorTokens.colorAlphaGray500

// Temporary color, which is not in the token right now
val SemanticColors.temporaryColorBgSpecial
    get() = if (isLight) Color(0xFFE4E8F0) else Color(0xFF3A4048)

// This color is not present in Semantic color, so put hard-coded value for now
@OptIn(CoreColorToken::class)
val SemanticColors.pinDigitBg
    get() = if (isLight) LightColorTokens.colorGray300 else DarkColorTokens.colorGray400

@OptIn(CoreColorToken::class)
val SemanticColors.pinnedMessageBannerIndicator
    get() = if (isLight) LightColorTokens.colorAlphaGray600 else DarkColorTokens.colorAlphaGray600

@OptIn(CoreColorToken::class)
val SemanticColors.pinnedMessageBannerBorder
    get() = if (isLight) LightColorTokens.colorAlphaGray400 else DarkColorTokens.colorAlphaGray400

val SemanticColors.floatingDateBadgeBackground
    get() = systemPillBackground

@PreviewsDayNight
@Composable
internal fun ColorAliasesPreview() = ElementPreview {
    ColorListPreview(
        backgroundColor = Color.Black,
        foregroundColor = Color.White,
        colors = persistentMapOf(
            "roomListRoomName" to ElementTheme.colors.roomListRoomName,
            "roomListRoomMessage" to ElementTheme.colors.roomListRoomMessage,
            "roomListRoomMessageDate" to ElementTheme.colors.roomListRoomMessageDate,
            "placeholderBackground" to ElementTheme.colors.placeholderBackground,
            "messageFromMeBackground" to ElementTheme.colors.messageFromMeBackground,
            "messageFromOtherBackground" to ElementTheme.colors.messageFromOtherBackground,
            "progressIndicatorTrackColor" to ElementTheme.colors.progressIndicatorTrackColor,
            "temporaryColorBgSpecial" to ElementTheme.colors.temporaryColorBgSpecial,
            "chatWallpaper" to ElementTheme.colors.chatWallpaper,
            "bubbleMetaText" to ElementTheme.colors.bubbleMetaText,
            "bubbleSecondaryText" to ElementTheme.colors.bubbleSecondaryText,
            "readTick" to ElementTheme.colors.readTick,
            "systemPillBackground" to ElementTheme.colors.systemPillBackground,
            "noticePillBackground" to ElementTheme.colors.noticePillBackground,
            "noticePillText" to ElementTheme.colors.noticePillText,
            "composerSurface" to ElementTheme.colors.composerSurface,
            "quoteOverlay" to ElementTheme.colors.quoteOverlay,
            "raisedSurface" to ElementTheme.colors.raisedSurface,
            "rowSelected" to ElementTheme.colors.rowSelected,
            "chipOutline" to ElementTheme.colors.chipOutline,
            "mutedBadge" to ElementTheme.colors.mutedBadge,
            "focusHighlight" to ElementTheme.colors.focusHighlight,
        )
    )
}
