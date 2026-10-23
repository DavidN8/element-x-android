/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.compound.colors

import androidx.compose.ui.graphics.Color
import io.element.android.compound.tokens.generated.SemanticColors
import io.element.android.compound.tokens.generated.compoundColorsDark
import io.element.android.compound.tokens.generated.compoundColorsLight

/**
 * The Matcha palette: Compound's semantic structure with WhatsApp-style values.
 * Neutral, flat surfaces; green only for the primary action, unread state, selection and links to actions.
 * See WHATSAPP_DESIGN.md for the role of each value.
 */
object MatchaPalette {
    // Greens
    val green = Color(0xFF1DAA61)
    val greenHovered = Color(0xFF1A9A58)
    val greenPressed = Color(0xFF178C50)
    val greenDark = Color(0xFF21C063)
    val greenDarkHovered = Color(0xFF1EB05B)
    val greenDarkPressed = Color(0xFF1C9F52)
    val greenText = Color(0xFF1B8755)
    val greenTextHovered = Color(0xFF187A4C)
    val greenTextPressed = Color(0xFF156B43)
    val greenPositiveDark = Color(0xFF71EB85)
    val greenDeep = Color(0xFF15603E)
    val greenTint = Color(0xFFD9FDD3)
    val greenTintDark = Color(0xFF103529)

    // Light neutrals
    val ink = Color(0xFF0A1014)
    val inkSecondary = Color(0xFF5B6368)
    val inkTertiary = Color(0xFF8D9599)
    val inkDisabled = Color(0xFFB3B9BD)
    val paper = Color(0xFFFFFFFF)
    val paperRaised = Color(0xFFF7F5F3)
    val paperPressed = Color(0xFFEFECE9)
    val paperSubtle = Color(0xFFFAF9F8)
    val lineLight = Color(0xFFE7E8E9)
    val dividerLight = Color(0x1A111B21)

    // Dark neutrals
    val night = Color(0xFF0A1014)
    val nightRaised = Color(0xFF12181C)
    val nightElevated = Color(0xFF20272B)
    val nightPressed = Color(0xFF2A3236)
    val snow = Color(0xFFF7F8FA)
    val snowSecondary = Color(0xFF8D9599)
    val snowTertiary = Color(0xFF6B7478)
    val snowDisabled = Color(0xFF3E474D)
    val lineDark = Color(0xFF22292D)
    val dividerDark = Color(0x1AFFFFFF)

    // Feedback
    val red = Color(0xFFEA0038)
    val redDark = Color(0xFFFB5061)
    val redTint = Color(0xFFFFE5EA)
    val redTintDark = Color(0xFF3A1820)
    val amber = Color(0xFFB37A00)
    val amberDark = Color(0xFFFFD279)
    val blueLink = Color(0xFF027EB5)
    val blueLinkDark = Color(0xFF53BDEB)
    val blueTint = Color(0xFFD2E8FE)
    val blueTintDark = Color(0xFF092642)
}

