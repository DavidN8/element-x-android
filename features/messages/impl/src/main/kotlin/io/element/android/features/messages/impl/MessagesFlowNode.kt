/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl

import android.os.Parcelable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.lifecycle.lifecycleScope
import com.bumble.appyx.core.children.nodeOrNull
import com.bumble.appyx.core.lifecycle.subscribe
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import com.bumble.appyx.navmodel.backstack.BackStack
import com.bumble.appyx.navmodel.backstack.active
import com.bumble.appyx.navmodel.backstack.operation.newRoot
import com.bumble.appyx.navmodel.backstack.operation.pop
import com.bumble.appyx.navmodel.backstack.operation.push
import com.bumble.appyx.navmodel.backstack.operation.replace
import com.bumble.appyx.navmodel.backstack.operation.singleTop
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import im.vector.app.features.analytics.plan.Interaction
import io.element.android.annotations.ContributesNode
import io.element.android.features.call.api.CallData
import io.element.android.features.call.api.ElementCallEntryPoint
import io.element.android.features.forward.api.ForwardEntryPoint
import io.element.android.features.knockrequests.api.list.KnockRequestsListEntryPoint
import io.element.android.features.location.api.LocationService
import io.element.android.features.location.api.RenderingMapsNotSupportedDialog
import io.element.android.features.location.api.ShareLocationEntryPoint
import io.element.android.features.location.api.ShowLocationEntryPoint
import io.element.android.features.location.api.ShowLocationMode
import io.element.android.features.matcha.api.MatchaChatBarActions
import io.element.android.features.matcha.api.MatchaRoomEntryPoint
import io.element.android.features.matcha.api.MatchaSpaceDocsEntryPoint
import io.element.android.features.messages.api.MessagesEntryPoint
import io.element.android.features.messages.impl.attachments.Attachment
import io.element.android.features.messages.impl.attachments.preview.AttachmentsPreviewNode
import io.element.android.features.messages.impl.matcha.ChooseSpaceBottomSheet
import io.element.android.features.messages.impl.pinned.DefaultPinnedEventsTimelineProvider
import io.element.android.features.messages.impl.pinned.list.PinnedMessagesListNode
import io.element.android.features.messages.impl.report.ReportMessageNode
import io.element.android.features.messages.impl.threads.ThreadedMessagesNode
import io.element.android.features.messages.impl.threads.list.ThreadsListNode
import io.element.android.features.messages.impl.timeline.TimelineController
import io.element.android.features.messages.impl.timeline.TimelineSelectionOwner
import io.element.android.features.messages.impl.timeline.debug.EventDebugInfoNode
import io.element.android.features.messages.impl.timeline.model.TimelineItem
import io.element.android.features.messages.impl.timeline.model.event.GalleryItem
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemAttachmentsContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemAudioContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemEventContentWithAttachment
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemFileContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemGalleryContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemImageContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemLocationContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemVideoContent
import io.element.android.features.messages.impl.timeline.model.event.TimelineItemVoiceContent
import io.element.android.features.messages.impl.timeline.model.event.blurHash
import io.element.android.features.messages.impl.timeline.model.event.duration
import io.element.android.features.poll.api.create.CreatePollEntryPoint
import io.element.android.features.poll.api.create.CreatePollMode
import io.element.android.libraries.androidutils.system.DeviceHasVulkanSupport
import io.element.android.libraries.architecture.BackstackView
import io.element.android.libraries.architecture.BaseFlowNode
import io.element.android.libraries.architecture.OverlayView
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.architecture.createNode
import io.element.android.libraries.architecture.overlay.Overlay
import io.element.android.libraries.architecture.overlay.operation.hide
import io.element.android.libraries.architecture.overlay.operation.show
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.dateformatter.api.DateFormatter
import io.element.android.libraries.dateformatter.api.DateFormatterMode
import io.element.android.libraries.dateformatter.api.toHumanReadableDuration
import io.element.android.libraries.designsystem.components.async.AsyncIndicator
import io.element.android.libraries.designsystem.components.async.AsyncIndicatorHost
import io.element.android.libraries.designsystem.components.async.AsyncIndicatorState
import io.element.android.libraries.di.RoomScope
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.matrix.api.core.EventId
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.core.ThreadId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.core.toRoomIdOrAlias
import io.element.android.libraries.matrix.api.media.MediaSource
import io.element.android.libraries.matrix.api.permalink.PermalinkData
import io.element.android.libraries.matrix.api.room.BaseRoom
import io.element.android.libraries.matrix.api.room.alias.matches
import io.element.android.libraries.matrix.api.room.joinedRoomMembers
import io.element.android.libraries.matrix.api.roomlist.RoomListService
import io.element.android.libraries.matrix.api.spaces.SpaceRoom
import io.element.android.libraries.matrix.api.spaces.SpaceService
import io.element.android.libraries.matrix.api.timeline.Timeline
import io.element.android.libraries.matrix.api.timeline.TimelineProvider
import io.element.android.libraries.matrix.api.timeline.item.TimelineItemDebugInfo
import io.element.android.libraries.matrix.ui.messages.RoomMemberProfilesCache
import io.element.android.libraries.matrix.ui.messages.RoomNamesCache
import io.element.android.libraries.mediaviewer.api.GalleryInfo
import io.element.android.libraries.mediaviewer.api.GalleryItemData
import io.element.android.libraries.mediaviewer.api.MediaInfo
import io.element.android.libraries.mediaviewer.api.MediaViewerEntryPoint
import io.element.android.libraries.textcomposer.mentions.LocalMentionSpanUpdater
import io.element.android.libraries.textcomposer.mentions.MentionSpanTheme
import io.element.android.libraries.textcomposer.mentions.MentionSpanUpdater
import io.element.android.libraries.ui.strings.CommonPlurals
import io.element.android.services.analytics.api.AnalyticsService
import io.element.android.services.analyticsproviders.api.trackers.captureInteraction
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.withContext
import kotlinx.parcelize.IgnoredOnParcel
import kotlinx.parcelize.Parcelize
import timber.log.Timber
import kotlin.time.Duration.Companion.milliseconds

