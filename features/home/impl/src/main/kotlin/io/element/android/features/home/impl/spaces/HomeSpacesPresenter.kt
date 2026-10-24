/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.home.impl.spaces

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.zacsweers.metro.Inject
import io.element.android.features.home.impl.datasource.RoomListRoomSummaryFactory
import io.element.android.features.invite.api.SeenInvitesStore
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.room.CurrentUserMembership
import io.element.android.libraries.matrix.api.roomlist.RoomList
import io.element.android.libraries.matrix.api.roomlist.RoomSummary
import io.element.android.libraries.matrix.api.spaces.SpaceRoom
import io.element.android.libraries.matrix.api.spaces.SpaceServiceFilter
import io.element.android.libraries.matrix.ui.safety.rememberHideInvitesAvatar
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.transform
import kotlin.time.Duration.Companion.seconds

@Inject
class HomeSpacesPresenter(
    private val client: MatrixClient,
    private val seenInvitesStore: SeenInvitesStore,
    private val featureFlagService: FeatureFlagService,
    private val roomListRoomSummaryFactory: RoomListRoomSummaryFactory,
    private val coroutineDispatchers: CoroutineDispatchers,
) : Presenter<HomeSpacesState> {
    @OptIn(ExperimentalCoroutinesApi::class)
    @Composable
    override fun present(): HomeSpacesState {
        val hideInvitesAvatar by client.rememberHideInvitesAvatar()
        val spaceRooms by remember {
            client.spaceService.topLevelSpacesFlow.map { it.toImmutableList() }
        }.collectAsState(persistentListOf())

        val seenSpaceInvites by remember {
            seenInvitesStore.seenRoomIds().map { it.toImmutableSet() }
        }.collectAsState(persistentSetOf())

        // Only built while the Matcha workspaces are on, so the stock Spaces tab costs nothing more.
        val channels by remember {
            featureFlagService.isFeatureEnabledFlow(FeatureFlags.MatchaWorkspaces)
                .flatMapLatest { isEnabled -> if (isEnabled) channelsFlow() else flowOf(null) }
        }.collectAsState(initial = null)

        fun handleEvent(event: HomeSpacesEvent) {
            // when (event) { }
        }

        return HomeSpacesState(
            space = CurrentSpace.Root,
            spaceRooms = spaceRooms,
            seenSpaceInvites = seenSpaceInvites,
            hideInvitesAvatar = hideInvitesAvatar,
            // TODO enable once we can link to the screen to explore public spaces
            canExploreSpaces = false,
            channels = channels,
            eventSink = ::handleEvent,
        )
    }

    /** The channels grouped by space, from what the SDK already knows: no room is opened. Null until the room list is loaded. */
    @OptIn(FlowPreview::class)
    private fun channelsFlow(): Flow<HomeChannels?> {
        val allRooms = client.roomListService.allRooms
        // The SDK does not update a room's notification mode in the room list, so the mute icons are rebuilt as the Chats list does.
        val rebuildsOnNotificationSettingsChange = client.notificationSettingsService.notificationSettingsChangeFlow
            .debounce(0.5.seconds)
            .transform<Unit, HomeChannels?> { allRooms.rebuildSummaries() }
        val channels = combine(
            client.spaceService.topLevelSpacesFlow.startingEmpty(),
            client.spaceService.spaceFiltersFlow.startingEmpty(),
            allRooms.loadingState,
            allRooms.summaries,
        ) { spaces, filters, loadingState, summaries ->
            // ponytail: rebuilds every row on each room list update; cache the rows by summary if it ever shows up in a profile.
            if (loadingState is RoomList.LoadingState.Loaded) groupChannels(spaces, filters, summaries) else null
        }.flowOn(coroutineDispatchers.computation)
        return merge(channels, rebuildsOnNotificationSettingsChange)
    }

    private fun groupChannels(spaces: List<SpaceRoom>, filters: List<SpaceServiceFilter>, summaries: List<RoomSummary>): HomeChannels {
        // The room list is sorted by recency, so each group is too.
        val channels = summaries
            .filter { it.info.currentUserMembership == CurrentUserMembership.JOINED && !it.info.isSpace && !it.isDm }
            .map(roomListRoomSummaryFactory::create)
        // A level 0 filter holds the direct children of a top level space, and the level 1 filters that follow it hold the rest of its tree.
        val trees = mutableMapOf<RoomId, MutableSet<RoomId>>()
        var topLevelSpaceId: RoomId? = null
        for (filter in filters) {
            if (filter.level == 0) topLevelSpaceId = filter.spaceRoom.roomId
            topLevelSpaceId?.let { trees.getOrPut(it) { mutableSetOf() } += filter.descendants }
        }
        val inAnySpace = filters.flatMapTo(mutableSetOf()) { it.descendants }
        return HomeChannels(
            spaces = spaces
                // Invites keep the row they have on the Spaces tab.
                .filter { it.state != CurrentUserMembership.INVITED }
                .map { space ->
                    val tree = trees[space.roomId].orEmpty()
                    SpaceChannels(space = space, channels = channels.filter { it.roomId in tree }.toImmutableList())
                }
                .toImmutableList(),
            otherChannels = channels.filter { it.roomId !in inAnySpace }.toImmutableList(),
        )
    }
}

/** The SDK replays its latest list, but sends none until it has one: start empty rather than wait. */
private fun <T> SharedFlow<List<T>>.startingEmpty(): Flow<List<T>> = onStart { if (replayCache.isEmpty()) emit(emptyList()) }
