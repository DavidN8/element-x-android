/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.element.android.appconfig.RoomListConfig
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.home.impl.HomeNavigationBarItem
import io.element.android.features.home.impl.R
import io.element.android.features.home.impl.filters.RoomListFiltersState
import io.element.android.features.home.impl.filters.RoomListFiltersView
import io.element.android.features.home.impl.filters.aRoomListFiltersState
import io.element.android.features.home.impl.spacefilters.SpaceFiltersState
import io.element.android.features.home.impl.spacefilters.aSelectedSpaceFiltersState
import io.element.android.features.home.impl.spacefilters.anUnselectedSpaceFiltersState
import io.element.android.libraries.designsystem.atomic.atoms.RedIndicatorAtom
import io.element.android.libraries.designsystem.components.TopAppBarScrollBehaviorLayout
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.modifiers.backgroundVerticalGradient
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.preview.USER_NAME_ALICE
import io.element.android.libraries.designsystem.text.AdaptativeTitle
import io.element.android.libraries.designsystem.theme.aliasAppTitle
import io.element.android.libraries.designsystem.theme.components.DropdownMenu
import io.element.android.libraries.designsystem.theme.components.DropdownMenuItem
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Surface
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.user.DisplayedStatus
import io.element.android.libraries.matrix.api.user.MatrixUser
import io.element.android.libraries.matrix.api.user.UserStatus
import io.element.android.libraries.matrix.ui.components.aMatrixUser
import io.element.android.libraries.matrix.ui.components.aMatrixUserList
import io.element.android.libraries.matrix.ui.model.getAvatarData
import io.element.android.libraries.matrix.ui.model.toEmojiText
import io.element.android.libraries.testtags.TestTags
import io.element.android.libraries.testtags.testTag
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.filter

@Composable
fun HomeTopBar(
    selectedNavigationItem: HomeNavigationBarItem,
    currentUserAndNeighbors: ImmutableList<MatrixUser>,
    showAvatarIndicator: Boolean,
    areSearchResultsDisplayed: Boolean,
    onToggleSearch: () -> Unit,
    onMenuActionClick: (RoomListMenuAction) -> Unit,
    onOpenSettings: () -> Unit,
    onAccountSwitch: (SessionId) -> Unit,
    scrollBehavior: TopAppBarScrollBehavior,
    canReportBug: Boolean,
    displayFilters: Boolean,
    filtersState: RoomListFiltersState,
    spaceFiltersState: SpaceFiltersState,
    modifier: Modifier = Modifier,
) {
    val contentPadding = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal).asPaddingValues()
    Column(modifier) {
        TopAppBar(
            modifier = Modifier
                .backgroundVerticalGradient(
                    isVisible = !areSearchResultsDisplayed,
                )
                .statusBarsPadding()
                .padding(contentPadding),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = Color.Transparent,
            ),
            title = {
                HomeTopBarTitle(
                    selectedNavigationItem = selectedNavigationItem,
                    spaceFiltersState = spaceFiltersState,
                )
            },
            actions = {
                NavigationIcon(
                    currentUserAndNeighbors = currentUserAndNeighbors,
                    showAvatarIndicator = showAvatarIndicator,
                    onAccountSwitch = onAccountSwitch,
                    onClick = onOpenSettings,
                )
                OverflowMenu(
                    onMenuActionClick = onMenuActionClick,
                    canReportBug = canReportBug,
                )
            },
            // Without a navigation icon, the default title inset already starts the title 16dp from the edge.
            windowInsets = WindowInsets(0),
        )
        if (displayFilters) {
            TopAppBarScrollBehaviorLayout(scrollBehavior = scrollBehavior) {
                Column(modifier = Modifier.padding(contentPadding)) {
                    SearchPill(onClick = onToggleSearch)
                    RoomListFiltersView(
                        state = filtersState,
                        spaceFiltersState = spaceFiltersState,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            }
        }
    }
}

/**
 * Chats shows the product name, or the name of the selected space; the other tabs show their own name. Plain text, never a wordmark.
 */
