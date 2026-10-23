/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.element.android.appconfig.MatchaConfig
import io.element.android.features.matcha.impl.R
import io.element.android.features.matcha.impl.ui.SheetHeader
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.ModalBottomSheet
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.designsystem.theme.components.TextFieldValidity
import io.element.android.libraries.designsystem.theme.components.hide
import io.element.android.libraries.ui.strings.CommonStrings

/** The New doc and Rename doc sheet. */
data class DocNameSheetState(
    /** Null for a new doc. */
    val documentId: String?,
    val name: String,
    val error: DocNameError? = null,
    val isSaving: Boolean = false,
    /** A new doc whose first save failed: submitting again saves it rather than adding another. */
    val unsavedDocumentId: String? = null,
)

enum class DocNameError { Empty, TooLong, SaveFailed }

/** The error to show for [name] while typing; an empty name is only reported on submit. */
fun docNameError(name: String): DocNameError? = if (name.trim().length > MatchaConfig.DOCUMENT_NAME_MAX_LENGTH) DocNameError.TooLong else null

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocNameSheet(
    state: DocNameSheetState,
    onNameChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden, enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded))
    val coroutineScope = rememberCoroutineScope()
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, scrollable = true) {
        DocNameSheetContent(state = state, onNameChange = onNameChange, onSubmit = onSubmit, onClose = { sheetState.hide(coroutineScope, onDismiss) })
    }
}

@Composable
private fun DocNameSheetContent(
    state: DocNameSheetState,
    onNameChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onClose: () -> Unit,
) {
    val title = stringResource(if (state.documentId == null) R.string.screen_matcha_docs_new else R.string.screen_matcha_docs_rename_title)
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    Column(modifier = Modifier.semantics { paneTitle = title }) {
        SheetHeader(title = title, isBack = false, onNavigationClick = onClose)
        val error = when (state.error) {
            DocNameError.Empty -> stringResource(R.string.screen_matcha_docs_error_name_empty)
            DocNameError.TooLong -> stringResource(R.string.screen_matcha_docs_error_name_too_long, MatchaConfig.DOCUMENT_NAME_MAX_LENGTH)
            DocNameError.SaveFailed -> stringResource(R.string.screen_matcha_docs_write_failed)
            null -> null
        }
        TextField(
            value = state.name,
            onValueChange = onNameChange,
            label = stringResource(R.string.screen_matcha_docs_name_hint),
            supportingText = error,
            validity = if (error != null) TextFieldValidity.Invalid else TextFieldValidity.None,
            singleLine = true,
            enabled = !state.isSaving,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .focusRequester(focusRequester)
                .semantics { if (error != null) liveRegion = LiveRegionMode.Polite },
        )
        Button(
            text = stringResource(if (state.documentId == null) CommonStrings.action_create else CommonStrings.action_save),
            onClick = onSubmit,
            showProgress = state.isSaving,
            enabled = !state.isSaving && state.error != DocNameError.TooLong,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        )
    }
}

class DocNameSheetStatePreviewParam : PreviewParameterProvider<DocNameSheetState> {
    override val values = sequenceOf(
        DocNameSheetState(documentId = null, name = ""),
        DocNameSheetState(documentId = "main", name = "Space notes"),
        DocNameSheetState(documentId = null, name = "", error = DocNameError.Empty),
        DocNameSheetState(documentId = null, name = "A".repeat(101), error = DocNameError.TooLong),
        DocNameSheetState(documentId = null, name = "Trip plans", isSaving = true),
        DocNameSheetState(documentId = null, name = "Trip plans", error = DocNameError.SaveFailed),
    )
}

@PreviewsDayNight
@Composable
internal fun DocNameSheetContentPreview(@PreviewParameter(DocNameSheetStatePreviewParam::class) state: DocNameSheetState) = ElementPreview {
    DocNameSheetContent(state = state, onNameChange = {}, onSubmit = {}, onClose = {})
}
