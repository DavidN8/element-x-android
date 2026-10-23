/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.matcha.impl.R
import io.element.android.features.matcha.impl.docs.DocNameSheet
import io.element.android.features.matcha.impl.docs.DocsMessagePage
import io.element.android.features.matcha.impl.docs.DocsNoticeStrip
import io.element.android.features.matcha.impl.docs.engine.TextDiff
import io.element.android.libraries.androidutils.system.startSharePlainTextIntent
import io.element.android.libraries.designsystem.atomic.atoms.PlaceholderAtom
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.components.dialogs.ConfirmationDialog
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.aliasChatTitle
import io.element.android.libraries.designsystem.theme.components.DropdownMenu
import io.element.android.libraries.designsystem.theme.components.DropdownMenuItem
import io.element.android.libraries.designsystem.theme.components.FloatingActionButton
import io.element.android.libraries.designsystem.theme.components.HorizontalDivider
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.IconButton
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.libraries.textcomposer.R as TextComposerR

@Composable
fun DocEditorView(
    state: DocEditorState,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.screen_matcha_docs_share_text)
    fun share() = context.startSharePlainTextIntent(activityResultLauncher = null, chooserTitle = shareTitle, text = state.text)
    BackHandler(enabled = state.isEditing || state.hasUnsavedChanges) { state.eventSink(DocEditorEvent.Back) }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                navigationIcon = { BackButton(onClick = { state.eventSink(DocEditorEvent.Back) }) },
                title = { DocTitle(state) },
                actions = { DocActions(state, onShare = ::share) },
            )
        },
        floatingActionButton = {
            if (state.canEdit && !state.isEditing && !state.isLoadError) {
                FloatingActionButton(onClick = { state.eventSink(DocEditorEvent.StartEditing) }) {
                    Icon(imageVector = CompoundIcons.Edit(), contentDescription = stringResource(R.string.a11y_matcha_doc_edit))
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .consumeWindowInsets(padding)
                .fillMaxSize(),
        ) {
            state.notice?.let { DocNoticeStrip(it) }
            when {
                state.isLoadError -> DocsMessagePage(
                    icon = CompoundIcons.Warning(),
                    title = stringResource(R.string.screen_matcha_docs_load_error_title),
                    message = stringResource(R.string.screen_matcha_docs_load_error_body),
                    actionText = stringResource(CommonStrings.action_retry),
                    onAction = { state.eventSink(DocEditorEvent.Retry) },
                )
                state.status == DocStatus.Loading && state.text.isEmpty() -> DocSkeleton()
                state.isEditing -> DocTextEditor(state)
                state.text.isBlank() -> Text(
                    text = stringResource(if (state.canEdit) R.string.screen_matcha_docs_doc_empty else R.string.screen_matcha_docs_doc_empty_read_only),
                    style = ElementTheme.typography.fontBodyMdRegular,
                    color = ElementTheme.colors.textSecondary,
                    modifier = Modifier.padding(16.dp),
                )
                else -> MarkdownPreview(markdown = state.text, modifier = Modifier.fillMaxSize())
            }
        }
    }
    if (state.showLeaveDialog) {
        ConfirmationDialog(
            title = stringResource(R.string.screen_matcha_docs_unsaved_title),
            content = stringResource(R.string.screen_matcha_docs_unsaved_body),
            submitText = stringResource(CommonStrings.action_leave),
            destructiveSubmit = true,
            thirdButtonText = shareTitle,
            onThirdButtonClick = {
                state.eventSink(DocEditorEvent.CancelLeave)
                share()
            },
            cancelText = stringResource(R.string.screen_matcha_docs_stay),
            onSubmitClick = { state.eventSink(DocEditorEvent.ConfirmLeave) },
            onDismiss = { state.eventSink(DocEditorEvent.CancelLeave) },
        )
    }
    state.nameSheet?.let { sheet ->
        DocNameSheet(
            state = sheet,
            onNameChange = { state.eventSink(DocEditorEvent.UpdateName(it)) },
            onSubmit = { state.eventSink(DocEditorEvent.SubmitName) },
            onDismiss = { state.eventSink(DocEditorEvent.DismissNameSheet) },
        )
    }
}