@ContributesNode(RoomScope::class)
@AssistedInject
class MessagesFlowNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val roomListService: RoomListService,
    private val sessionId: SessionId,
    private val shareLocationEntryPoint: ShareLocationEntryPoint,
    private val showLocationEntryPoint: ShowLocationEntryPoint,
    private val createPollEntryPoint: CreatePollEntryPoint,
    private val elementCallEntryPoint: ElementCallEntryPoint,
    private val mediaViewerEntryPoint: MediaViewerEntryPoint,
    private val forwardEntryPoint: ForwardEntryPoint,
    private val analyticsService: AnalyticsService,
    private val locationService: LocationService,
    private val room: BaseRoom,
    private val roomMemberProfilesCache: RoomMemberProfilesCache,
    private val roomNamesCache: RoomNamesCache,
    private val mentionSpanUpdater: MentionSpanUpdater,
    private val mentionSpanTheme: MentionSpanTheme,
    private val pinnedEventsTimelineProvider: DefaultPinnedEventsTimelineProvider,
    private val timelineController: TimelineController,
    private val knockRequestsListEntryPoint: KnockRequestsListEntryPoint,
    private val dateFormatter: DateFormatter,
    private val coroutineDispatchers: CoroutineDispatchers,
    private val hasVulkanSupport: DeviceHasVulkanSupport,
    private val featureFlagService: FeatureFlagService,
    private val spaceService: SpaceService,
    private val matchaSpaceDocsEntryPoint: MatchaSpaceDocsEntryPoint,
    private val matchaRoomEntryPoint: MatchaRoomEntryPoint,
) : BaseFlowNode<MessagesFlowNode.NavTarget>(
    backstack = BackStack(
        initialElement = plugins.filterIsInstance<MessagesEntryPoint.Params>().first().initialTarget.toNavTarget(),
        savedStateMap = buildContext.savedStateMap,
    ),
    overlay = Overlay(
        savedStateMap = buildContext.savedStateMap,
    ),
    buildContext = buildContext,
    plugins = plugins,
), MessagesEntryPoint.NodeProxy {
    sealed interface NavTarget : Parcelable {
        @Parcelize
        data class Messages(val focusedEventId: EventId?) : NavTarget

        @Parcelize
        data class MediaViewer(
            val mode: MediaViewerEntryPoint.MediaViewerMode,
            val eventId: EventId?,
            val mediaInfo: MediaInfo,
            val mediaSource: MediaSource,
            val thumbnailSource: MediaSource?,
            val canUseOverlay: Boolean,
            val blurHash: String?,
        ) : NavTarget

        @Parcelize
        data class GalleryViewer(
            val fromPinnedMessages: Boolean,
            val eventId: EventId?,
            val galleryInfo: GalleryInfo,
            val canUseOverlay: Boolean,
            val galleryItems: List<GalleryItemData> = emptyList(),
        ) : NavTarget

        @Parcelize
        data class AttachmentPreview(val timelineMode: Timeline.Mode, val attachments: ImmutableList<Attachment>, val inReplyToEventId: EventId?) : NavTarget

        @Parcelize
        data class LocationViewer(val mode: ShowLocationMode) : NavTarget

        @Parcelize
        data class EventDebugInfo(val eventId: EventId?, val debugInfo: TimelineItemDebugInfo) : NavTarget

        @Parcelize
        data class ForwardEvent(
            val eventIds: List<EventId>,
            @IgnoredOnParcel
            val timelineProvider: TimelineProvider? = null,
        ) : NavTarget

        @Parcelize
        data class ReportMessage(val eventId: EventId, val senderId: UserId) : NavTarget

        @Parcelize
        data class SendLocation(val timelineMode: Timeline.Mode) : NavTarget

        @Parcelize
        data class CreatePoll(val timelineMode: Timeline.Mode) : NavTarget

        @Parcelize
        data class EditPoll(val timelineMode: Timeline.Mode, val eventId: EventId) : NavTarget

        @Parcelize
        data object PinnedMessagesList : NavTarget

        @Parcelize
        data object KnockRequestsList : NavTarget

        @Parcelize
        data class Thread(val threadRootId: ThreadId, val focusedEventId: EventId?) : NavTarget

        @Parcelize
        data object ThreadsList : NavTarget

        @Parcelize
        data class AvatarPreview(val name: String, val avatarUrl: String) : NavTarget

        /** The chat's Matcha task board, opening the task of [initialThreadId] when set. */
        @Parcelize
        data class Tasks(val initialThreadId: ThreadId?) : NavTarget

        /** The Matcha docs of [spaceId], one of the chat's joined parent spaces. */
        @Parcelize
        data class SpaceDocs(val spaceId: RoomId) : NavTarget
    }

    private val callback: MessagesEntryPoint.Callback = callback()
    private val asyncIndicatorState = AsyncIndicatorState()

    private var displayVulkanNotSupportedError by mutableStateOf(false)

    /** The joined spaces this chat belongs to, whose docs can be opened from the chat; empty while the Matcha workspaces are off. */
    private var parentSpaces by mutableStateOf<ImmutableList<SpaceRoom>>(persistentListOf())

    /** Whether the Matcha workspaces are on, read when a thread is picked in All threads. */
    private var isMatchaWorkspacesEnabled = false

    /** The spaces offered by the "Choose a space" sheet, while it is shown. */
    private var spaceDocsChoice by mutableStateOf<ImmutableList<SpaceRoom>?>(null)

    override fun onBuilt() {
        super.onBuilt()
        lifecycle.subscribe(
            onDestroy = {
                timelineController.close()
            }
        )
        setupCacheUpdaters()
        loadParentSpaces()

        pinnedEventsTimelineProvider.launchIn(lifecycleScope)
    }

    private fun loadParentSpaces() {
        featureFlagService.isFeatureEnabledFlow(FeatureFlags.MatchaWorkspaces)
            .distinctUntilChanged()
            .onEach { isEnabled ->
                isMatchaWorkspacesEnabled = isEnabled
                parentSpaces = if (isEnabled) {
                    spaceService.joinedParents(room.roomId)
                        .onFailure { Timber.w(it, "Could not read the parent spaces of the room") }
                        .getOrNull()
                        .orEmpty()
                        .toImmutableList()
                } else {
                    persistentListOf()
                }
            }
            .launchIn(lifecycleScope)
    }

    /**
     * Shows a Matcha screen of this chat: from a thread or the board it replaces them, so switching threads and the board doesn't
     * stack screens and back always returns to the chat; from anywhere else it is pushed.
     */
    private fun showMatchaScreen(navTarget: NavTarget) {
        when (backstack.active?.key?.navTarget) {
            is NavTarget.Thread, is NavTarget.Tasks -> backstack.replace(navTarget)
            else -> backstack.push(navTarget)
        }
    }

    private fun openGeneral() = backstack.backToGeneral()

    private val openSpaceDocsAction: () -> Unit = { openSpaceDocs() }

    /** Where the chip bar of the chat and of its threads navigates to. */
    private val matchaChatBarActions = object : MatchaChatBarActions {
        override fun openGeneral() = this@MessagesFlowNode.openGeneral()

        override fun openTasks() = showMatchaScreen(NavTarget.Tasks(initialThreadId = null))

        override fun openThreadTask(threadId: ThreadId) = showMatchaScreen(NavTarget.Tasks(initialThreadId = threadId))

        override fun openThread(threadId: ThreadId) = showMatchaScreen(NavTarget.Thread(threadId, focusedEventId = null))

        override fun openAllThreads() = backstack.push(NavTarget.ThreadsList)

        // Read while the bar is composed, so More offers Space docs once the parent spaces are known.
        override val openSpaceDocs: (() -> Unit)?
            get() = openSpaceDocsAction.takeIf { parentSpaces.isNotEmpty() }
    }

    /** Opens the docs of the chat's space; with several joined parent spaces, the user chooses one first. */
    private fun openSpaceDocs() {
        val spaces = parentSpaces
        when (spaces.size) {
            0 -> Timber.w("No joined parent space to open the docs of")
            1 -> backstack.push(NavTarget.SpaceDocs(spaces.single().roomId))
            else -> spaceDocsChoice = spaces
        }
    }

    private fun setupCacheUpdaters() {
        room.membersStateFlow
            .onEach { membersState ->
                withContext(coroutineDispatchers.computation) {
                    roomMemberProfilesCache.replace(membersState.joinedRoomMembers())
                }
            }
            .launchIn(lifecycleScope)

        roomListService
            .allRooms
            .summaries
            .onEach {
                withContext(coroutineDispatchers.computation) {
                    roomNamesCache.replace(it)
                }
            }
            .launchIn(lifecycleScope)
    }

    override fun resolve(navTarget: NavTarget, buildContext: BuildContext): Node {
        return when (navTarget) {
            is NavTarget.Messages -> {
                val callback = object : MessagesNode.Callback {
                    override fun navigateToRoomDetails() {
                        callback.navigateToRoomDetails()
                    }

                    override fun handleEventClick(
                        timelineMode: Timeline.Mode,
                        event: TimelineItem.Event,
                        canUseOverlay: Boolean,
                    ): Boolean {
                        return processEventClick(
                            timelineMode = timelineMode,
                            event = event,
                            canUseOverlay = canUseOverlay,
                        )
                    }

                    override fun handleGalleryItemClick(
                        timelineMode: Timeline.Mode,
                        event: TimelineItem.Event,
                        galleryItemIndex: Int,
                        canUseOverlay: Boolean,
                    ): Boolean {
                        return processGalleryEventClick(
                            timelineMode = timelineMode,
                            event = event,
                            canUseOverlay = canUseOverlay,
                            galleryItemIndex = galleryItemIndex,
                        )
                    }

                    override fun navigateToPreviewAttachments(attachments: ImmutableList<Attachment>, inReplyToEventId: EventId?) {
                        backstack.push(
                            NavTarget.AttachmentPreview(
                                attachments = attachments,
                                timelineMode = Timeline.Mode.Live,
                                inReplyToEventId = inReplyToEventId,
                            )
                        )
                    }

                    override fun navigateToRoomMemberDetails(userId: UserId) {
                        callback.navigateToRoomMemberDetails(userId)
                    }

                    override fun handlePermalinkClick(data: PermalinkData) {
                        callback.handlePermalinkClick(data, pushToBackstack = true)
                    }

                    override fun navigateToEventDebugInfo(eventId: EventId?, debugInfo: TimelineItemDebugInfo) {
                        backstack.push(NavTarget.EventDebugInfo(eventId, debugInfo))
                    }

                    override fun forwardEvents(eventIds: List<EventId>, timelineProvider: TimelineProvider) {
                        backstack.push(NavTarget.ForwardEvent(eventIds, timelineProvider))
                    }

                    override fun navigateToReportMessage(eventId: EventId, senderId: UserId) {
                        backstack.push(NavTarget.ReportMessage(eventId, senderId))
                    }

                    override fun navigateToSendLocation() {
                        if (hasVulkanSupport()) {
                            backstack.push(NavTarget.SendLocation(Timeline.Mode.Live))
                        } else {
                            displayVulkanNotSupportedError = true
                        }
                    }

                    override fun navigateToCreatePoll() {
                        backstack.push(NavTarget.CreatePoll(Timeline.Mode.Live))
                    }

                    override fun navigateToEditPoll(eventId: EventId) {
                        backstack.push(NavTarget.EditPoll(Timeline.Mode.Live, eventId))
                    }

                    override fun navigateToCurrentLiveLocation() {
                        if (hasVulkanSupport()) {
                            backstack.push(NavTarget.LocationViewer(ShowLocationMode.Live(senderId = sessionId)))
                        } else {
                            displayVulkanNotSupportedError = true
                        }
                    }

                    override fun navigateToRoomCall(roomId: RoomId, isAudioCall: Boolean) {
                        val callData = CallData(
                            sessionId = sessionId,
                            roomId = roomId,
                            isAudioCall = isAudioCall,
                        )
                        analyticsService.captureInteraction(Interaction.Name.MobileRoomCallButton)
                        elementCallEntryPoint.startCall(callData)
                    }

                    override fun navigateToPinnedMessagesList() {
                        backstack.push(NavTarget.PinnedMessagesList)
                    }

                    override fun navigateToKnockRequestsList() {
                        backstack.push(NavTarget.KnockRequestsList)
                    }

                    override fun navigateToThread(threadRootId: ThreadId, focusedEventId: EventId?) {
                        backstack.push(NavTarget.Thread(threadRootId, focusedEventId))
                    }

                    override fun navigateToThreadsList() {
                        backstack.push(NavTarget.ThreadsList)
                    }

                    override fun navigateToDeveloperSettings() {
                        callback.navigateToDeveloperSettings()
                    }

                    override fun navigateToAvatarPreview(username: String, avatarUrl: String) {
                        overlay.show(NavTarget.AvatarPreview(username, avatarUrl))
                    }

                    override val matchaChatBarActions = this@MessagesFlowNode.matchaChatBarActions
                }
                val inputs = MessagesNode.Inputs(focusedEventId = navTarget.focusedEventId)
                createNode<MessagesNode>(buildContext, listOf(callback, inputs))
            }
            is NavTarget.MediaViewer -> {
                val params = MediaViewerEntryPoint.Params.RoomMedia(
                    mode = navTarget.mode,
                    eventId = navTarget.eventId,
                    mediaInfo = navTarget.mediaInfo,
                    mediaSource = navTarget.mediaSource,
                    thumbnailSource = navTarget.thumbnailSource,
                    blurHash = navTarget.blurHash,
                )
                val callback = object : MediaViewerEntryPoint.Callback {
                    override fun onDone() {
                        if (navTarget.canUseOverlay) {
                            overlay.hide()
                        } else {
                            backstack.pop()
                        }
                    }

                    override fun viewInTimeline(eventId: EventId) {
                        this@MessagesFlowNode.viewInTimeline(eventId)
                    }

                    override fun forwardEvent(eventId: EventId, fromPinnedEvents: Boolean) {
                        // Need to go to the parent because of the overlay
                        callback.forwardEvent(eventId, fromPinnedEvents)
                    }
                }
                mediaViewerEntryPoint.createNode(
                    parentNode = this,
                    buildContext = buildContext,
                    params = params,
                    callback = callback
                )
            }
            is NavTarget.GalleryViewer -> {
                val params = MediaViewerEntryPoint.Params.EventGallery(
                    eventId = navTarget.eventId,
                    galleryInfo = navTarget.galleryInfo,
                    galleryItems = navTarget.galleryItems,
                    fromPinnedMessages = navTarget.fromPinnedMessages,
                )
                val callback = object : MediaViewerEntryPoint.Callback {
                    override fun onDone() {
                        if (navTarget.canUseOverlay) {
                            overlay.hide()
                        } else {
                            backstack.pop()
                        }
                    }

                    override fun viewInTimeline(eventId: EventId) {
                        this@MessagesFlowNode.viewInTimeline(eventId)
                    }

                    override fun forwardEvent(eventId: EventId, fromPinnedEvents: Boolean) {
                        // Need to go to the parent because of the overlay
                        callback.forwardEvent(eventId, fromPinnedEvents)
                    }
                }
                mediaViewerEntryPoint.createNode(
                    parentNode = this,
                    buildContext = buildContext,
                    params = params,
                    callback = callback
                )
            }
            is NavTarget.AttachmentPreview -> {
                val inputs = AttachmentsPreviewNode.Inputs(
                    attachments = navTarget.attachments,
                    timelineMode = navTarget.timelineMode,
                    inReplyToEventId = navTarget.inReplyToEventId,
                )
                createNode<AttachmentsPreviewNode>(buildContext, listOf(inputs))
            }
            is NavTarget.LocationViewer -> {
                val inputs = ShowLocationEntryPoint.Inputs(navTarget.mode)
                showLocationEntryPoint.createNode(
                    parentNode = this,
                    buildContext = buildContext,
                    inputs = inputs,
                )
            }
            is NavTarget.EventDebugInfo -> {
                val inputs = EventDebugInfoNode.Inputs(navTarget.eventId, navTarget.debugInfo)
                createNode<EventDebugInfoNode>(buildContext, listOf(inputs))
            }
            is NavTarget.ForwardEvent -> {
                // If no timeline provider is received, assume the live timeline should be used
                val timelineProvider = navTarget.timelineProvider ?: timelineController
                val params = ForwardEntryPoint.Params(
                    eventIds = navTarget.eventIds,
                    timelineProvider = timelineProvider,
                    fromRoomId = room.roomId,
                )
                val callback = object : ForwardEntryPoint.Callback {
                    override fun onDone(roomIds: List<RoomId>) {
                        // Exit selection mode only if forwarding actually happened.
                        if (roomIds.isNotEmpty()) {
                            exitSelectionMode()
                        }
                        backstack.pop()
                        val singleRoomId = roomIds.singleOrNull()
                        when {
                            singleRoomId != null -> callback.navigateToRoom(singleRoomId)
                            roomIds.isNotEmpty() -> {
                                val numberOfMessages = navTarget.eventIds.size
                                asyncIndicatorState.enqueue(durationMs = AsyncIndicator.DURATION_SHORT) {
                                    AsyncIndicator.Custom(
                                        text = pluralStringResource(CommonPlurals.screen_room_messages_forwarded, numberOfMessages, numberOfMessages),
                                    )
                                }
                            }
                        }
                    }
                }
                forwardEntryPoint.createNode(
                    parentNode = this,
                    buildContext = buildContext,
                    params = params,
                    callback = callback,
                )
            }
            is NavTarget.ReportMessage -> {
                val inputs = ReportMessageNode.Inputs(navTarget.eventId, navTarget.senderId)
                createNode<ReportMessageNode>(buildContext, listOf(inputs))
            }
            is NavTarget.SendLocation -> {
                shareLocationEntryPoint.createNode(
                    parentNode = this,
                    buildContext = buildContext,
                    timelineMode = navTarget.timelineMode,
                )
            }
            is NavTarget.CreatePoll -> {
                createPollEntryPoint.createNode(
                    parentNode = this,
                    buildContext = buildContext,
                    params = CreatePollEntryPoint.Params(
                        timelineMode = navTarget.timelineMode,
                        mode = CreatePollMode.NewPoll
                    ),
                )
            }
            is NavTarget.EditPoll -> {
                createPollEntryPoint.createNode(
                    parentNode = this,
                    buildContext = buildContext,
                    params = CreatePollEntryPoint.Params(
                        timelineMode = navTarget.timelineMode,
                        mode = CreatePollMode.EditPoll(eventId = navTarget.eventId)
                    ),
                )
            }
            NavTarget.PinnedMessagesList -> {
                val callback = object : PinnedMessagesListNode.Callback {
                    override fun handleEventClick(event: TimelineItem.Event, canUseOverlay: Boolean) {
                        processEventClick(
                            timelineMode = Timeline.Mode.PinnedEvents,
                            event = event,
                            canUseOverlay = canUseOverlay,
                        )
                    }

                    override fun handleGalleryItemClick(
                        event: TimelineItem.Event,
                        galleryItemIndex: Int,
                        canUseOverlay: Boolean,
                    ) {
                        processGalleryEventClick(
                            timelineMode = Timeline.Mode.PinnedEvents,
                            event = event,
                            galleryItemIndex = galleryItemIndex,
                            canUseOverlay = canUseOverlay,
                        )
                    }

                    override fun navigateToRoomMemberDetails(userId: UserId) {
                        callback.navigateToRoomMemberDetails(userId)
                    }

                    override fun viewInTimeline(eventId: EventId) {
                        this@MessagesFlowNode.viewInTimeline(eventId)
                    }

                    override fun handlePermalinkClick(data: PermalinkData.RoomLink) {
                        callback.handlePermalinkClick(data, pushToBackstack = !room.matches(data.roomIdOrAlias))
                    }

                    override fun navigateToEventDebugInfo(eventId: EventId?, debugInfo: TimelineItemDebugInfo) {
                        backstack.push(NavTarget.EventDebugInfo(eventId, debugInfo))
                    }

                    override fun handleForwardEventClick(eventId: EventId, timelineProvider: TimelineProvider) {
                        backstack.push(NavTarget.ForwardEvent(eventIds = listOf(eventId), timelineProvider = timelineProvider))
                    }

                    override fun navigateToThread(threadRootId: ThreadId) {
                        backstack.push(NavTarget.Thread(threadRootId, null))
                    }
                }
                createNode<PinnedMessagesListNode>(buildContext, plugins = listOf(callback))
            }
            NavTarget.KnockRequestsList -> {
                knockRequestsListEntryPoint.createNode(this, buildContext)
            }
            is NavTarget.Thread -> {
                val inputs = ThreadedMessagesNode.Inputs(
                    threadRootEventId = navTarget.threadRootId,
                    focusedEventId = navTarget.focusedEventId,
                )
                val callback = object : ThreadedMessagesNode.Callback {
                    override fun handleEventClick(
                        timelineMode: Timeline.Mode,
                        event: TimelineItem.Event,
                        canUseOverlay: Boolean,
                    ): Boolean {
                        return processEventClick(
                            timelineMode = timelineMode,
                            event = event,
                            canUseOverlay = canUseOverlay,
                        )
                    }

                    override fun handleGalleryItemClick(
                        timelineMode: Timeline.Mode,
                        event: TimelineItem.Event,
                        galleryItemIndex: Int,
                        canUseOverlay: Boolean,
                    ): Boolean {
                        return processGalleryEventClick(
                            timelineMode = timelineMode,
                            event = event,
                            canUseOverlay = canUseOverlay,
                            galleryItemIndex = galleryItemIndex,
                        )
                    }

                    override fun navigateToPreviewAttachments(attachments: ImmutableList<Attachment>, inReplyToEventId: EventId?) {
                        backstack.push(
                            NavTarget.AttachmentPreview(
                                attachments = attachments,
                                timelineMode = Timeline.Mode.Thread(navTarget.threadRootId),
                                inReplyToEventId = inReplyToEventId,
                            )
                        )
                    }

                    override fun navigateToRoomMemberDetails(userId: UserId) {
                        callback.navigateToRoomMemberDetails(userId)
                    }

                    override fun handlePermalinkClick(data: PermalinkData) {
                        callback.handlePermalinkClick(data, pushToBackstack = true)
                    }

                    override fun navigateToEventDebugInfo(eventId: EventId?, debugInfo: TimelineItemDebugInfo) {
                        backstack.push(NavTarget.EventDebugInfo(eventId, debugInfo))
                    }

                    override fun handleForwardEventClick(eventIds: List<EventId>, timelineProvider: TimelineProvider) {
                        backstack.push(NavTarget.ForwardEvent(eventIds, timelineProvider))
                    }

                    override fun navigateToReportMessage(eventId: EventId, senderId: UserId) {
                        backstack.push(NavTarget.ReportMessage(eventId, senderId))
                    }

                    override fun navigateToSendLocation() {
                        if (hasVulkanSupport()) {
                            backstack.push(NavTarget.SendLocation(Timeline.Mode.Thread(navTarget.threadRootId)))
                        } else {
                            displayVulkanNotSupportedError = true
                        }
                    }

                    override fun navigateToCreatePoll() {
                        backstack.push(NavTarget.CreatePoll(Timeline.Mode.Thread(navTarget.threadRootId)))
                    }

                    override fun navigateToEditPoll(eventId: EventId) {
                        backstack.push(NavTarget.EditPoll(Timeline.Mode.Thread(navTarget.threadRootId), eventId))
                    }

                    override fun navigateToCurrentLiveLocation() {
                        if (hasVulkanSupport()) {
                            backstack.push(NavTarget.LocationViewer(ShowLocationMode.Live(senderId = sessionId)))
                        } else {
                            displayVulkanNotSupportedError = true
                        }
                    }

                    override fun navigateToRoomCall(roomId: RoomId, isAudioCall: Boolean) {
                        val callData = CallData(
                            sessionId = sessionId,
                            roomId = roomId,
                            isAudioCall = isAudioCall
                        )
                        analyticsService.captureInteraction(Interaction.Name.MobileRoomCallButton)
                        elementCallEntryPoint.startCall(callData)
                    }

                    override fun navigateToThread(threadRootId: ThreadId, focusedEventId: EventId?) {
                        backstack.push(NavTarget.Thread(threadRootId, focusedEventId))
                    }

                    override fun navigateToDeveloperSettings() {
                        callback.navigateToDeveloperSettings()
                    }

                    override fun navigateToAvatarPreview(username: String, avatarUrl: String) {
                        overlay.show(NavTarget.AvatarPreview(username, avatarUrl))
                    }

                    override fun navigateToRoomDetails() {
                        callback.navigateToRoomDetails()
                    }

                    override val matchaChatBarActions = this@MessagesFlowNode.matchaChatBarActions
                }
                createNode<ThreadedMessagesNode>(buildContext, listOf(inputs, callback))
            }
            NavTarget.ThreadsList -> {
                val callback = object : ThreadsListNode.Callback {
                    override fun openThread(threadId: ThreadId) {
                        if (isMatchaWorkspacesEnabled) {
                            backstack.openThreadOverGeneral(threadId)
                        } else {
                            backstack.push(NavTarget.Thread(threadId, focusedEventId = null))
                        }
                    }
                }
                createNode<ThreadsListNode>(buildContext, listOf(callback))
            }
            is NavTarget.Tasks -> {
                val callback = object : MatchaRoomEntryPoint.Callback {
                    override fun openThread(threadId: ThreadId) = showMatchaScreen(NavTarget.Thread(threadId, focusedEventId = null))

                    override fun openGeneral() = this@MessagesFlowNode.openGeneral()

                    override fun openAllThreads() = backstack.push(NavTarget.ThreadsList)

                    override fun openSpaceDocs() = this@MessagesFlowNode.openSpaceDocs()

                    override fun openRoomDetails() = callback.navigateToRoomDetails()
                }
                matchaRoomEntryPoint.createTasksNode(
                    parentNode = this,
                    buildContext = buildContext,
                    params = MatchaRoomEntryPoint.Params(initialThreadId = navTarget.initialThreadId),
                    callback = callback,
                )
            }
            is NavTarget.SpaceDocs -> {
                val callback = object : MatchaSpaceDocsEntryPoint.Callback {
                    override fun close() {
                        backstack.pop()
                    }
                }
                matchaSpaceDocsEntryPoint.createNode(
                    parentNode = this,
                    buildContext = buildContext,
                    params = MatchaSpaceDocsEntryPoint.Params(spaceId = navTarget.spaceId),
                    callback = callback,
                )
            }
            is NavTarget.AvatarPreview -> {
                val callback = object : MediaViewerEntryPoint.Callback {
                    override fun onDone() {
                        overlay.hide()
                    }

                    override fun viewInTimeline(eventId: EventId) {
                        // Cannot happen
                    }

                    override fun forwardEvent(eventId: EventId, fromPinnedEvents: Boolean) {
                        // Cannot happen
                    }
                }
                val params = mediaViewerEntryPoint.createParamsForAvatar(
                    filename = navTarget.name,
                    avatarUrl = navTarget.avatarUrl,
                )
                mediaViewerEntryPoint.createNode(
                    parentNode = this,
                    buildContext = buildContext,
                    params = params,
                    callback = callback,
                )
            }
        }
    }

    private fun viewInTimeline(eventId: EventId) {
        val permalinkData = PermalinkData.RoomLink(
            roomIdOrAlias = room.roomId.toRoomIdOrAlias(),
            eventId = eventId,
        )
        callback.handlePermalinkClick(permalinkData, pushToBackstack = false)
    }

    private fun processEventClick(
        timelineMode: Timeline.Mode,
        event: TimelineItem.Event,
        canUseOverlay: Boolean,
    ): Boolean {
        val navTarget = when (event.content) {
            is TimelineItemImageContent -> {
                buildMediaViewerNavTarget(
                    mode = MediaViewerEntryPoint.MediaViewerMode.TimelineImagesAndVideos(timelineMode),
                    event = event,
                    content = event.content,
                    mediaSource = event.content.mediaSource,
                    thumbnailSource = event.content.thumbnailSource,
                    canUseOverlay = canUseOverlay,
                )
            }
            is TimelineItemVideoContent -> {
                buildMediaViewerNavTarget(
                    mode = MediaViewerEntryPoint.MediaViewerMode.TimelineImagesAndVideos(timelineMode),
                    event = event,
                    content = event.content,
                    mediaSource = event.content.mediaSource,
                    thumbnailSource = event.content.thumbnailSource,
                    canUseOverlay = canUseOverlay,
                )
            }
            is TimelineItemFileContent -> {
                buildMediaViewerNavTarget(
                    mode = MediaViewerEntryPoint.MediaViewerMode.TimelineFilesAndAudios(timelineMode),
                    event = event,
                    content = event.content,
                    mediaSource = event.content.mediaSource,
                    thumbnailSource = event.content.thumbnailSource,
                    canUseOverlay = canUseOverlay,
                )
            }
            is TimelineItemAudioContent -> {
                buildMediaViewerNavTarget(
                    mode = MediaViewerEntryPoint.MediaViewerMode.TimelineFilesAndAudios(timelineMode),
                    event = event,
                    content = event.content,
                    mediaSource = event.content.mediaSource,
                    thumbnailSource = null,
                    canUseOverlay = canUseOverlay,
                )
            }
            is TimelineItemLocationContent -> {
                if (hasVulkanSupport()) {
                    val mode = when (event.content.mode) {
                        is TimelineItemLocationContent.Mode.Live -> ShowLocationMode.Live(event.senderId)
                        is TimelineItemLocationContent.Mode.Static -> ShowLocationMode.Static(
                            location = event.content.mode.location,
                            senderName = event.safeSenderName,
                            senderId = event.senderId,
                            senderAvatarUrl = event.senderAvatar.url,
                            timestamp = event.sentTimeMillis,
                            assetType = event.content.assetType,
                        )
                    }
                    NavTarget.LocationViewer(mode = mode).takeIf { locationService.isServiceAvailable().value }
                } else {
                    displayVulkanNotSupportedError = true
                    null
                }
            }
            else -> null
        }
        return when (navTarget) {
            is NavTarget.MediaViewer -> {
                if (canUseOverlay) {
                    overlay.show(navTarget)
                } else {
                    backstack.push(navTarget)
                }
                true
            }
            is NavTarget.LocationViewer -> {
                backstack.push(navTarget)
                true
            }
            else -> false
        }
    }

    private fun processGalleryEventClick(
        timelineMode: Timeline.Mode,
        event: TimelineItem.Event,
        galleryItemIndex: Int,
        canUseOverlay: Boolean,
    ): Boolean {
        val navTarget = when (event.content) {
            is TimelineItemGalleryContent -> {
                val galleryInfo = GalleryInfo(
                    caption = event.content.caption,
                    senderId = event.senderId,
                    senderName = event.safeSenderName,
                    senderAvatar = event.senderAvatar.url,
                    dateSent = dateFormatter.format(
                        event.sentTimeMillis,
                        mode = DateFormatterMode.Day,
                    ),
                    dateSentFull = dateFormatter.format(
                        timestamp = event.sentTimeMillis,
                        mode = DateFormatterMode.Full,
                    ),
                    initialIndex = galleryItemIndex,
                )
                val galleryItems = event.content.items.map { galleryItem ->
                    GalleryItemData(
                        filename = galleryItem.filename,
                        mimeType = galleryItem.mimeType,
                        mediaSource = galleryItem.mediaSource,
                        thumbnailSource = galleryItem.thumbnailSource,
                        type = galleryItem.type.toMediaViewerType(),
                        blurHash = galleryItem.blurhash,
                    )
                }.reversed()
                NavTarget.GalleryViewer(
                    eventId = event.eventId,
                    galleryInfo = galleryInfo,
                    canUseOverlay = canUseOverlay,
                    galleryItems = galleryItems,
                    fromPinnedMessages = timelineMode is Timeline.Mode.PinnedEvents
                )
            }
            is TimelineItemAttachmentsContent -> {
                val galleryInfo = GalleryInfo(
                    caption = event.content.caption,
                    senderId = event.senderId,
                    senderName = event.safeSenderName,
                    senderAvatar = event.senderAvatar.url,
                    dateSent = dateFormatter.format(
                        event.sentTimeMillis,
                        mode = DateFormatterMode.Day,
                    ),
                    dateSentFull = dateFormatter.format(
                        timestamp = event.sentTimeMillis,
                        mode = DateFormatterMode.Full,
                    ),
                    initialIndex = galleryItemIndex,
                )
                val galleryItems = event.content.attachments.map { attachment ->
                    GalleryItemData(
                        filename = attachment.filename,
                        mimeType = attachment.mimeType,
                        mediaSource = attachment.mediaSource,
                        thumbnailSource = attachment.thumbnailSource,
                        type = GalleryItemData.Type.File,
                        blurHash = null,
                    )
                }.reversed()
                NavTarget.GalleryViewer(
                    eventId = event.eventId,
                    galleryInfo = galleryInfo,
                    canUseOverlay = canUseOverlay,
                    galleryItems = galleryItems,
                    fromPinnedMessages = timelineMode is Timeline.Mode.PinnedEvents
                )
            }
            else -> null
        }
        if (navTarget != null) {
            if (canUseOverlay) {
                overlay.show(navTarget)
            } else {
                backstack.push(navTarget)
            }
            return true
        }
        return false
    }

    private fun buildMediaViewerNavTarget(
        mode: MediaViewerEntryPoint.MediaViewerMode,
        event: TimelineItem.Event,
        content: TimelineItemEventContentWithAttachment,
        mediaSource: MediaSource,
        thumbnailSource: MediaSource?,
        canUseOverlay: Boolean,
    ): NavTarget {
        return NavTarget.MediaViewer(
            mode = mode,
            eventId = event.eventId,
            mediaInfo = MediaInfo(
                filename = content.filename,
                fileSize = content.fileSize,
                caption = content.caption,
                formattedCaption = content.formattedCaption,
                mimeType = content.mimeType,
                formattedFileSize = content.formattedFileSize,
                fileExtension = content.fileExtension,
                senderId = event.senderId,
                senderName = event.safeSenderName,
                senderAvatar = event.senderAvatar.url,
                dateSent = dateFormatter.format(
                    event.sentTimeMillis,
                    mode = DateFormatterMode.Day,
                ),
                dateSentFull = dateFormatter.format(
                    timestamp = event.sentTimeMillis,
                    mode = DateFormatterMode.Full,
                ),
                waveform = (content as? TimelineItemVoiceContent)?.waveform,
                duration = content.duration()?.toHumanReadableDuration(),
            ),
            mediaSource = mediaSource,
            thumbnailSource = thumbnailSource,
            canUseOverlay = canUseOverlay,
            blurHash = content.blurHash(),
        )
    }

    override suspend fun attachThread(threadId: ThreadId, focusedEventId: EventId?) {
        // Wait until we have the UI for the main timeline attached
        waitForChildAttached<MessagesNode>()
        // Give some time for the items in the main timeline to be received, otherwise loading the focused thread root id won't work
        // (look at TimelineItemIndexer and firstProcessLatch for more info)
        delay(10.milliseconds)
        // Then push the new threads screen on top
        backstack.push(NavTarget.Thread(threadId, focusedEventId))
    }

    private fun exitSelectionMode() {
        val prevKey = backstack.elements.value
            .lastOrNull { it.targetState == BackStack.State.STASHED }
            ?.key
        val timelineSelectionOwner = children.value[prevKey]?.nodeOrNull as? TimelineSelectionOwner
        timelineSelectionOwner?.exitTimelineSelection()
    }

    @Composable
    override fun View(modifier: Modifier) {
        mentionSpanTheme.updateStyles()

        if (displayVulkanNotSupportedError) {
            RenderingMapsNotSupportedDialog { displayVulkanNotSupportedError = false }
        }

        spaceDocsChoice?.let { spaces ->
            ChooseSpaceBottomSheet(
                spaces = spaces,
                onSelectSpace = { spaceId ->
                    spaceDocsChoice = null
                    backstack.push(NavTarget.SpaceDocs(spaceId))
                },
                onDismiss = { spaceDocsChoice = null },
            )
        }

        CompositionLocalProvider(
            LocalMentionSpanUpdater provides mentionSpanUpdater
        ) {
            Box(modifier = modifier) {
                BackstackView()
                OverlayView()
                AsyncIndicatorHost(modifier = Modifier.statusBarsPadding(), state = asyncIndicatorState)
            }
        }
    }
}

