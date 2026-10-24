/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class)

package io.element.android.features.home.impl.roomlist

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.AndroidComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.v2.runAndroidComposeUiTest
import com.google.common.truth.Truth.assertThat
import io.element.android.features.home.impl.HomeEvent
import io.element.android.features.home.impl.HomeNavigationBarItem
import io.element.android.features.home.impl.HomeView
import io.element.android.features.home.impl.R
import io.element.android.features.home.impl.aHomeState
import io.element.android.features.home.impl.components.RoomListMenuAction
import io.element.android.features.home.impl.model.RoomListRoomSummary
import io.element.android.features.home.impl.model.RoomSummaryDisplayType
import io.element.android.features.home.impl.model.aRoomListRoomSummary
import io.element.android.features.home.impl.spaces.HomeChannels
import io.element.android.features.home.impl.spaces.HomeSpacesState
import io.element.android.features.home.impl.spaces.SpaceChannels
import io.element.android.features.home.impl.spaces.aHomeSpacesState
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.CurrentUserMembership
import io.element.android.libraries.previewutils.room.aSpaceRoom
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.tests.testutils.EnsureNeverCalled
import io.element.android.tests.testutils.EnsureNeverCalledWithParam
import io.element.android.tests.testutils.EventsRecorder
import io.element.android.tests.testutils.clickOn
import io.element.android.tests.testutils.ensureCalledOnce
import io.element.android.tests.testutils.ensureCalledOnceWithParam
import io.element.android.tests.testutils.robolectric.RobolectricTest
import io.element.android.tests.testutils.setSafeContent
import kotlinx.collections.immutable.persistentListOf
import org.junit.Test
import org.robolectric.annotation.Config

class RoomListViewTest : RobolectricTest() {
    @Config(qualifiers = "h1024dp")
    @Test
    fun `displaying the view automatically sends a couple of UpdateVisibleRangeEvents`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<RoomListEvent>()
        setRoomListView(
            state = aRoomListState(
                contentState = aRoomsContentState(securityBannerState = SecurityBannerState.RecoveryKeyConfirmation),
                eventSink = eventsRecorder,
            )
        )

