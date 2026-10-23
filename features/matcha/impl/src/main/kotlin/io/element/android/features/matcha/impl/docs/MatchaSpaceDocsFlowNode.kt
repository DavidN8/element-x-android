/*
 * Copyright (c) 2026 Element Creations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.matcha.impl.docs

import android.os.Parcelable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.lifecycleScope
import com.bumble.appyx.core.lifecycle.subscribe
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.node.node
import com.bumble.appyx.core.plugin.Plugin
import com.bumble.appyx.navmodel.backstack.BackStack
import com.bumble.appyx.navmodel.backstack.operation.pop
import com.bumble.appyx.navmodel.backstack.operation.push
import com.bumble.appyx.navmodel.backstack.operation.replace
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.features.matcha.api.MatchaSpaceDocsEntryPoint
import io.element.android.features.matcha.impl.R
import io.element.android.features.matcha.impl.docs.editor.DocEditorPresenter
import io.element.android.features.matcha.impl.docs.editor.DocEditorView
import io.element.android.features.matcha.impl.docs.list.SpaceDocsListPresenter
import io.element.android.features.matcha.impl.docs.list.SpaceDocsListView
import io.element.android.features.matcha.impl.docs.setup.SpaceDocsSetup
import io.element.android.features.matcha.impl.docs.setup.SpaceDocsSetupPresenter
import io.element.android.features.matcha.impl.docs.setup.SpaceDocsSetupView
import io.element.android.libraries.architecture.BackstackView
import io.element.android.libraries.architecture.BaseFlowNode
import io.element.android.libraries.architecture.NodeInputs
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.architecture.inputs
import io.element.android.libraries.designsystem.components.button.BackButton
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.designsystem.theme.components.TopAppBar
import io.element.android.libraries.di.SessionScope
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.api.FeatureFlags
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The docs of a space, or a chat's earlier document: the list, the editor and the one-time setup. It opens the space and its
 * documents when created and closes them when it goes away. The back stack is not restored after process death, since the
 * documents it shows must be read again anyway.
 */
@ContributesNode(SessionScope::class)
@AssistedInject
class MatchaSpaceDocsFlowNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val contextFactory: SpaceDocsContextFactory,
    private val listPresenterFactory: SpaceDocsListPresenter.Factory,
    private val editorPresenterFactory: DocEditorPresenter.Factory,
    private val featureFlagService: FeatureFlagService,
) : BaseFlowNode<MatchaSpaceDocsFlowNode.NavTarget>(
    backstack = BackStack(initialElement = NavTarget.Loading, savedStateMap = null),
    buildContext = buildContext,
    plugins = plugins,
) {
    data class Inputs(val params: MatchaSpaceDocsEntryPoint.Params) : NodeInputs

    sealed interface NavTarget : Parcelable {
        @Parcelize
        data object Loading : NavTarget

        @Parcelize
        data object List : NavTarget

        @Parcelize
        data object Setup : NavTarget

        @Parcelize
        data class Editor(val documentId: String) : NavTarget
    }

    private val params = inputs<Inputs>().params
    private val callback: MatchaSpaceDocsEntryPoint.Callback = callback()
    private var docsContext: SpaceDocsContext? = null

    override fun onBuilt() {
        super.onBuilt()
        lifecycle.subscribe(onDestroy = { docsContext?.close() })
        lifecycleScope.launch {
            val isEarlier = params.legacyChatId != null
            val context = contextFactory.create(roomId = params.legacyChatId ?: params.spaceId, isEarlierChatDocument = isEarlier, parentScope = lifecycleScope)
            if (context == null) {
                Timber.w("Docs opened for a room that is not joined")
                callback.close()
                return@launch
            }
            docsContext = context
            val documentId = params.documentId?.takeIf(DocsProtocol::isDocumentId)
            backstack.replace(
                when {
                    isEarlier -> NavTarget.Editor(DocsProtocol.MAIN_DOCUMENT_ID)
                    documentId != null -> NavTarget.Editor(documentId)
                    else -> NavTarget.List
                }
            )
        }
    }

    override fun resolve(navTarget: NavTarget, buildContext: BuildContext): Node {
        val context = docsContext
        if (context == null || navTarget == NavTarget.Loading) return loadingNode(buildContext)
        return when (navTarget) {
            NavTarget.Loading -> loadingNode(buildContext)
            NavTarget.List -> {
                val presenter = listPresenterFactory.create(
                    context,
                    object : SpaceDocsListPresenter.Navigator {
                        override fun openDocument(documentId: String) = backstack.push(NavTarget.Editor(documentId))
                        override fun openSetup() = backstack.push(NavTarget.Setup)
                    },
                )
                val onBack = once(::close)
                node(buildContext) { modifier -> SpaceDocsListView(state = presenter.present(), onBackClick = onBack, modifier = modifier) }
            }
            NavTarget.Setup -> {
                val onDone = once(::closeScreen)
                val presenter = SpaceDocsSetupPresenter(SpaceDocsSetup(context.room), onDone = onDone)
                node(buildContext) { modifier -> SpaceDocsSetupView(state = presenter.present(), onCloseClick = onDone, modifier = modifier) }
            }
            is NavTarget.Editor -> {
                val presenter = editorPresenterFactory.create(context, navTarget.documentId, onDone = once(::closeScreen))
                node(buildContext) { modifier -> DocEditorView(state = presenter.present(), modifier = modifier) }
            }
        }
    }

    /** A screen leaves once, however often its back button is tapped. */
    private fun once(action: () -> Unit): () -> Unit {
        val isDone = AtomicBoolean(false)
        return { if (isDone.compareAndSet(false, true)) action() }
    }

    /** Back from a screen: to the one below it, or out of docs. */
    private fun closeScreen() {
        if (backstack.elements.value.size > 1) backstack.pop() else close()
    }

    private fun close() = callback.close()

    private fun loadingNode(buildContext: BuildContext) = node(buildContext) { modifier ->
        Scaffold(
            modifier = modifier,
            topBar = {
                TopAppBar(
                    titleStr = stringResource(R.string.screen_matcha_docs_title),
                    navigationIcon = { BackButton(onClick = ::close) },
                )
            },
        ) { padding ->
            Column(Modifier.padding(padding)) { repeat(LOADING_ROWS) { DocRowPlaceholder() } }
        }
    }

    @Composable
    override fun View(modifier: Modifier) {
        val isEnabled by remember { featureFlagService.isFeatureEnabledFlow(FeatureFlags.MatchaWorkspaces) }.collectAsState(initial = false)
        if (isEnabled) BackstackView(modifier = modifier)
    }

    private companion object {
        const val LOADING_ROWS = 3
    }
}
