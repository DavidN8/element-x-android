/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.threads.list

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.R
import io.element.android.libraries.designsystem.atomic.atoms.PlaceholderAtom
import io.element.android.libraries.designsystem.atomic.molecules.IconTitleSubtitleMolecule
import io.element.android.libraries.designsystem.components.BigIcon
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.preview.USER_NAME_ALICE
import io.element.android.libraries.designsystem.theme.aliasChatTitle
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.designsystem.utils.lazyColumnContentPadding
import io.element.android.libraries.designsystem.utils.scaffoldScrollableContentInsets
import io.element.android.libraries.matrix.api.core.ThreadId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.core.asEventId
import io.element.android.libraries.matrix.api.room.threads.ThreadListItem
import io.element.android.libraries.matrix.api.room.threads.ThreadListItemEvent
import io.element.android.libraries.matrix.api.timeline.item.event.EventContent
import io.element.android.libraries.matrix.api.timeline.item.event.MessageContent
import io.element.android.libraries.matrix.api.timeline.item.event.ProfileDetails
import io.element.android.libraries.matrix.api.timeline.item.event.TextMessageType
import io.element.android.libraries.matrix.api.timeline.item.event.getDisambiguatedDisplayName
import io.element.android.libraries.ui.strings.CommonPlurals
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.coroutines.delay

@Composable
fun ThreadsListView(
    state: ThreadsListState,
    onThreadClick: (ThreadId) -> Unit,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    val description = stringResource(
                        CommonStrings.a11y_threads_in_room,
                        state.roomName,
                    )
                    Column(
                        modifier = Modifier.clearAndSetSemantics {
                            heading()
                            contentDescription = description
                        },
                    ) {
                        Text(
                            text = stringResource(CommonStrings.common_matcha_all_threads),
                            style = ElementTheme.typography.aliasChatTitle,
                            color = ElementTheme.colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = state.roomName,
                            style = ElementTheme.typography.fontBodyMdRegular,
                            color = ElementTheme.colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    BackButton(onBackClick)
                },
                windowInsets = scaffoldScrollableContentInsets,
            )
        }
    ) { padding ->
        when {
            state.threads.isNotEmpty() -> ThreadsList(
                state = state,
                onThreadClick = onThreadClick,
                contentPadding = padding,
            )
            state.loadFailed -> ThreadsListMessage(
                title = stringResource(R.string.screen_matcha_threads_list_load_error),
                modifier = Modifier.padding(padding),
            ) {
                Button(
                    text = stringResource(CommonStrings.action_retry),
                    onClick = { state.eventSink(ThreadsListEvent.Retry) },
                )
            }
            state.isLoadingFirstPage -> Column(modifier = Modifier.padding(padding)) {
                repeat(3) { ThreadListPlaceholderRow() }
            }
            else -> ThreadsListMessage(
                title = stringResource(R.string.screen_matcha_threads_list_empty_title),
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Composable
private fun ThreadsList(
    state: ThreadsListState,
    onThreadClick: (ThreadId) -> Unit,
    contentPadding: PaddingValues,
) {
    val lazyListState = rememberLazyListState()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding + lazyColumnContentPadding,
        state = lazyListState,
    ) {
        items(state.threads, key = { row -> row.item.threadId }) { row ->
            ThreadListItemRow(
                threadItem = row,
                onClick = onThreadClick,
            )
        }
        if (state.hasMoreToLoad) {
            item {
                TextButton(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    text = stringResource(R.string.screen_matcha_threads_list_load_older),
                    onClick = { state.eventSink(ThreadsListEvent.Paginate) },
                )
            }
        }
    }

    ScrollHelper(lazyListState) {
        state.eventSink(ThreadsListEvent.Paginate)
    }
}

/** The empty and error states: a big thread icon, a title and an optional action under it. */
@Composable
private fun ThreadsListMessage(
    title: String,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconTitleSubtitleMolecule(
            title = title,
            subTitle = null,
            iconStyle = BigIcon.Style.Default(CompoundIcons.Threads()),
        )
        action()
    }
}

@Composable
private fun ThreadListPlaceholderRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(AvatarSize.ThreadsListItem.dp)
                .background(ElementTheme.colors.bgSubtleSecondary, CircleShape)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PlaceholderAtom(width = 160.dp, height = 14.dp)
            PlaceholderAtom(width = 220.dp, height = 12.dp)
        }
    }
}

@Composable
private fun ScrollHelper(
    listState: LazyListState,
    onPaginate: () -> Unit,
) {
    val updatedOnPaginate by rememberUpdatedState(onPaginate)
    val lastVisibleItemIndex by remember {
        derivedStateOf { listState.firstVisibleItemIndex + listState.layoutInfo.visibleItemsInfo.size - 1 }
    }
    val shouldPaginate by remember {
        derivedStateOf {
            val canLoadNewItems = listState.isScrollInProgress || listState.layoutInfo.totalItemsCount == 0
            canLoadNewItems && lastVisibleItemIndex >= listState.layoutInfo.totalItemsCount - 1
        }
    }
    LaunchedEffect(shouldPaginate, lastVisibleItemIndex) {
        if (shouldPaginate) {
            updatedOnPaginate()
            delay(400L)
        }
    }
}