/** Goes back to the chat's own timeline, closing the threads and the board opened above it. */
internal fun BackStack<MessagesFlowNode.NavTarget>.backToGeneral() {
    val messages = elements.value
        .lastOrNull { it.key.navTarget is MessagesFlowNode.NavTarget.Messages && it.targetState != BackStack.State.DESTROYED }
        ?.key
        ?.navTarget
    if (messages != null) {
        singleTop(messages)
    } else {
        // The flow started on another screen, such as the pinned messages.
        newRoot(MessagesFlowNode.NavTarget.Messages(focusedEventId = null))
    }
}

/**
 * Opens [threadId] as the only screen above the chat's timeline, as a thread picked in All threads does: the threads, boards and All threads
 * opened before are closed, so they don't stack and back returns to General.
 */
internal fun BackStack<MessagesFlowNode.NavTarget>.openThreadOverGeneral(threadId: ThreadId) {
    backToGeneral()
    push(MessagesFlowNode.NavTarget.Thread(threadId, focusedEventId = null))
}

private fun GalleryItem.Type.toMediaViewerType(): GalleryItemData.Type {
    return when (this) {
        GalleryItem.Type.Image -> GalleryItemData.Type.Image
        GalleryItem.Type.Video -> GalleryItemData.Type.Video
        GalleryItem.Type.Audio -> GalleryItemData.Type.Audio
        GalleryItem.Type.File -> GalleryItemData.Type.File
    }
}