@Composable
private fun HomeTopBarTitle(
    selectedNavigationItem: HomeNavigationBarItem,
    spaceFiltersState: SpaceFiltersState,
) {
    val isChats = selectedNavigationItem == HomeNavigationBarItem.Chats
    if (isChats && spaceFiltersState is SpaceFiltersState.Selected) {
        AdaptativeTitle(
            title = spaceFiltersState.selectedFilter.spaceRoom.displayName,
            style = ElementTheme.typography.fontHeadingSmMedium,
            twoLinesStyle = ElementTheme.typography.fontHeadingSmMedium,
        )
    } else {
        Text(
            text = stringResource(if (isChats) R.string.screen_matcha_home_product_name else selectedNavigationItem.labelRes),
            style = ElementTheme.typography.aliasAppTitle,
            color = if (isChats && ElementTheme.isLightTheme) ElementTheme.colors.textActionAccent else ElementTheme.colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/**
 * A fake search field: it only opens the search overlay, which has the real one.
 */
@Composable
private fun SearchPill(
    onClick: () -> Unit,
) {
    Surface(
        shape = CircleShape,
        color = ElementTheme.colors.bgSubtleSecondary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
            .height(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Button, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = CompoundIcons.Search(),
                contentDescription = null,
                tint = ElementTheme.colors.iconSecondary,
            )
            Text(
                text = stringResource(CommonStrings.action_search),
                style = ElementTheme.typography.fontBodyLgRegular,
                color = ElementTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun OverflowMenu(
    onMenuActionClick: (RoomListMenuAction) -> Unit,
    canReportBug: Boolean,
) {
    var showMenu by remember { mutableStateOf(false) }
    Box {
        IconButton(
            onClick = { showMenu = !showMenu }
        ) {
            Icon(
                imageVector = CompoundIcons.OverflowVertical(),
                contentDescription = stringResource(R.string.a11y_matcha_home_more_options),
            )
        }
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            val items = buildList {
                add(RoomListMenuAction.CreateRoom to CommonStrings.action_create_room)
                add(RoomListMenuAction.CreateSpace to CommonStrings.action_create_space)
                add(RoomListMenuAction.Settings to CommonStrings.action_open_settings)
                if (RoomListConfig.SHOW_INVITE_MENU_ITEM) {
                    add(RoomListMenuAction.InviteFriends to CommonStrings.action_invite)
                }
                if (RoomListConfig.SHOW_REPORT_PROBLEM_MENU_ITEM && canReportBug) {
                    add(RoomListMenuAction.ReportBug to CommonStrings.common_report_a_problem)
                }
            }
            items.forEach { (action, titleRes) ->
                DropdownMenuItem(
                    onClick = {
                        showMenu = false
                        onMenuActionClick(action)
                    },
                    text = { Text(stringResource(id = titleRes)) },
                )
            }
        }
    }
}

@Composable
private fun NavigationIcon(
    currentUserAndNeighbors: ImmutableList<MatrixUser>,
    showAvatarIndicator: Boolean,
    onAccountSwitch: (SessionId) -> Unit,
    onClick: () -> Unit,
) {
    // Reserve the status emoji slot for all the accounts, else the width of the navigation icon,
    // and so the position of the title, would change while scrolling the pager.
    val reserveStatusEmojiSlot = currentUserAndNeighbors.any { it.displayedStatus != null }
    if (currentUserAndNeighbors.size == 1) {
        AccountIcon(
            matrixUser = currentUserAndNeighbors.single(),
            isCurrentAccount = true,
            showAvatarIndicator = showAvatarIndicator,
            reserveStatusEmojiSlot = reserveStatusEmojiSlot,
            onClick = onClick,
        )
    } else {
        // Render a vertical pager
        val pagerState = rememberPagerState(initialPage = 1) { currentUserAndNeighbors.size }
        // Listen to page changes and switch account if needed
        val latestOnAccountSwitch by rememberUpdatedState(onAccountSwitch)
        LaunchedEffect(pagerState) {
            snapshotFlow { pagerState.settledPage }
                // The current account is on page 1, so there is nothing to switch to
                .filter { page -> page != 1 }
                .collect { page ->
                    latestOnAccountSwitch(SessionId(currentUserAndNeighbors[page].userId.value))
                }
        }
        VerticalPager(
            state = pagerState,
            modifier = Modifier.height(48.dp),
        ) { page ->
            AccountIcon(
                matrixUser = currentUserAndNeighbors[page],
                isCurrentAccount = page == 1,
                showAvatarIndicator = page == 1 && showAvatarIndicator,
                reserveStatusEmojiSlot = reserveStatusEmojiSlot,
                onClick = if (page == 1) {
                    onClick
                } else {
                    {}
                },
            )
        }
    }
}

@Composable
private fun AccountIcon(
    matrixUser: MatrixUser,
    isCurrentAccount: Boolean,
    showAvatarIndicator: Boolean,
    reserveStatusEmojiSlot: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val testTag = if (isCurrentAccount) Modifier.testTag(TestTags.homeScreenSettings) else Modifier
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .then(testTag)
            .clickable(
                interactionSource = interactionSource,
                onClick = onClick,
                // The ripple is rendered on the avatar only, see below.
                indication = null,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .indication(
                    interactionSource = interactionSource,
                    indication = ripple(bounded = false),
                ),
            contentAlignment = Alignment.Center,
        ) {
            val avatarData by remember(matrixUser) {
                derivedStateOf {
                    matrixUser.getAvatarData(size = AvatarSize.CurrentUserTopBar)
                }
            }
            Avatar(
                avatarData = avatarData,
                avatarType = AvatarType.User,
                contentDescription = if (isCurrentAccount) {
                    if (showAvatarIndicator) {
                        stringResource(CommonStrings.a11y_settings_with_required_action)
                    } else {
                        stringResource(CommonStrings.common_settings)
                    }
                } else {
                    null
                },
            )
            if (showAvatarIndicator) {
                RedIndicatorAtom(
                    modifier = Modifier.align(Alignment.TopEnd)
                )
            }
        }
        if (reserveStatusEmojiSlot) {
            StatusEmoji(
                emoji = matrixUser.displayedStatus?.toEmojiText(),
                modifier = Modifier.offset(x = statusEmojiStartOffset),
            )
        }
    }
}

private val statusEmojiSize = 20.dp

// The avatar is centered inside a 48dp touch target, so there is already 8dp of free space
// on its trailing side. Compensate it to end up with a 4dp gap after the avatar itself.
private val statusEmojiStartOffset = 4.dp - (48.dp - AvatarSize.CurrentUserTopBar.dp) / 2

@Composable
private fun StatusEmoji(
    emoji: String?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // Ensure the emoji size is not affected by the user's font scale settings
    val fixedFontScaleDensity = remember(density.density) {
        Density(density = density.density, fontScale = 1f)
    }
    Box(
        modifier = modifier.size(statusEmojiSize),
        contentAlignment = Alignment.Center,
    ) {
        if (emoji != null) {
            CompositionLocalProvider(LocalDensity provides fixedFontScaleDensity) {
                Text(
                    text = emoji,
                    style = ElementTheme.typography.fontBodyMdRegular,
                )
            }
        }
    }
}

@PreviewsDayNight
@Composable
internal fun HomeTopBarPreview() = ElementPreview {
    HomeTopBar(
        selectedNavigationItem = HomeNavigationBarItem.Chats,
        currentUserAndNeighbors = persistentListOf(aMatrixUser(id = "@id:domain", displayName = USER_NAME_ALICE)),
        showAvatarIndicator = false,
        areSearchResultsDisplayed = false,
        scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState()),
        onOpenSettings = {},
        onAccountSwitch = {},
        onToggleSearch = {},
        canReportBug = true,
        displayFilters = true,
        filtersState = aRoomListFiltersState(),
        spaceFiltersState = anUnselectedSpaceFiltersState(),
        onMenuActionClick = {},
    )
}

@PreviewsDayNight
@Composable
internal fun HomeTopBarSpaceFiltersSelectedPreview() = ElementPreview {
    HomeTopBar(
        selectedNavigationItem = HomeNavigationBarItem.Chats,
        currentUserAndNeighbors = persistentListOf(aMatrixUser(id = "@id:domain", displayName = USER_NAME_ALICE)),
        showAvatarIndicator = false,
        areSearchResultsDisplayed = false,
        scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState()),
        onOpenSettings = {},
        onAccountSwitch = {},
        onToggleSearch = {},
        canReportBug = true,
        displayFilters = true,
        filtersState = aRoomListFiltersState(),
        spaceFiltersState = aSelectedSpaceFiltersState(),
        onMenuActionClick = {},
    )
}

@PreviewsDayNight
@Composable
internal fun HomeTopBarSpacesPreview() = ElementPreview {
    HomeTopBar(
        selectedNavigationItem = HomeNavigationBarItem.Spaces,
        currentUserAndNeighbors = persistentListOf(aMatrixUser(id = "@id:domain", displayName = USER_NAME_ALICE)),
        showAvatarIndicator = false,
        areSearchResultsDisplayed = false,
        scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState()),
        onOpenSettings = {},
        onAccountSwitch = {},
        onToggleSearch = {},
        canReportBug = true,
        displayFilters = false,
        filtersState = aRoomListFiltersState(),
        spaceFiltersState = anUnselectedSpaceFiltersState(),
        onMenuActionClick = {},
    )
}

@PreviewsDayNight
@Composable
internal fun HomeTopBarWithIndicatorPreview() = ElementPreview {
    HomeTopBar(
        selectedNavigationItem = HomeNavigationBarItem.Chats,
        currentUserAndNeighbors = persistentListOf(aMatrixUser(id = "@id:domain", displayName = USER_NAME_ALICE)),
        showAvatarIndicator = true,
        areSearchResultsDisplayed = false,
        scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState()),
        onOpenSettings = {},
        onAccountSwitch = {},
        onToggleSearch = {},
        canReportBug = true,
        displayFilters = true,
        filtersState = aRoomListFiltersState(),
        spaceFiltersState = anUnselectedSpaceFiltersState(),
        onMenuActionClick = {},
    )
}

