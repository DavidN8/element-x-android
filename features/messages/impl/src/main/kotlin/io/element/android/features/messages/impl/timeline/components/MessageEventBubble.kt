/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2022-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.timeline.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.messages.impl.timeline.model.TimelineItemGroupPosition
import io.element.android.features.messages.impl.timeline.model.bubble.BubbleState
import io.element.android.features.messages.impl.timeline.model.bubble.BubbleStatePreviewParam
import io.element.android.libraries.core.extensions.to01
import io.element.android.libraries.designsystem.modifiers.onKeyboardContextMenuAction
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.text.toDp
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.messageFromMeBackground
import io.element.android.libraries.designsystem.theme.messageFromOtherBackground
import io.element.android.libraries.testtags.TestTags
import io.element.android.libraries.testtags.testTag
import io.element.android.libraries.ui.utils.a11y.isTalkbackActive
import io.element.android.libraries.ui.utils.graphics.drawInLayer

private val BUBBLE_RADIUS = 10.dp

/** Width of the tail on the first bubble of a run, and of the matching gap kept on that side of every bubble so that bodies align. */
internal val BUBBLE_TAIL_WIDTH = 8.dp
private val BUBBLE_TAIL_HEIGHT = 10.dp
private val BUBBLE_SHADOW_OFFSET = 1.dp

private val MIN_BUBBLE_WIDTH = 80.dp
private val MAX_BUBBLE_WIDTH = 520.dp

@Composable
fun MessageEventBubble(
    state: BubbleState,
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    customBackgroundColor: Color? = null,
    borderColor: Color? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val clickableModifier = if (isTalkbackActive()) {
        Modifier
    } else {
        Modifier
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                indication = ripple(),
                interactionSource = interactionSource
            )
            .onKeyboardContextMenuAction(onLongClick)
    }

    // Ignore state.isHighlighted for now, we need a design decision on it.
    val backgroundBubbleColor by rememberUpdatedState(customBackgroundColor ?: MessageEventBubbleDefaults.backgroundBubbleColor(state.isMine))
    val bubbleShape = remember(state.groupPosition, state.isMine) { MessageEventBubbleDefaults.shape(state.groupPosition, state.isMine) }
    // A 1 dp shadow under the bubble in light mode only; dark mode separates surfaces by tone.
    val shadowColor = if (ElementTheme.colors.isLight) BUBBLE_SHADOW_COLOR else Color.Transparent

    val updatedBorderColor by rememberUpdatedState(borderColor)
    BoxWithConstraints(
        modifier = modifier
            .drawWithCache {
                // Calculate the outline of the background and cache it
                val outline = bubbleShape.createOutline(size, layoutDirection, this)
                val shadowOffsetPx = BUBBLE_SHADOW_OFFSET.toPx()

                onDrawWithContent {
                    translate(top = shadowOffsetPx) {
                        drawOutline(outline, shadowColor)
                    }
                    // Draw the contents in a layer to be able to clip them with the same outline
                    // For some reason, doing this clipping outside a layer messes up with the touch events
                    drawInLayer(
                        composingStrategy = CompositingStrategy.Offscreen,
                        outline = outline,
                        clip = true,
                    ) {
                        // Draw the background first, so that it's behind the content
                        drawRect(backgroundBubbleColor)

                        // Then draw the content on top of it
                        drawContent()

                        // Draw border color, if any
                        updatedBorderColor?.let { drawOutline(outline, it, style = Stroke(width = 1.dp.toPx())) }
                    }
                }
            },
        // Need to set the contentAlignment again (it's already set in TimelineItemEventRow), for the case
        // when content width is low.
        contentAlignment = if (state.isMine) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        val maxBubbleWidth = (constraints.maxWidth * MessageEventBubbleDefaults.BUBBLE_WIDTH_RATIO).toInt().toDp().coerceAtMost(MAX_BUBBLE_WIDTH)
        Box(
            modifier = Modifier
                .testTag(TestTags.messageBubble)
                .then(clickableModifier)
                // Keep the tail's room on the outer side of every bubble, so that the bodies of a run line up.
                .padding(
                    start = if (state.isMine) 0.dp else BUBBLE_TAIL_WIDTH,
                    end = if (state.isMine) BUBBLE_TAIL_WIDTH else 0.dp,
                )
                .widthIn(
                    min = MIN_BUBBLE_WIDTH,
                    max = maxBubbleWidth,
                ),
            content = content,
        )
    }
}

