/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs.notestab

import android.os.Parcelable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.matcha.impl.R
import io.element.android.features.matcha.impl.docs.DocNameSheet
import io.element.android.features.matcha.impl.docs.DocRowContent
import io.element.android.features.matcha.impl.docs.DocRowPlaceholder
import io.element.android.features.matcha.impl.docs.DocTile
import io.element.android.features.matcha.impl.docs.DocsMessagePage
import io.element.android.libraries.designsystem.components.SimpleModalBottomSheet
import io.element.android.libraries.designsystem.components.avatar.Avatar
import io.element.android.libraries.designsystem.components.avatar.AvatarType
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.FloatingActionButton
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.matrix.api.core.RoomId
import io.element.android.libraries.ui.strings.CommonStrings
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.parcelize.Parcelize

/** Keeps the last row clear of the New note button. */
private val FAB_CLEARANCE = 88.dp

private const val PLACEHOLDER_ROWS = 3

/** The key of a row of a space's section, so the rows on screen tell which spaces are shown. */
@Parcelize
private data class SpaceItemKey(val spaceId: String, val item: String) : Parcelable

/**
 * The Notes tab inside Home's scaffold: a section per space with its notes, the New note button at the bottom end of
 * [contentPadding], and the sheets.
 */
@Composable
fun NotesTabView(
    state: NotesTabState,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val eventSink = state.eventSink
    val endPadding = contentPadding.calculateEndPadding(LocalLayoutDirection.current)
    Box(modifier = modifier.fillMaxSize()) {
        when (val content = state.content) {
            NotesTabContent.Loading -> Column(Modifier.padding(contentPadding)) { repeat(PLACEHOLDER_ROWS) { DocRowPlaceholder() } }
            is NotesTabContent.Empty -> DocsMessagePage(
                icon = CompoundIcons.Document(),
                title = stringResource(R.string.screen_matcha_notes_tab_empty_title),
                message = stringResource(if (content.hasSpaces) R.string.screen_matcha_notes_tab_none_on_body else R.string.screen_matcha_notes_tab_empty_body),
                modifier = Modifier.padding(contentPadding),
            )
            is NotesTabContent.Spaces -> NotesList(sections = content.sections, contentPadding = contentPadding, eventSink = eventSink)
        }
        if (state.canCreate) {
            FloatingActionButton(
                onClick = { eventSink(NotesTabEvent.NewNote) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = endPadding + 16.dp, bottom = contentPadding.calculateBottomPadding() + 16.dp),
            ) {
                Icon(imageVector = CompoundIcons.Plus(), contentDescription = stringResource(R.string.screen_matcha_docs_new))
            }
        }
    }
    when (val sheet = state.sheet) {
        is NotesTabSheet.ChooseSpace -> ChooseSpaceSheet(
            spaces = sheet.spaces,
            onChoose = { eventSink(NotesTabEvent.ChooseSpace(it)) },
            onDismiss = { eventSink(NotesTabEvent.DismissSheet) },
        )
        is NotesTabSheet.Name -> DocNameSheet(
            state = sheet.state,
            onNameChange = { eventSink(NotesTabEvent.UpdateName(it)) },
            onSubmit = { eventSink(NotesTabEvent.SubmitName) },
            onDismiss = { eventSink(NotesTabEvent.DismissSheet) },
        )
        null -> Unit
    }
}

@Composable
private fun NotesList(
    sections: ImmutableList<NotesSection>,
    contentPadding: PaddingValues,
    eventSink: (NotesTabEvent) -> Unit,
) {
    val listState = rememberLazyListState()
    val currentEventSink by rememberUpdatedState(eventSink)
    // Only the spaces on screen have their notes read.
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.mapNotNullTo(HashSet()) { item -> (item.key as? SpaceItemKey)?.spaceId?.let(::RoomId) } }
            .distinctUntilChanged()
            .collect { currentEventSink(NotesTabEvent.SpacesShown(it)) }
    }
    val layoutDirection = LocalLayoutDirection.current
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection),
            top = contentPadding.calculateTopPadding(),
            end = contentPadding.calculateEndPadding(layoutDirection),
            bottom = contentPadding.calculateBottomPadding() + FAB_CLEARANCE,
        ),
    ) {
        sections.forEachIndexed { index, section -> section(section, isFirst = index == 0, eventSink = eventSink) }
    }
}

