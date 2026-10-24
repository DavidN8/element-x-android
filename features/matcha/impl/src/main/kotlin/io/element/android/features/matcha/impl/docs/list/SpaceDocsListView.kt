/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.matcha.impl.R
import io.element.android.features.matcha.impl.docs.DocNameSheet
import io.element.android.features.matcha.impl.docs.DocRowContent
import io.element.android.features.matcha.impl.docs.DocRowPlaceholder
import io.element.android.features.matcha.impl.docs.DocsMessagePage
import io.element.android.features.matcha.impl.docs.DocsNoticeStrip
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.aliasScreenTitle
import io.element.android.libraries.designsystem.theme.components.FloatingActionButton
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun SpaceDocsListView(
    state: SpaceDocsListState,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onClick = onBackClick) },
                title = { DocsListTitle(spaceName = state.spaceName) },
            )
        },
        floatingActionButton = {
            if (state.canCreate) {
                FloatingActionButton(onClick = { state.eventSink(SpaceDocsListEvent.NewDocument) }) {
                    Icon(imageVector = CompoundIcons.Plus(), contentDescription = stringResource(R.string.screen_matcha_docs_new))
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (state.hasUnreadableNames) {
                DocsNoticeStrip(icon = CompoundIcons.Warning(), text = stringResource(R.string.screen_matcha_docs_list_decrypt_error))
            }
            when (val content = state.content) {
                SpaceDocsListContent.Loading -> Column { repeat(PLACEHOLDER_ROWS) { DocRowPlaceholder() } }
                SpaceDocsListContent.LoadError -> DocsMessagePage(
                    icon = CompoundIcons.Warning(),
                    title = null,
                    message = stringResource(R.string.screen_matcha_docs_list_load_error),
                    actionText = stringResource(CommonStrings.action_retry),
                    onAction = { state.eventSink(SpaceDocsListEvent.Retry) },
                )
                is SpaceDocsListContent.NotSetUp -> DocsMessagePage(
                    icon = CompoundIcons.Document(),
                    title = null,
                    message = stringResource(if (content.canSetUp) R.string.screen_matcha_docs_tip else R.string.screen_matcha_docs_not_enabled),
                    actionText = if (content.canSetUp) stringResource(R.string.screen_matcha_docs_tip_action) else null,
                    onAction = { state.eventSink(SpaceDocsListEvent.OpenSetup) },
                )
                is SpaceDocsListContent.Documents -> DocumentRows(state, content)
            }
        }
    }
    state.nameSheet?.let { sheet ->
        DocNameSheet(
            state = sheet,
            onNameChange = { state.eventSink(SpaceDocsListEvent.UpdateName(it)) },
            onSubmit = { state.eventSink(SpaceDocsListEvent.SubmitName) },
            onDismiss = { state.eventSink(SpaceDocsListEvent.DismissNameSheet) },
        )
    }
}

@Composable
private fun DocsListTitle(spaceName: String?) {
    Column {
        Text(
            text = stringResource(CommonStrings.common_matcha_docs),
            style = ElementTheme.typography.aliasScreenTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (spaceName != null) {
            Text(
                text = spaceName,
                style = ElementTheme.typography.fontBodyMdRegular,
                color = ElementTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocumentRows(state: SpaceDocsListState, content: SpaceDocsListContent.Documents) {
    val renameLabel = stringResource(CommonStrings.action_matcha_rename)
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(content.documents, key = { it.id }) { row ->
            DocRowContent(
                title = row.title ?: stringResource(R.string.screen_matcha_docs_main_name),
                subtitle = row.editedAt?.let { stringResource(R.string.screen_matcha_docs_edited, it) },
                modifier = Modifier.combinedClickable(
                    onClick = { state.eventSink(SpaceDocsListEvent.OpenDocument(row.id)) },
                    // Renaming writes the catalog, so it needs the same right as creating.
                    onLongClick = { state.eventSink(SpaceDocsListEvent.Rename(row.id)) }.takeIf { state.canCreate },
                    onLongClickLabel = renameLabel,
                ),
            )
        }
        if (content.documents.size == 1) {
            item {
                Text(
                    text = stringResource(R.string.screen_matcha_docs_list_empty),
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        }
    }
}

private const val PLACEHOLDER_ROWS = 3

@PreviewsDayNight
@Composable
internal fun SpaceDocsListViewPreview(@PreviewParameter(SpaceDocsListStatePreviewParam::class) state: SpaceDocsListState) = ElementPreview {
    SpaceDocsListView(state = state, onBackClick = {})
}