private val BUBBLE_SHADOW_COLOR = Color(0x1A0B141A)

object MessageEventBubbleDefaults {
    /**
     * The bubble's outline. The body leaves [BUBBLE_TAIL_WIDTH] free on the outer side (the end for your own messages, the start for others),
     * where the first bubble of a run draws its tail at the top corner.
     */
    fun shape(groupPosition: TimelineItemGroupPosition, isMine: Boolean): Shape = BubbleShape(
        hasTail = groupPosition.isNew(),
        isMine = isMine,
    )

    @Composable
    fun backgroundBubbleColor(isMine: Boolean): Color {
        return if (isMine) {
            ElementTheme.colors.messageFromMeBackground
        } else {
            ElementTheme.colors.messageFromOtherBackground
        }
    }

    // Design says: The maximum width of a bubble is still 3/4 of the screen width. But try with 78% now.
    const val BUBBLE_WIDTH_RATIO = 0.78f
}

private class BubbleShape(
    private val hasTail: Boolean,
    private val isMine: Boolean,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val tailWidth = with(density) { BUBBLE_TAIL_WIDTH.toPx() }
        val tailHeight = with(density) { BUBBLE_TAIL_HEIGHT.toPx() }
        val radius = with(density) { BUBBLE_RADIUS.toPx() }
        // The tail is on the end side for your own messages and on the start side for others, mirrored in RTL.
        val tailOnRight = isMine == (layoutDirection == LayoutDirection.Ltr)
        val bodyLeft = if (tailOnRight) 0f else tailWidth
        val bodyRight = if (tailOnRight) size.width - tailWidth else size.width
        val body = RoundRect(
            left = bodyLeft,
            top = 0f,
            right = bodyRight,
            bottom = size.height,
            topLeftCornerRadius = CornerRadius(if (hasTail && !tailOnRight) 0f else radius),
            topRightCornerRadius = CornerRadius(if (hasTail && tailOnRight) 0f else radius),
            bottomRightCornerRadius = CornerRadius(radius),
            bottomLeftCornerRadius = CornerRadius(radius),
        )
        val path = Path().apply { addRoundRect(body) }
        if (hasTail) {
            val tail = Path().apply {
                if (tailOnRight) {
                    moveTo(bodyRight - 1f, 0f)
                    lineTo(size.width - tailWidth / 4, 0f)
                    // A slightly rounded tip
                    quadraticTo(size.width, 0f, size.width - tailWidth / 4, tailHeight / 4)
                    lineTo(bodyRight, tailHeight)
                    lineTo(bodyRight - 1f, tailHeight)
                } else {
                    moveTo(bodyLeft + 1f, 0f)
                    lineTo(tailWidth / 4, 0f)
                    quadraticTo(0f, 0f, tailWidth / 4, tailHeight / 4)
                    lineTo(bodyLeft, tailHeight)
                    lineTo(bodyLeft + 1f, tailHeight)
                }
                close()
            }
            path.op(path, tail, PathOperation.Union)
        }
        return Outline.Generic(path)
    }
}

@PreviewsDayNight
@Composable
internal fun MessageEventBubblePreview(@PreviewParameter(BubbleStatePreviewParam::class) state: BubbleState) = ElementPreview {
    // Due to position offset, surround with a Box
    Box(
        modifier = Modifier
            .size(width = 240.dp, height = 64.dp)
            .padding(vertical = 8.dp),
        contentAlignment = if (state.isMine) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        MessageEventBubble(
            state = state,
            interactionSource = remember { MutableInteractionSource() },
            onClick = {},
            onLongClick = {},
        ) {
            // Render the state as a text to better understand the previews
            Box(
                modifier = Modifier
                    .size(width = 120.dp, height = 32.dp)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "${state.groupPosition.javaClass.simpleName} isMine:${state.isMine.to01()}",
                    style = ElementTheme.typography.fontBodyXsRegular,
                )
            }
        }
    }
}
