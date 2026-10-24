/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.matcha.impl.R
import io.element.android.libraries.designsystem.components.BigIcon
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.components.dialogs.ConfirmationDialog
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.Icon
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun SpaceDocsSetupView(
    state: SpaceDocsSetupState,
    onCloseClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                titleStr = stringResource(R.string.screen_matcha_docs_setup_title),
                navigationIcon = {
                    BackButton(onClick = onCloseClick, imageVector = CompoundIcons.Close(), contentDescription = stringResource(CommonStrings.action_close))
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                BigIcon(style = BigIcon.Style.Default(CompoundIcons.Document()), modifier = Modifier.align(Alignment.CenterHorizontally))
                SetupRow(CompoundIcons.Group(), R.string.screen_matcha_docs_setup_together_title, R.string.screen_matcha_docs_setup_together_body)
                SetupRow(CompoundIcons.Lock(), R.string.screen_matcha_docs_setup_encrypted_title, R.string.screen_matcha_docs_setup_encrypted_body)
                SetupRow(CompoundIcons.Room(), R.string.screen_matcha_docs_setup_rooms_title, R.string.screen_matcha_docs_setup_rooms_body)
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
            ) {
                if (state.hasFailed) {
                    Text(
                        text = stringResource(R.string.screen_matcha_docs_setup_error),
                        style = ElementTheme.typography.fontBodyMdRegular,
                        color = ElementTheme.colors.textCriticalPrimary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                            .semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
                Button(
                    text = stringResource(if (state.isWorking) R.string.screen_matcha_docs_setup_progress else R.string.screen_matcha_docs_setup_action),
                    onClick = { state.eventSink(SpaceDocsSetupEvent.TurnOn) },
                    showProgress = state.isWorking,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    if (state.showEncryptionDialog) {
        ConfirmationDialog(
            title = stringResource(R.string.screen_matcha_docs_encrypt_dialog_title),
            content = stringResource(R.string.screen_matcha_docs_encrypt_dialog_body),
            submitText = stringResource(R.string.screen_matcha_docs_encrypt_dialog_action),
            onSubmitClick = { state.eventSink(SpaceDocsSetupEvent.ConfirmEncryption) },
            onDismiss = { state.eventSink(SpaceDocsSetupEvent.DismissEncryption) },
        )
    }
}

@Composable
private fun SetupRow(icon: ImageVector, title: Int, body: Int) {
    Row(modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Icon(imageVector = icon, contentDescription = null, tint = ElementTheme.colors.iconSecondary, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(text = stringResource(title), style = ElementTheme.typography.fontBodyLgMedium, color = ElementTheme.colors.textPrimary)
            Spacer(Modifier.height(2.dp))
            Text(text = stringResource(body), style = ElementTheme.typography.fontBodyMdRegular, color = ElementTheme.colors.textSecondary)
        }
    }
}

class SpaceDocsSetupStatePreviewParam : PreviewParameterProvider<SpaceDocsSetupState> {
    override val values = sequenceOf(
        aSpaceDocsSetupState(),
        aSpaceDocsSetupState(isWorking = true),
        aSpaceDocsSetupState(hasFailed = true),
        aSpaceDocsSetupState(showEncryptionDialog = true),
    )
}

private fun aSpaceDocsSetupState(isWorking: Boolean = false, hasFailed: Boolean = false, showEncryptionDialog: Boolean = false) =
    SpaceDocsSetupState(isWorking = isWorking, hasFailed = hasFailed, showEncryptionDialog = showEncryptionDialog, eventSink = {})

@PreviewsDayNight
@Composable
internal fun SpaceDocsSetupViewPreview(@PreviewParameter(SpaceDocsSetupStatePreviewParam::class) state: SpaceDocsSetupState) = ElementPreview {
    SpaceDocsSetupView(state = state, onCloseClick = {})
}