private fun LazyListScope.section(section: NotesSection, isFirst: Boolean, eventSink: (NotesTabEvent) -> Unit) {
    val spaceId = section.space.spaceId
    fun key(item: String) = SpaceItemKey(spaceId.value, item)
    item(key = key("header")) {
        // The header opens the space's own list, which can also rename notes.
        SpaceHeader(space = section.space, hasBand = !isFirst, onClick = { eventSink(NotesTabEvent.OpenNote(spaceId, null)) })
    }
    when (val content = section.content) {
        NotesSectionContent.Loading -> item(key = key("loading")) { DocRowPlaceholder() }
        NotesSectionContent.LoadError -> item(key = key("error")) { LoadErrorRow(onRetry = { eventSink(NotesTabEvent.Retry(spaceId)) }) }
        // The space's notes screen turns them on.
        NotesSectionContent.TurnOn -> item(key = key("turn-on")) { TurnOnRow(onClick = { eventSink(NotesTabEvent.OpenNote(spaceId, null)) }) }
        is NotesSectionContent.Notes -> items(content.notes, key = { key("note/${it.id}") }) { note ->
            DocRowContent(
                title = note.title ?: stringResource(R.string.screen_matcha_docs_main_name),
                subtitle = note.editedAt?.let { stringResource(R.string.screen_matcha_docs_edited, it) },
                modifier = Modifier.clickable { eventSink(NotesTabEvent.OpenNote(spaceId, note.id)) },
            )
        }
    }
}

/** An 8 dp band between spaces, then the space's avatar and name, like a community in a list of communities. */
@Composable
private fun SpaceHeader(space: NotesTabSpace, hasBand: Boolean, onClick: () -> Unit) {
    Column {
        if (hasBand) {
            Spacer(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(ElementTheme.colors.bgSubtleSecondary),
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .heightIn(min = 76.dp)
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics(mergeDescendants = true) { heading() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Avatar(avatarData = space.avatar, avatarType = AvatarType.Space())
            Text(
                text = space.displayName(),
                style = ElementTheme.typography.fontBodyLgMedium,
                color = ElementTheme.colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            )
            Icon(imageVector = CompoundIcons.ChevronRight(), contentDescription = null, tint = ElementTheme.colors.iconSecondary)
        }
    }
}

/** "Turn on notes", in grey: the space has no notes yet and the user may turn them on. */
@Composable
private fun TurnOnRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DocTile(icon = CompoundIcons.Plus())
        Spacer(Modifier.width(16.dp))
        Text(
            text = stringResource(R.string.screen_matcha_docs_setup_action),
            style = ElementTheme.typography.fontBodyLgRegular,
            color = ElementTheme.colors.textSecondary,
        )
    }
}

/** "Couldn't load the notes" with Retry. */
@Composable
private fun LoadErrorRow(onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DocTile(icon = CompoundIcons.Warning())
        Text(
            text = stringResource(R.string.screen_matcha_docs_list_load_error),
            style = ElementTheme.typography.fontBodyMdRegular,
            color = ElementTheme.colors.textSecondary,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 16.dp),
        )
        TextButton(text = stringResource(CommonStrings.action_retry), onClick = onRetry)
    }
}

/** New note's Choose a space: the spaces that can take the note. */
@Composable
private fun ChooseSpaceSheet(spaces: ImmutableList<NotesTabSpace>, onChoose: (RoomId) -> Unit, onDismiss: () -> Unit) {
    SimpleModalBottomSheet(title = stringResource(CommonStrings.common_matcha_choose_space), onDismiss = onDismiss) {
        spaces.forEach { space ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onChoose(space.spaceId) }
                    .heightIn(min = 64.dp)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(avatarData = space.avatar, avatarType = AvatarType.Space())
                Text(
                    text = space.displayName(),
                    style = ElementTheme.typography.fontBodyLgMedium,
                    color = ElementTheme.colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun NotesTabSpace.displayName(): String = name ?: stringResource(CommonStrings.common_no_room_name)

@PreviewsDayNight
@Composable
internal fun NotesTabViewPreview(@PreviewParameter(NotesTabStatePreviewParam::class) state: NotesTabState) = ElementPreview {
    NotesTabView(state = state, contentPadding = PaddingValues())
}