@Composable
private fun DocTitle(state: DocEditorState) {
    val isFailed = state.status == DocStatus.Failed
    val retryLabel = stringResource(CommonStrings.action_retry)
    // When a save failed, the whole title retries, so the target is large enough to tap.
    val modifier = if (isFailed) {
        Modifier
            .minimumInteractiveComponentSize()
            .clickable(onClickLabel = retryLabel, role = Role.Button) { state.eventSink(DocEditorEvent.Retry) }
    } else {
        Modifier
    }
    Column(modifier = modifier, verticalArrangement = Arrangement.Center) {
        Text(
            text = state.title ?: stringResource(R.string.screen_matcha_docs_main_name),
            style = ElementTheme.typography.aliasChatTitle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = stringResource(
                when (state.status) {
                    DocStatus.Loading -> CommonStrings.common_loading
                    DocStatus.Saving -> CommonStrings.common_saving
                    DocStatus.Saved -> R.string.screen_matcha_docs_status_saved
                    DocStatus.Offline -> R.string.screen_matcha_docs_status_offline
                    DocStatus.Failed -> R.string.screen_matcha_docs_status_failed
                    DocStatus.ViewOnly -> R.string.screen_matcha_docs_status_view_only
                    DocStatus.EarlierChat -> R.string.screen_matcha_docs_status_earlier
                }
            ),
            style = ElementTheme.typography.fontBodyMdRegular,
            color = if (isFailed) ElementTheme.colors.textCriticalPrimary else ElementTheme.colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DocActions(state: DocEditorState, onShare: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (!state.isLoadError) {
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(imageVector = CompoundIcons.OverflowVertical(), contentDescription = stringResource(R.string.a11y_matcha_docs_more))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    if (state.canRename) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.screen_matcha_docs_rename)) },
                            onClick = {
                                showMenu = false
                                state.eventSink(DocEditorEvent.Rename)
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.screen_matcha_docs_share_text)) },
                        onClick = {
                            showMenu = false
                            onShare()
                        },
                    )
                }
            }
        }
        if (state.isEditing) {
            IconButton(onClick = { state.eventSink(DocEditorEvent.StopEditing) }) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(ElementTheme.colors.bgAccentRest, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = CompoundIcons.Check(),
                        contentDescription = stringResource(CommonStrings.action_done),
                        tint = ElementTheme.colors.iconOnSolidPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun DocNoticeStrip(notice: DocNotice) {
    DocsNoticeStrip(
        icon = if (notice == DocNotice.Offline) CompoundIcons.Offline() else CompoundIcons.Warning(),
        text = stringResource(
            when (notice) {
                DocNotice.Offline -> R.string.screen_matcha_docs_offline_strip
                DocNotice.DecryptError -> R.string.screen_matcha_docs_decrypt_error
                DocNotice.Incomplete -> R.string.screen_matcha_docs_incomplete
                DocNotice.Invalid -> R.string.screen_matcha_docs_invalid_update
                DocNotice.PermissionError -> R.string.screen_matcha_docs_permission_error
                DocNotice.TooLarge -> R.string.screen_matcha_docs_too_large
            }
        ),
    )
}

@Composable
private fun DocSkeleton() {
    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PlaceholderAtom(width = 200.dp, height = 20.dp)
        repeat(SKELETON_LINES) { PlaceholderAtom(width = 280.dp, height = 14.dp) }
    }
}

/** The plain text field of edit mode, with the formatting row docked above the keyboard. */
@Composable
private fun DocTextEditor(state: DocEditorState) {
    var value by remember { mutableStateOf(TextFieldValue(state.text, TextRange(state.text.length))) }
    // Edits from others arrive between keystrokes; the cursor keeps its place in the text around it.
    LaunchedEffect(state.text, state.isApplyingEdits) {
        if (!state.isApplyingEdits && state.text != value.text) {
            val changes = TextDiff.changes(value.text, state.text)
            value = TextFieldValue(
                text = state.text,
                selection = TextRange(TextDiff.mapOffset(value.selection.start, changes), TextDiff.mapOffset(value.selection.end, changes)),
            )
        }
    }
    fun update(newValue: TextFieldValue) {
        if (newValue.text != value.text) state.eventSink(DocEditorEvent.Edit(value.text, newValue.text))
        value = newValue
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        BasicTextField(
            value = value,
            onValueChange = ::update,
            textStyle = ElementTheme.typography.fontBodyLgRegular.copy(lineHeight = 24.sp, color = ElementTheme.colors.textPrimary),
            cursorBrush = SolidColor(ElementTheme.colors.textActionAccent),
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp)
                .focusRequester(focusRequester),
        )
        FormattingRow(value = value, onFormat = { update(MarkdownFormatting.apply(it, value)) })
    }
}

