/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appnav

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.bumble.appyx.navmodel.backstack.BackStack
import com.bumble.appyx.navmodel.backstack.BackStackElement
import io.element.android.appnav.twopane.NoRoomSelectedView
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.architecture.appyx.SafeChildren
import io.element.android.libraries.designsystem.theme.components.ElementDividerDefaults
import io.element.android.libraries.designsystem.utils.LocalIsInDetailPane

// Room list / detail split, Material list-detail proportions (WhatsApp uses the same 40/60 on tablets).
private const val LIST_PANE_WEIGHT = 2f
private const val DETAIL_PANE_WEIGHT = 3f

/**
 * Two-pane rendering of [LoggedInFlowNode] for wide windows (tablets, unfolded foldables): the Home node
 * (room list) stays visible on the start side while the rest of the backstack renders on the end side.
 *
 * The nav model is untouched: this only changes how the existing backstack is drawn, so phone
 * behaviour is unchanged. While another element is active the Home node is stashed (lifecycle CREATED),
 * so back presses go to the detail pane first and Home's own back handlers only resume once it is active again.
 */
@Composable
internal fun LoggedInFlowNode.LoggedInTwoPaneView(
    homeElement: BackStackElement<LoggedInFlowNode.NavTarget>,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier) {
        // The Home node is kept alive while stashed (ChildEntry.KeepMode.KEEP), so it can be drawn directly.
        // Wrapped in a Box like Appyx's ChildRendererImpl does: flow nodes don't apply the modifier they are given.
        val homeNode = remember(homeElement.key.id) { childOrCreate(homeElement.key).node }
        Box(
            modifier = Modifier
                .weight(LIST_PANE_WEIGHT)
                .fillMaxHeight(),
        ) {
            homeNode.Compose()
        }
        Box(
            modifier = Modifier
                .weight(DETAIL_PANE_WEIGHT)
                .fillMaxHeight(),
        ) {
            // The screens of this pane continue the list pane's header band in their top bars.
            CompositionLocalProvider(LocalIsInDetailPane provides true) {
                if (homeElement.targetState == BackStack.State.ACTIVE) {
                    NoRoomSelectedView()
                }
                // No transition: the phone slide animation would sweep across the whole pane on every room switch.
                SafeChildren(
                    navModel = backstack,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    children<LoggedInFlowNode.NavTarget> { child, descriptor ->
                        // Home is drawn in the start pane.
                        if (descriptor.element !is LoggedInFlowNode.NavTarget.Home) {
                            child()
                        }
                    }
                }
            }
            // Hairline between the panes. It starts below the status bar so the header band reads as one strip, and it is drawn
            // over this pane rather than between the panes, which would leave a gap in the band.
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .statusBarsPadding()
                    .width(ElementDividerDefaults.thickness)
                    .background(ElementTheme.colors.separatorPrimary),
            )
        }
    }
}