        // The banner is not included in the range, only the rooms are
        eventsRecorder.assertList(
            listOf(
                RoomListEvent.UpdateVisibleRange(0..4),
            )
        )
    }

    @Test
    fun `clicking on close recovery key banner emits the expected Event`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<RoomListEvent>()
        setRoomListView(
            state = aRoomListState(
                contentState = aRoomsContentState(securityBannerState = SecurityBannerState.RecoveryKeyConfirmation),
                // Ignore the visible range updates, which are also emitted when the banner is shrinking
                eventSink = { event ->
                    if (event !is RoomListEvent.UpdateVisibleRange) {
                        eventsRecorder(event)
                    }
                },
            )
        )

        val close = activity!!.getString(CommonStrings.action_close)
        onNodeWithContentDescription(close).performClick()
        eventsRecorder.assertSingle(RoomListEvent.DismissBanner)
    }

    @Test
    fun `clicking on close setup key banner emits the expected Event`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<RoomListEvent>()
        setRoomListView(
            state = aRoomListState(
                contentState = aRoomsContentState(securityBannerState = SecurityBannerState.SetUpRecovery),
                // Ignore the visible range updates, which are also emitted when the banner is shrinking
                eventSink = { event ->
                    if (event !is RoomListEvent.UpdateVisibleRange) {
                        eventsRecorder(event)
                    }
                },
            )
        )

        val close = activity!!.getString(CommonStrings.action_close)
        onNodeWithContentDescription(close).performClick()
        eventsRecorder.assertSingle(RoomListEvent.DismissBanner)
    }

    @Test
    fun `clicking on close swipe avatar banner emits the expected Event`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<RoomListEvent>()
        setRoomListView(
            state = aRoomListState(
                contentState = aRoomsContentState(showSwipeAvatarBanner = true),
                // Ignore the visible range updates, which are also emitted when the banner is shrinking
                eventSink = { event ->
                    if (event !is RoomListEvent.UpdateVisibleRange) {
                        eventsRecorder(event)
                    }
                },
            )
        )

        eventsRecorder.assertEmpty()
        val close = activity!!.getString(CommonStrings.action_close)
        onNodeWithContentDescription(close).performClick()
        eventsRecorder.assertSingle(RoomListEvent.DismissSwipeAvatarBanner)
    }

    @Test
    fun `clicking on continue recovery key banner invokes the expected callback`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<RoomListEvent>()
        ensureCalledOnce { callback ->
            setRoomListView(
                state = aRoomListState(
                    contentState = aRoomsContentState(securityBannerState = SecurityBannerState.RecoveryKeyConfirmation),
                    eventSink = eventsRecorder,
                ),
                onConfirmRecoveryKeyClick = callback,
            )

            // Remove automatic initial events
            eventsRecorder.clear()

            clickOn(CommonStrings.action_continue)

            eventsRecorder.assertEmpty()
        }
    }

    @Test
    fun `clicking on continue setup key banner invokes the expected callback`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<RoomListEvent>()
        ensureCalledOnce { callback ->
            setRoomListView(
                state = aRoomListState(
                    contentState = aRoomsContentState(securityBannerState = SecurityBannerState.SetUpRecovery),
                    eventSink = eventsRecorder,
                ),
                onSetUpRecoveryClick = callback,
            )
            // Remove automatic initial events
            eventsRecorder.clear()
            clickOn(R.string.banner_set_up_recovery_submit)
            eventsRecorder.assertEmpty()
        }
    }

    @Test
    fun `clicking on start chat when the session has no room invokes the expected callback`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<RoomListEvent>(expectEvents = false)
        ensureCalledOnce { callback ->
            setRoomListView(
                state = aRoomListState(
                    eventSink = eventsRecorder,
                    contentState = anEmptyContentState(),
                ),
                onCreateRoomClick = callback,
            )
            clickOn(CommonStrings.action_start_chat)
        }
    }

    // The search pill and the chips push the first room below the default window height.
    @Config(qualifiers = "h1024dp")
    @Test
    fun `clicking on a room invokes the expected callback`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<RoomListEvent>()
        val state = aRoomListState(
            eventSink = eventsRecorder,
        )
        val room0 = state.contentAsRooms().summaries.first {
            it.displayType == RoomSummaryDisplayType.ROOM
        }
        ensureCalledOnceWithParam(room0.roomId) { callback ->
            setRoomListView(
                state = state,
                onRoomClick = callback,
            )

            // Remove automatic initial events
            eventsRecorder.clear()

            onNodeWithText(room0.latestEvent.content().toString()).performClick()
        }

        eventsRecorder.assertEmpty()
    }

    // The search pill and the chips push the first room below the default window height.
    @Config(qualifiers = "h1024dp")
    @Test
    fun `clicking on a room twice invokes the expected callback only once`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<RoomListEvent>()
        val state = aRoomListState(
            eventSink = eventsRecorder,
        )
        val room0 = state.contentAsRooms().summaries.first {
            it.displayType == RoomSummaryDisplayType.ROOM
        }
        ensureCalledOnceWithParam(room0.roomId) { callback ->
            setRoomListView(
                state = state,
                onRoomClick = callback,
            )
            // Remove automatic initial events
            eventsRecorder.clear()
            onNodeWithText(room0.latestEvent.content().toString())
                .performClick()
                .performClick()
        }
        eventsRecorder.assertEmpty()
    }

    // The search pill and the chips push the first room below the default window height.
    @Config(qualifiers = "h1024dp")
    @Test
    fun `long clicking on a room emits the expected Event`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<RoomListEvent>()
        val state = aRoomListState(
            eventSink = eventsRecorder,
        )
        val room0 = state.contentAsRooms().summaries.first {
            it.displayType == RoomSummaryDisplayType.ROOM
        }
        setRoomListView(
            state = state,
        )
        // Remove automatic initial events
        eventsRecorder.clear()

        onNodeWithText(room0.latestEvent.content().toString()).performTouchInput { longClick() }
        eventsRecorder.assertSingle(RoomListEvent.ShowContextMenu(room0))
    }

    @Test
    fun `clicking on a room setting invokes the expected callback and emits expected Event`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<RoomListEvent>()
        val state = aRoomListState(
            contextMenu = aContextMenuShown(),
            eventSink = eventsRecorder,
        )
        val room0 = (state.contextMenu as RoomListState.ContextMenu.Shown).roomId
        ensureCalledOnceWithParam(room0) { callback ->
            setRoomListView(
                state = state,
                onRoomSettingsClick = callback,
            )

            // Remove automatic initial events
            eventsRecorder.clear()

            clickOn(CommonStrings.common_settings)
        }

        eventsRecorder.assertSingle(RoomListEvent.HideContextMenu)
    }

    @Test
    fun `clicking on accept and decline invite emits the expected Events`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<RoomListEvent>()
        val state = aRoomListState(
            eventSink = eventsRecorder,
        )
        val invitedRoom = state.contentAsRooms().summaries.first {
            it.displayType == RoomSummaryDisplayType.INVITE
        }
        setRoomListView(state = state)

        // Remove automatic initial events
        eventsRecorder.clear()

        clickOn(CommonStrings.action_accept)
        clickOn(CommonStrings.action_decline)
        eventsRecorder.assertList(
            listOf(
                RoomListEvent.AcceptInvite(invitedRoom),
                RoomListEvent.ShowDeclineInviteMenu(invitedRoom),
            )
        )
    }

    @Test
    fun `clicking on add account in the multi-account announcement emits the expected Event and invokes the callback`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<HomeEvent>()
        ensureCalledOnce { callback ->
            setRoomListView(
                state = aRoomListState(),
                showMultiAccountAnnouncement = true,
                homeEventSink = eventsRecorder,
                onAddAccountClick = callback,
            )
            clickOn(R.string.screen_multi_account_announcement_action)
            // Wait for the bottom sheet to be hidden
            waitForIdle()
        }
        eventsRecorder.assertSingle(HomeEvent.DismissMultiAccountAnnouncement)
    }

    @Test
    fun `the Tasks tab shows the Matcha tasks, with no Home action button`() = runAndroidComposeUiTest {
        var tasksTabPadding: PaddingValues? = null
        setRoomListView(
            state = aRoomListState(),
            currentHomeNavigationBarItem = HomeNavigationBarItem.Tasks,
            isMatchaWorkspacesEnabled = true,
            tasksTab = { tasksTabPadding = it },
        )
        assertThat(tasksTabPadding).isNotNull()
        val startChat = activity!!.getString(CommonStrings.action_start_chat)
        onNodeWithContentDescription(startChat).assertDoesNotExist()
        // The tab's label is also the top bar's title.
        onAllNodesWithText("Tasks").onFirst().assertExists()
    }

    @Test
    fun `tapping the Tasks tab selects it`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<HomeEvent>()
        setRoomListView(state = aRoomListState(), isMatchaWorkspacesEnabled = true, homeEventSink = eventsRecorder)
        clickOn(CommonStrings.common_matcha_tasks)
        eventsRecorder.assertSingle(HomeEvent.SelectHomeNavigationBarItem(HomeNavigationBarItem.Tasks))
    }

    @Test
    fun `with the Matcha workspaces the bar is Chats, Tasks, Notes and Channels, and tapping Notes selects it`() = runAndroidComposeUiTest {
        val eventsRecorder = EventsRecorder<HomeEvent>()
        setRoomListView(state = aRoomListState(), isMatchaWorkspacesEnabled = true, homeEventSink = eventsRecorder)
        val activity = requireNotNull(activity)
        onNodeWithText(activity.getString(R.string.screen_home_tab_channels)).assertExists()
        onNodeWithText(activity.getString(R.string.screen_home_tab_spaces)).assertDoesNotExist()
        clickOn(R.string.screen_home_tab_notes)
        eventsRecorder.assertSingle(HomeEvent.SelectHomeNavigationBarItem(HomeNavigationBarItem.Notes))
    }

    @Test
    fun `the Notes tab shows the Matcha notes, with no Home action button`() = runAndroidComposeUiTest {
        var notesTabPadding: PaddingValues? = null
        setRoomListView(
            state = aRoomListState(),
            currentHomeNavigationBarItem = HomeNavigationBarItem.Notes,
            isMatchaWorkspacesEnabled = true,
            notesTab = { notesTabPadding = it },
        )
        assertThat(notesTabPadding).isNotNull()
        val activity = requireNotNull(activity)
        onNodeWithContentDescription(activity.getString(CommonStrings.action_start_chat)).assertDoesNotExist()
        onNodeWithContentDescription(activity.getString(CommonStrings.action_create_space)).assertDoesNotExist()
        // The tab's label is also the top bar's title.
        onAllNodesWithText(activity.getString(R.string.screen_home_tab_notes)).onFirst().assertExists()
    }

    @Test
    fun `on the Channels tab, a channel opens its room`() = runAndroidComposeUiTest {
        ensureCalledOnceWithParam(RoomId("!general:example.com")) { callback ->
            setChannelsTab(onRoomClick = callback)
            onNodeWithText("general").performClick()
        }
        onNodeWithContentDescription(activity!!.getString(CommonStrings.action_create_space)).assertExists()
    }

    @Test
    fun `on the Channels tab, a space header opens the space`() = runAndroidComposeUiTest {
        ensureCalledOnceWithParam(RoomId("!acme:example.com")) { callback ->
            setChannelsTab(onRoomClick = callback)
            onNodeWithText("Acme").performClick()
        }
    }

    @Test
    fun `on the Channels tab, All notes opens the space's notes`() = runAndroidComposeUiTest {
        ensureCalledOnceWithParam(RoomId("!acme:example.com")) { callback ->
            setChannelsTab(onSpaceNotesClick = callback)
            clickOn(R.string.screen_home_channels_all_notes)
        }
    }

    @Test
    fun `on the Channels tab, All boards opens the space's boards`() = runAndroidComposeUiTest {
        ensureCalledOnceWithParam(RoomId("!acme:example.com")) { callback ->
            setChannelsTab(onSpaceBoardsClick = callback)
            clickOn(R.string.screen_home_channels_all_boards)
        }
    }

    @Test
    fun `on the Channels tab, All chats opens the space`() = runAndroidComposeUiTest {
        ensureCalledOnceWithParam(RoomId("!acme:example.com")) { callback ->
            setChannelsTab(onRoomClick = callback)
            clickOn(R.string.screen_home_channels_all_chats)
        }
    }
}

