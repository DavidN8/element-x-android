/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.ui.messages.reply

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.core.extensions.toSafeLength
import io.element.android.libraries.designsystem.atomic.atoms.PlaceholderAtom
import io.element.android.libraries.designsystem.colors.AvatarColorsProvider
import io.element.android.libraries.designsystem.icons.CompoundDrawables
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.bubbleSecondaryText
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.timeline.item.event.ProfileDetails
import io.element.android.libraries.matrix.api.timeline.item.event.getDisambiguatedDisplayName
import io.element.android.libraries.matrix.ui.components.AttachmentThumbnail
import io.element.android.libraries.matrix.ui.media.contentvalidation.ContentErrorView
import io.element.android.libraries.matrix.ui.media.contentvalidation.ContentValidationValue
import io.element.android.libraries.matrix.ui.media.contentvalidation.InvalidContentView
import io.element.android.libraries.matrix.ui.media.contentvalidation.NotFoundContentView
import io.element.android.libraries.matrix.ui.messages.sender.SenderName
import io.element.android.libraries.matrix.ui.messages.sender.SenderNameMode
import io.element.android.libraries.ui.strings.CommonStrings

/**
 * The quoted message of a reply, drawn on the quote surface of a bubble or of the composer: a bar in the quoted sender's colour at the start,
 * the sender's name, the quoted text, and a thumbnail at the end for media. When [isFromMe], the quoted message is yours and the bar
 * uses the accent colour.
 */
@Composable
fun InReplyToView(
    inReplyTo: InReplyToDetails,
    hideImage: Boolean,
    contentValidationValue: ContentValidationValue,
    modifier: Modifier = Modifier,
    maxLines: Int = 2,
    isFromMe: Boolean = false,
) {
    when (inReplyTo) {
        is InReplyToDetails.Ready -> when (contentValidationValue) {
            ContentValidationValue.Valid -> ReplyToReadyContent(
                senderId = inReplyTo.senderId,
                senderProfile = inReplyTo.senderProfile,
                metadata = inReplyTo.metadata(hideImage),
                maxLines = maxLines,
                isFromMe = isFromMe,
                modifier = modifier,
            )
            ContentValidationValue.Invalid -> ReplyToInvalidContent()
            is ContentValidationValue.UnrecoverableError -> ReplyToNotFoundContent()
            else -> ReplyToLoadingContent(modifier = modifier)
        }
        is InReplyToDetails.Error ->
            ReplyToErrorContent(modifier = modifier)
        is InReplyToDetails.Loading ->
            ReplyToLoadingContent(modifier = modifier)
    }
}

@Composable
private fun ReplyToReadyContent(
    senderId: UserId,
    senderProfile: ProfileDetails,
    metadata: InReplyToMetadata?,
    maxLines: Int,
    isFromMe: Boolean,
    modifier: Modifier = Modifier,
) {
    // The colour is only on the bar: decorative sender colours are too light for text on the quote surface.
    val barColor = if (isFromMe) ElementTheme.colors.bgAccentRest else AvatarColorsProvider.provide(senderId.value).foreground
    Row(
        modifier
            .drawBehind {
                drawRect(barColor, size = Size(QUOTE_BAR_WIDTH.toPx(), size.height))
            }
            .padding(start = QUOTE_BAR_WIDTH),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val a11InReplyToText = stringResource(CommonStrings.common_in_reply_to, senderProfile.getDisambiguatedDisplayName(senderId))
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .semantics(mergeDescendants = false) { isTraversalGroup = true },
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            SenderName(
                senderId = senderId,
                senderProfile = senderProfile,
                senderNameMode = SenderNameMode.Reply,
                modifier = Modifier.semantics {
                    contentDescription = a11InReplyToText
                    isTraversalGroup = true
                    traversalIndex = 1f
                },
            )
            ReplyToContentText(metadata, maxLines)
        }
        if (metadata is InReplyToMetadata.Thumbnail) {
            AttachmentThumbnail(
                info = metadata.attachmentThumbnailInfo,
                backgroundColor = ElementTheme.colors.bgSubtlePrimary,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

private val QUOTE_BAR_WIDTH = 4.dp

@Composable
private fun ReplyToLoadingContent(
    modifier: Modifier = Modifier,
) {
    val paddings = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
    Row(
        modifier.padding(paddings)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PlaceholderAtom(width = 80.dp, height = 12.dp)
            PlaceholderAtom(width = 140.dp, height = 14.dp)
        }
    }
}

@Composable
private fun ReplyToErrorContent(
    modifier: Modifier = Modifier,
) {
    ContentErrorView(
        title = stringResource(CommonStrings.error_message_not_found),
        message = null,
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp),
        onTextLayout = null,
    )
}

@Composable
private fun ReplyToContentText(
    metadata: InReplyToMetadata?,
    maxLines: Int,
) {
    val text = when (metadata) {
        InReplyToMetadata.Redacted -> stringResource(id = CommonStrings.common_message_removed)
        InReplyToMetadata.UnableToDecrypt -> stringResource(id = CommonStrings.common_waiting_for_decryption_key)
        // Add a limit to the text length to avoid a crash in Compose
        is InReplyToMetadata.Text -> metadata.text.toSafeLength()
        // Add a limit to the text length to avoid a crash in Compose
        is InReplyToMetadata.Thumbnail -> metadata.text.toSafeLength()
        null -> ""
    }
    val iconResourceId = when (metadata) {
        InReplyToMetadata.Redacted -> CompoundDrawables.ic_compound_delete
        InReplyToMetadata.UnableToDecrypt -> CompoundDrawables.ic_compound_time
        else -> null
    }
    Row(
        modifier = Modifier.semantics(mergeDescendants = false) {
            isTraversalGroup = true
            traversalIndex = -1f
        },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconResourceId != null) {
            Icon(
                resourceId = iconResourceId,
                tint = ElementTheme.colors.bubbleSecondaryText,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = text,
            style = ElementTheme.typography.fontBodyMdRegular,
            textAlign = TextAlign.Start,
            color = ElementTheme.colors.bubbleSecondaryText,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ReplyToInvalidContent(
    modifier: Modifier = Modifier
) {
    InvalidContentView(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp),
        onTextLayout = null,
    )
}

@Composable
private fun ReplyToNotFoundContent(
    modifier: Modifier = Modifier
) {
    NotFoundContentView(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp),
        onTextLayout = null,
    )
}

@PreviewsDayNight
@Composable
internal fun InReplyToViewPreview(@PreviewParameter(provider = InReplyToDetailsPreviewParam::class) inReplyTo: InReplyToDetails) = ElementPreview {
    InReplyToView(
        inReplyTo = inReplyTo,
        hideImage = false,
        contentValidationValue = ContentValidationValue.Valid,
    )
}

@PreviewsDayNight
@Composable
internal fun ReplyToInvalidContentPreview() {
    ElementPreview {
        ReplyToInvalidContent(modifier = Modifier.padding(10.dp))
    }
}

@PreviewsDayNight
@Composable
internal fun ReplyToNotFoundContentPreview() {
    ElementPreview {
        ReplyToNotFoundContent(modifier = Modifier.padding(10.dp))
    }
}