/** The Matcha light palette. */
val matchaColorsLight: SemanticColors = with(MatchaPalette) {
    compoundColorsLight.copy(
        // Accent: FAB, badges, unread timestamps, selection.
        bgAccentRest = green,
        bgAccentHovered = greenHovered,
        bgAccentPressed = greenPressed,
        bgAccentSelected = greenTint,
        bgAccentSubtle = greenTint,
        // Primary buttons are green, in the darker green so white labels stay readable.
        bgActionPrimaryRest = greenText,
        bgActionPrimaryHovered = greenTextHovered,
        bgActionPrimaryPressed = greenTextPressed,
        bgActionPrimaryDisabled = inkDisabled,
        bgActionSecondaryRest = paper,
        bgActionTertiaryRest = paper,
        bgActionTertiaryHovered = paperRaised,
        bgActionTertiarySelected = greenTint,
        bgBadgeAccent = greenTint,
        bgBadgePrimary = ink,
        bgBadgeSecondary = paperPressed,
        bgCanvasDefault = paper,
        bgCanvasDefaultLevel1 = paper,
        bgCanvasDisabled = paperRaised,
        bgCriticalPrimary = red,
        bgCriticalHovered = Color(0xFFC4002F),
        bgCriticalSubtle = redTint,
        bgCriticalSubtleHovered = Color(0xFFFFCCD6),
        // Avatar placeholders: WhatsApp's soft tint / strong ink pairs.
        bgDecorative1 = greenTint,
        bgDecorative2 = blueTint,
        bgDecorative3 = Color(0xFFFFDAE7),
        bgDecorative4 = Color(0xFFE8E0FF),
        bgDecorative5 = Color(0xFFFEE2D8),
        bgDecorative6 = Color(0xFFCBF2EE),
        textDecorative1 = greenText,
        textDecorative2 = Color(0xFF0063CB),
        textDecorative3 = Color(0xFFD42A66),
        textDecorative4 = Color(0xFF5E47DE),
        textDecorative5 = Color(0xFFC4532D),
        textDecorative6 = Color(0xFF028377),
        bgInfoSubtle = blueTint,
        bgSubtlePrimary = paperPressed,
        bgSubtleSecondary = paperRaised,
        bgSubtleSecondaryLevel0 = paperRaised,
        bgSubtleTertiary = paperSubtle,
        bgSuccessRest = green,
        bgSuccessHovered = greenHovered,
        bgSuccessPressed = greenPressed,
        bgSuccessSubtle = greenTint,
        borderAccentPrimary = green,
        borderAccentSubtle = greenTint,
        borderCriticalPrimary = red,
        borderDisabled = lineLight,
        borderFocused = green,
        borderInteractivePrimary = inkTertiary,
        borderInteractiveSecondary = lineLight,
        borderInteractiveHovered = inkSecondary,
        borderSuccessPrimary = green,
        borderSuccessSubtle = greenTint,
        // Flat headers: no coloured gradient wash.
        gradientSubtleStop1 = Color.Transparent,
        gradientSubtleStop2 = Color.Transparent,
        gradientSubtleStop3 = Color.Transparent,
        gradientSubtleStop4 = Color.Transparent,
        gradientSubtleStop5 = Color.Transparent,
        gradientSubtleStop6 = Color.Transparent,
        gradientActionStop1 = green,
        gradientActionStop2 = green,
        gradientActionStop3 = greenText,
        gradientActionStop4 = greenDeep,
        // Badges carry digits, so they use the text green (white on it is 4.5:1).
        iconAccentPrimary = greenText,
        iconAccentTertiary = green,
        iconCriticalPrimary = red,
        iconDisabled = inkDisabled,
        iconOnSolidPrimary = paper,
        iconPrimary = ink,
        iconPrimaryAlpha = ink,
        iconSecondary = inkSecondary,
        iconSecondaryAlpha = inkSecondary,
        iconTertiary = inkTertiary,
        iconTertiaryAlpha = inkTertiary,
        iconQuaternary = inkDisabled,
        iconQuaternaryAlpha = inkDisabled,
        iconSuccessPrimary = green,
        iconWarningPrimary = amber,
        separatorPrimary = dividerLight,
        separatorSecondary = dividerLight,
        textActionAccent = greenText,
        textActionPrimary = ink,
        textActionSuccess = greenText,
        textBadgeAccent = greenDeep,
        textCriticalPrimary = red,
        textDisabled = inkDisabled,
        textLinkExternal = blueLink,
        textOnSolidPrimary = paper,
        textPrimary = ink,
        textSecondary = inkSecondary,
        textSuccessPrimary = greenText,
        textWarningPrimary = amber,
    )
}

