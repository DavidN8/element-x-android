/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.messagecomposer

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.messages.impl.R
import io.element.android.libraries.androidutils.ui.hideKeyboard
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.Text

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AttachmentsBottomSheet(
    state: MessageComposerState,
    onSendLocationClick: () -> Unit,
    onCreatePollClick: () -> Unit,
    enableTextFormatting: Boolean,
    modifier: Modifier = Modifier,
    // Null hides the Matcha Task tile.
    onCreateTaskClick: (() -> Unit)? = null,
) {
    val localView = LocalView.current
    var isVisible by rememberSaveable { mutableStateOf(state.showAttachmentSourcePicker) }

    BackHandler(enabled = isVisible) {
        isVisible = false
    }

    LaunchedEffect(state.showAttachmentSourcePicker) {
        isVisible = if (state.showAttachmentSourcePicker) {
            // We need to use this instead of `LocalFocusManager.clearFocus()` to hide the keyboard when focus is on an Android View
            localView.hideKeyboard()
            true
        } else {
            false
        }
    }
    // Send 'DismissAttachmentMenu' event when the bottomsheet was just hidden
    LaunchedEffect(isVisible) {
        if (!isVisible) {
            state.eventSink(MessageComposerEvent.DismissAttachmentMenu)
        }
    }

    if (isVisible) {
        ModalBottomSheet(
            modifier = modifier,
            sheetState = rememberBottomSheetState(
                initialValue = SheetValue.Hidden,
                enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
            ),
            onDismissRequest = { isVisible = false },
            scrollable = false,
        ) {
            AttachmentSourcePickerMenu(
                state = state,
                enableTextFormatting = enableTextFormatting,
                onSendLocationClick = onSendLocationClick,
                onCreatePollClick = onCreatePollClick,
                onCreateTaskClick = onCreateTaskClick,
            )
        }
    }
}

/** One tile of the attach sheet: a round icon in a decorative tone with its label under it. */
private data class AttachmentTile(
    val icon: ImageVector,
    @StringRes val label: Int,
    val background: Color,
    val iconColor: Color,
    val onClick: () -> Unit,
)

@Composable
private fun AttachmentSourcePickerMenu(
    state: MessageComposerState,
    onSendLocationClick: () -> Unit,
    onCreatePollClick: () -> Unit,
    enableTextFormatting: Boolean,
    onCreateTaskClick: (() -> Unit)?,
) {
    val colors = ElementTheme.colors
    // No tile is green: green means an action or a selection, never decoration.
    val tiles = buildList {
        add(AttachmentTile(CompoundIcons.Image(), R.string.screen_room_attachment_source_gallery, colors.bgDecorative4, colors.textDecorative4) {
            state.eventSink(MessageComposerEvent.PickAttachmentSource.FromGallery)
        })
        add(AttachmentTile(CompoundIcons.TakePhoto(), R.string.screen_room_attachment_source_camera_photo, colors.bgDecorative3, colors.textDecorative3) {
            state.eventSink(MessageComposerEvent.PickAttachmentSource.PhotoFromCamera)
        })
        add(AttachmentTile(CompoundIcons.VideoCall(), R.string.screen_room_attachment_source_camera_video, colors.bgDecorative5, colors.textDecorative5) {
            state.eventSink(MessageComposerEvent.PickAttachmentSource.VideoFromCamera)
        })
        add(AttachmentTile(CompoundIcons.Attachment(), R.string.screen_room_attachment_source_files, colors.bgDecorative2, colors.textDecorative2) {
            state.eventSink(MessageComposerEvent.PickAttachmentSource.FromFiles)
        })
        if (state.canShareLocation) {
            add(AttachmentTile(CompoundIcons.LocationPin(), R.string.screen_room_attachment_source_location, colors.bgDecorative6, colors.textDecorative6) {
                state.eventSink(MessageComposerEvent.PickAttachmentSource.Location)
                onSendLocationClick()
            })
        }
        add(AttachmentTile(CompoundIcons.Polls(), R.string.screen_room_attachment_source_poll, colors.bgDecorative1, colors.textDecorative1) {
            state.eventSink(MessageComposerEvent.PickAttachmentSource.Poll)
            onCreatePollClick()
        })
        if (onCreateTaskClick != null) {
            add(AttachmentTile(CompoundIcons.CheckCircle(), R.string.screen_matcha_room_attach_task, colors.bgDecorative4, colors.textDecorative4) {
                state.eventSink(MessageComposerEvent.DismissAttachmentMenu)
                onCreateTaskClick()
            })
        }
        if (enableTextFormatting) {
            add(AttachmentTile(
                CompoundIcons.TextFormatting(),
                R.string.screen_room_attachment_text_formatting,
                colors.bgSubtleSecondary,
                colors.iconSecondary
            ) {
                state.eventSink(MessageComposerEvent.ToggleTextFormatting(enabled = true))
            })
        }
    }
    BoxWithConstraints(
        modifier = Modifier
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 16.dp)
    ) {
        val columns = if (maxWidth >= 600.dp) 4 else 3
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            tiles.chunked(columns).forEach { rowTiles ->
                Row {
                    rowTiles.forEach { tile ->
                        AttachmentTileView(tile = tile, modifier = Modifier.weight(1f))
                    }
                    repeat(columns - rowTiles.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentTileView(
    tile: AttachmentTile,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(role = Role.Button, onClick = tile.onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(tile.background, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = tile.icon,
                // The label names the tile
                contentDescription = null,
                tint = tile.iconColor,
            )
        }
        Text(
            text = stringResource(tile.label),
            style = ElementTheme.typography.fontBodySmRegular,
            color = ElementTheme.colors.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@PreviewsDayNight
@Composable
internal fun AttachmentsBottomSheetPreview() = ElementPreview(fillMaxSize = true) {
    AttachmentsBottomSheet(
        state = aMessageComposerState(
            canShareLocation = true,
            showAttachmentSourcePicker = true,
        ),
        onSendLocationClick = {},
        onCreatePollClick = {},
        enableTextFormatting = true,
        onCreateTaskClick = {},
    )
}
