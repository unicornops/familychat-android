/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.tokenlogin

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.libraries.architecture.NodeInputs
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.architecture.inputs

@ContributesNode(AppScope::class)
@AssistedInject
class TokenLoginNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    presenterFactory: TokenLoginPresenter.Factory,
) : Node(buildContext, plugins = plugins) {
    /**
     * The sign-in code itself is not here: [signInCodeId] names it in the in-memory `SignInCodeStore`.
     */
    data class Inputs(
        val hs: String,
        val loginHint: String?,
        val signInCodeId: String,
    ) : NodeInputs

    interface Callback : Plugin {
        /** The code was declined or could not be used: continue with the regular, pre-filled sign-in flow. */
        fun onContinueWithPassword()
    }

    private val inputs: Inputs = inputs()
    private val callback: Callback = callback()
    private val presenter = presenterFactory.create(
        params = TokenLoginPresenter.Params(hs = inputs.hs, loginHint = inputs.loginHint, signInCodeId = inputs.signInCodeId),
        onContinueWithPassword = callback::onContinueWithPassword,
    )

    @Composable
    override fun View(modifier: Modifier) {
        val state = presenter.present()
        TokenLoginView(
            state = state,
            modifier = modifier,
        )
    }
}