@Composable
private fun FormattingRow(value: TextFieldValue, onFormat: (FormatCommand) -> Unit) {
    Column(modifier = Modifier.background(ElementTheme.colors.bgCanvasDefault)) {
        HorizontalDivider(color = ElementTheme.colors.separatorPrimary)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FormatCommand.entries.forEach { command ->
                val description = stringResource(command.description())
                val isActive = MarkdownFormatting.isActive(command, value)
                IconButton(
                    onClick = { onFormat(command) },
                    modifier = Modifier
                        .background(if (isActive) ElementTheme.colors.bgAccentSubtle else ElementTheme.colors.bgCanvasDefault, CircleShape)
                        .semantics { contentDescription = description },
                ) {
                    val icon = command.icon()
                    if (icon == null) {
                        Text(
                            text = stringResource(R.string.screen_matcha_docs_heading_glyph),
                            style = ElementTheme.typography.fontBodyLgMedium,
                            color = ElementTheme.colors.iconSecondary,
                        )
                    } else {
                        Icon(imageVector = icon, contentDescription = null, tint = ElementTheme.colors.iconSecondary)
                    }
                }
            }
        }
    }
}

/** The heading button is a text glyph, as in WhatsApp's formatting row. */
@Composable
private fun FormatCommand.icon(): ImageVector? = when (this) {
    FormatCommand.Heading -> null
    FormatCommand.Bold -> CompoundIcons.Bold()
    FormatCommand.Italic -> CompoundIcons.Italic()
    FormatCommand.Strikethrough -> CompoundIcons.Strikethrough()
    FormatCommand.BulletList -> CompoundIcons.ListBulleted()
    FormatCommand.NumberedList -> CompoundIcons.ListNumbered()
    FormatCommand.Quote -> CompoundIcons.Quote()
    FormatCommand.InlineCode -> CompoundIcons.InlineCode()
    FormatCommand.CodeBlock -> CompoundIcons.Code()
    FormatCommand.Link -> CompoundIcons.Link()
    FormatCommand.Checklist -> CompoundIcons.CheckCircle()
}

private fun FormatCommand.description() = when (this) {
    FormatCommand.Heading -> R.string.a11y_matcha_format_heading
    FormatCommand.Bold -> TextComposerR.string.rich_text_editor_format_bold
    FormatCommand.Italic -> TextComposerR.string.rich_text_editor_format_italic
    FormatCommand.Strikethrough -> TextComposerR.string.rich_text_editor_format_strikethrough
    FormatCommand.BulletList -> TextComposerR.string.rich_text_editor_bullet_list
    FormatCommand.NumberedList -> TextComposerR.string.rich_text_editor_numbered_list
    FormatCommand.Quote -> TextComposerR.string.rich_text_editor_quote
    FormatCommand.InlineCode -> TextComposerR.string.rich_text_editor_inline_code
    FormatCommand.CodeBlock -> TextComposerR.string.rich_text_editor_code_block
    FormatCommand.Link -> TextComposerR.string.rich_text_editor_link
    FormatCommand.Checklist -> R.string.a11y_matcha_format_checklist
}

private const val SKELETON_LINES = 4

@PreviewsDayNight
@Composable
internal fun DocEditorViewPreview(@PreviewParameter(DocEditorStatePreviewParam::class) state: DocEditorState) = ElementPreview {
    DocEditorView(state = state)
}