private fun AndroidComposeUiTest<ComponentActivity>.setChannelsTab(
    onRoomClick: (RoomId) -> Unit = EnsureNeverCalledWithParam(),
    onSpaceNotesClick: (RoomId) -> Unit = EnsureNeverCalledWithParam(),
    onSpaceBoardsClick: (RoomId) -> Unit = EnsureNeverCalledWithParam(),
) {
    val spaceChannels = SpaceChannels(
        space = aSpaceRoom(displayName = "Acme", roomId = RoomId("!acme:example.com"), state = CurrentUserMembership.JOINED),
        channels = persistentListOf(aRoomListRoomSummary(id = "!general:example.com", name = "general")),
    )
    setRoomListView(
        state = aRoomListState(),
        currentHomeNavigationBarItem = HomeNavigationBarItem.Channels,
        isMatchaWorkspacesEnabled = true,
        homeSpacesState = aHomeSpacesState(
            spaceRooms = listOf(spaceChannels.space),
            channels = HomeChannels(spaces = persistentListOf(spaceChannels), otherChannels = persistentListOf()),
        ),
        onRoomClick = onRoomClick,
        onSpaceNotesClick = onSpaceNotesClick,
        onSpaceBoardsClick = onSpaceBoardsClick,
    )
}

private fun AndroidComposeUiTest<ComponentActivity>.setRoomListView(
    state: RoomListState,
    onRoomClick: (RoomId) -> Unit = EnsureNeverCalledWithParam(),
    onSpaceNotesClick: (RoomId) -> Unit = EnsureNeverCalledWithParam(),
    onSpaceBoardsClick: (RoomId) -> Unit = EnsureNeverCalledWithParam(),
    onSettingsClick: () -> Unit = EnsureNeverCalled(),
    onSetUpRecoveryClick: () -> Unit = EnsureNeverCalled(),
    onConfirmRecoveryKeyClick: () -> Unit = EnsureNeverCalled(),
    onCreateRoomClick: () -> Unit = EnsureNeverCalled(),
    onCreateSpaceClick: () -> Unit = EnsureNeverCalled(),
    onRoomSettingsClick: (RoomId) -> Unit = EnsureNeverCalledWithParam(),
    onMenuActionClick: (RoomListMenuAction) -> Unit = EnsureNeverCalledWithParam(),
    onReportRoomClick: (RoomId) -> Unit = EnsureNeverCalledWithParam(),
    onDeclineInviteAndBlockUser: (RoomListRoomSummary) -> Unit = EnsureNeverCalledWithParam(),
    onAddAccountClick: () -> Unit = EnsureNeverCalled(),
    showMultiAccountAnnouncement: Boolean = false,
    homeEventSink: (HomeEvent) -> Unit = {},
    currentHomeNavigationBarItem: HomeNavigationBarItem = HomeNavigationBarItem.Chats,
    isMatchaWorkspacesEnabled: Boolean = false,
    homeSpacesState: HomeSpacesState = aHomeSpacesState(),
    tasksTab: @Composable (PaddingValues) -> Unit = {},
    notesTab: @Composable (PaddingValues) -> Unit = {},
) {
    setSafeContent {
        HomeView(
            homeState = aHomeState(
                roomListState = state,
                showMultiAccountAnnouncement = showMultiAccountAnnouncement,
                eventSink = homeEventSink,
                currentHomeNavigationBarItem = currentHomeNavigationBarItem,
                isMatchaWorkspacesEnabled = isMatchaWorkspacesEnabled,
                homeSpacesState = homeSpacesState,
            ),
            onRoomClick = { roomId, _ -> onRoomClick(roomId) },
            onSpaceNotesClick = onSpaceNotesClick,
            onSpaceBoardsClick = onSpaceBoardsClick,
            onSettingsClick = onSettingsClick,
            onAddAccountClick = onAddAccountClick,
            onSwitchToAccount = EnsureNeverCalledWithParam(),
            onSetUpRecoveryClick = onSetUpRecoveryClick,
            onConfirmRecoveryKeyClick = onConfirmRecoveryKeyClick,
            onStartChatClick = onCreateRoomClick,
            onCreateSpaceClick = onCreateSpaceClick,
            onRoomSettingsClick = onRoomSettingsClick,
            onMenuActionClick = onMenuActionClick,
            onDeclineInviteAndBlockUser = onDeclineInviteAndBlockUser,
            onReportRoomClick = onReportRoomClick,
            acceptDeclineInviteView = {},
            tasksTab = tasksTab,
            notesTab = notesTab,
            leaveRoomView = {},
        )
    }
}