/** The Matcha dark palette: one step darker, raised surfaces step up in tone instead of casting shadows. */
val matchaColorsDark: SemanticColors = with(MatchaPalette) {
    compoundColorsDark.copy(
        bgAccentRest = greenDark,
        bgAccentHovered = greenDarkHovered,
        bgAccentPressed = greenDarkPressed,
        bgAccentSelected = greenTintDark,
        bgAccentSubtle = greenTintDark,
        bgActionPrimaryRest = greenDark,
        bgActionPrimaryHovered = greenDarkHovered,
        bgActionPrimaryPressed = greenDarkPressed,
        bgActionPrimaryDisabled = snowDisabled,
        bgActionSecondaryRest = night,
        bgActionTertiaryRest = night,
        bgActionTertiaryHovered = nightRaised,
        bgActionTertiarySelected = greenTintDark,
        bgBadgeAccent = greenTintDark,
        bgBadgePrimary = snow,
        bgBadgeSecondary = nightPressed,
        bgCanvasDefault = night,
        bgCanvasDefaultLevel1 = night,
        bgCanvasDisabled = nightRaised,
        bgCriticalPrimary = redDark,
        bgCriticalHovered = red,
        bgCriticalSubtle = redTintDark,
        bgCriticalSubtleHovered = Color(0xFF4A1F29),
        bgDecorative1 = greenTintDark,
        bgDecorative2 = blueTintDark,
        bgDecorative3 = Color(0xFF36192A),
        bgDecorative4 = Color(0xFF242447),
        bgDecorative5 = Color(0xFF35221E),
        bgDecorative6 = Color(0xFF092D2F),
        textDecorative1 = Color(0xFF25D366),
        textDecorative2 = Color(0xFF53A6FD),
        textDecorative3 = Color(0xFFFF72A1),
        textDecorative4 = Color(0xFFA791FF),
        textDecorative5 = Color(0xFFFC9775),
        textDecorative6 = Color(0xFF42C7B8),
        bgInfoSubtle = blueTintDark,
        bgSubtlePrimary = nightPressed,
        bgSubtleSecondary = nightElevated,
        bgSubtleSecondaryLevel0 = nightElevated,
        bgSubtleTertiary = nightRaised,
        bgSuccessRest = greenDark,
        bgSuccessHovered = greenDarkHovered,
        bgSuccessPressed = greenDarkPressed,
        bgSuccessSubtle = greenTintDark,
        borderAccentPrimary = greenDark,
        borderAccentSubtle = greenTintDark,
        borderCriticalPrimary = redDark,
        borderDisabled = lineDark,
        borderFocused = greenDark,
        borderInteractivePrimary = snowTertiary,
        borderInteractiveSecondary = lineDark,
        borderInteractiveHovered = snowSecondary,
        borderSuccessPrimary = greenDark,
        borderSuccessSubtle = greenTintDark,
        gradientSubtleStop1 = Color.Transparent,
        gradientSubtleStop2 = Color.Transparent,
        gradientSubtleStop3 = Color.Transparent,
        gradientSubtleStop4 = Color.Transparent,
        gradientSubtleStop5 = Color.Transparent,
        gradientSubtleStop6 = Color.Transparent,
        gradientActionStop1 = greenDark,
        gradientActionStop2 = greenDark,
        gradientActionStop3 = green,
        gradientActionStop4 = greenText,
        iconAccentPrimary = greenDark,
        iconAccentTertiary = greenDark,
        iconCriticalPrimary = redDark,
        iconDisabled = snowDisabled,
        // On green, WhatsApp's dark mode uses dark icons and labels.
        iconOnSolidPrimary = night,
        iconPrimary = snow,
        iconPrimaryAlpha = snow,
        iconSecondary = snowSecondary,
        iconSecondaryAlpha = snowSecondary,
        iconTertiary = snowTertiary,
        iconTertiaryAlpha = snowTertiary,
        iconQuaternary = snowDisabled,
        iconQuaternaryAlpha = snowDisabled,
        iconSuccessPrimary = greenDark,
        iconWarningPrimary = amberDark,
        separatorPrimary = dividerDark,
        separatorSecondary = dividerDark,
        textActionAccent = greenDark,
        textActionPrimary = snow,
        textActionSuccess = greenDark,
        textBadgeAccent = greenTint,
        textCriticalPrimary = redDark,
        textDisabled = snowDisabled,
        textLinkExternal = blueLinkDark,
        textOnSolidPrimary = night,
        textPrimary = snow,
        textSecondary = snowSecondary,
        textSuccessPrimary = greenPositiveDark,
        textWarningPrimary = amberDark,
    )
}
