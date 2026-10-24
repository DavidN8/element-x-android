/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.messages.impl.matcha

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.libraries.designsystem.components.SimpleModalBottomSheet
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarSize
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.matrix.api.spaces.SpaceRoom
import io.element.android.libraries.matrix.ui.model.getAvatarData
import io.element.android.libraries.previewutils.room.aSpaceRoom
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * "Choose a space", shown when a chat's Space docs could belong to several of its joined parent spaces.
 */
@Composable
fun ChooseSpaceBottomSheet(
    spaces: ImmutableList<SpaceRoom>,
    onSelectSpace: (RoomId) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SimpleModalBottomSheet(
        title = stringResource(CommonStrings.common_matcha_choose_space),
        onDismiss = onDismiss,
        modifier = modifier,
    ) {
        spaces.forEach { space ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectSpace(space.roomId) }
                    .heightIn(min = 64.dp)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(
                    avatarData = space.getAvatarData(AvatarSize.SpaceListItem),
                    avatarType = AvatarType.Space(),
                )
                Text(
                    modifier = Modifier.padding(start = 16.dp),
                    text = space.displayName,
                    style = ElementTheme.typography.fontBodyLgMedium,
                    color = ElementTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@PreviewsDayNight
@Composable
internal fun ChooseSpaceBottomSheetPreview() = ElementPreview {
    ChooseSpaceBottomSheet(
        spaces = persistentListOf(
            aSpaceRoom(displayName = "Design team", roomId = RoomId("!design:example.com")),
            aSpaceRoom(displayName = "Company", roomId = RoomId("!company:example.com")),
        ),
        onSelectSpace = {},
        onDismiss = {},
    )
}