@PreviewsDayNight
@Composable
internal fun HomeTopBarWithStatusPreview() = ElementPreview {
    HomeTopBar(
        selectedNavigationItem = HomeNavigationBarItem.Chats,
        currentUserAndNeighbors = persistentListOf(
            aMatrixUser(
                id = "@id:domain",
                displayName = USER_NAME_ALICE,
                displayedStatus = DisplayedStatus.UserSet(UserStatus(emoji = "🌴", text = "Away")),
            )
        ),
        showAvatarIndicator = false,
        areSearchResultsDisplayed = false,
        scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState()),
        onOpenSettings = {},
        onAccountSwitch = {},
        onToggleSearch = {},
        canReportBug = true,
        displayFilters = true,
        filtersState = aRoomListFiltersState(),
        spaceFiltersState = anUnselectedSpaceFiltersState(),
        onMenuActionClick = {},
    )
}

@PreviewsDayNight
@Composable
internal fun HomeTopBarMultiAccountPreview() = ElementPreview {
    HomeTopBar(
        selectedNavigationItem = HomeNavigationBarItem.Chats,
        currentUserAndNeighbors = aMatrixUserList().take(3).toImmutableList(),
        showAvatarIndicator = false,
        areSearchResultsDisplayed = false,
        scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState()),
        onOpenSettings = {},
        onAccountSwitch = {},
        onToggleSearch = {},
        canReportBug = true,
        displayFilters = true,
        filtersState = aRoomListFiltersState(),
        spaceFiltersState = anUnselectedSpaceFiltersState(),
        onMenuActionClick = {},
    )
}