/**
 * A thread as a chat row: a round thread tile, the thread's name and the time of the last reply, then the last reply with the reply count.
 */
@Composable
private fun ThreadListItemRow(
    threadItem: ThreadListRowItem,
    onClick: (ThreadId) -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable { onClick(threadItem.item.threadId) }
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(AvatarSize.ThreadsListItem.dp)
                .background(ElementTheme.colors.bgSubtleSecondary, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = CompoundIcons.Threads(),
                contentDescription = null,
                tint = ElementTheme.colors.iconSecondary,
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val name = threadItem.rootEventText?.lineSequence()?.firstOrNull { it.isNotBlank() }?.trim()
                Text(
                    modifier = Modifier.weight(1f),
                    text = name ?: stringResource(CommonStrings.common_matcha_thread_untitled),
                    style = ElementTheme.typography.fontBodyLgMedium,
                    color = if (name != null) ElementTheme.colors.textPrimary else ElementTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // The time turns green once there is unread data for threads; there is none yet.
                Text(
                    text = threadItem.formattedTimestamp,
                    style = ElementTheme.typography.fontBodySmRegular,
                    color = ElementTheme.colors.textSecondary,
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val latestEvent = threadItem.item.latestEvent
                val latestText = if (latestEvent != null) {
                    "${latestEvent.senderProfile.getDisambiguatedDisplayName(latestEvent.senderId)}: ${threadItem.latestEventText.orEmpty()}"
                } else {
                    ""
                }
                Text(
                    modifier = Modifier.weight(1f),
                    text = latestText,
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    modifier = Modifier.size(14.dp),
                    imageVector = CompoundIcons.Threads(),
                    contentDescription = null,
                    tint = ElementTheme.colors.iconSecondary
                )
                val replies = threadItem.item.numberOfReplies.toInt()
                val repliesDescription = pluralStringResource(CommonPlurals.common_replies, replies, replies)
                Text(
                    modifier = Modifier.clearAndSetSemantics {
                        contentDescription = repliesDescription
                    },
                    text = "$replies",
                    style = ElementTheme.typography.fontBodySmRegular,
                    color = ElementTheme.colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@PreviewsDayNight
@Composable
internal fun ThreadsListViewPreview(@PreviewParameter(ThreadsListStatePreviewParam::class) state: ThreadsListState) {
    ElementPreview {
        ThreadsListView(
            state = state,
            onThreadClick = {},
            onBackClick = {},
        )
    }
}

@PreviewsDayNight
@Composable
internal fun ThreadListItemRowPreview() {
    ElementPreview {
        ThreadListItemRow(
            threadItem = aThreadListRowItem(),
            onClick = {},
        )
    }
}

fun aThreadListRowItem(
    threadId: ThreadId = ThreadId("\$a-thread-id"),
    rootEvent: ThreadListItemEvent = aThreadListItemEvent(threadId = threadId),
    latestEvent: ThreadListItemEvent? = aThreadListItemEvent(threadId = threadId),
    numberOfReplies: Long = 42,
    rootEventText: String? = "Hello world!",
    latestEventText: String? = "Hello again!",
    formattedTimestamp: String = "12:34",
) = ThreadListRowItem(
    item = aThreadListItem(
        threadId = threadId,
        rootEvent = rootEvent,
        latestEvent = latestEvent,
        numberOfReplies = numberOfReplies,
    ),
    rootEventText = rootEventText,
    latestEventText = latestEventText,
    formattedTimestamp = formattedTimestamp,
)

fun aThreadListItem(
    threadId: ThreadId = ThreadId("\$a-thread-id"),
    rootEvent: ThreadListItemEvent = aThreadListItemEvent(threadId = threadId),
    latestEvent: ThreadListItemEvent? = aThreadListItemEvent(threadId = threadId),
    numberOfReplies: Long = 42,
) = ThreadListItem(
    rootEvent = rootEvent,
    latestEvent = latestEvent,
    numberOfReplies = numberOfReplies,
)

fun aThreadListItemEvent(
    threadId: ThreadId = ThreadId("\$a-thread-id"),
    senderId: UserId = UserId("@a-user-id:server"),
    senderProfile: ProfileDetails = ProfileDetails.Ready(
        displayName = USER_NAME_ALICE,
        displayNameAmbiguous = false,
        avatarUrl = null,
        displayedStatus = null,
    ),
    isOwn: Boolean = false,
    content: EventContent = MessageContent(
        body = "Hello world!",
        inReplyTo = null,
        isEdited = false,
        threadInfo = null,
        type = TextMessageType("Hello world!", null),
    ),
    timestamp: Long = 0L,
) = ThreadListItemEvent(
    eventId = threadId.asEventId(),
    senderId = senderId,
    senderProfile = senderProfile,
    isOwn = isOwn,
    content = content,
    timestamp = timestamp,
)
