/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl

import android.app.Activity
import android.os.Parcelable
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.bumble.appyx.core.lifecycle.subscribe
import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import com.bumble.appyx.navmodel.backstack.BackStack
import com.bumble.appyx.navmodel.backstack.operation.pop
import com.bumble.appyx.navmodel.backstack.operation.push
import com.bumble.appyx.navmodel.backstack.operation.replace
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedInject
import io.element.android.annotations.ContributesNode
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.login.api.LoginEntryPoint
import io.element.android.features.login.impl.accountprovider.AccountProviderDataSource
import io.element.android.features.login.impl.classic.ElementClassicConnection
import io.element.android.features.login.impl.qrcode.QrCodeLoginFlowNode
import io.element.android.features.login.impl.screens.chooseaccountprovider.ChooseAccountProviderNode
import io.element.android.features.login.impl.screens.classic.ClassicFlowNode
import io.element.android.features.login.impl.screens.confirmaccountprovider.ConfirmAccountProviderNode
import io.element.android.features.login.impl.screens.loginpassword.LoginPasswordNode
import io.element.android.features.login.impl.screens.onboarding.OnBoardingNode
import io.element.android.features.login.impl.screens.tokenlogin.TokenLoginNode
import io.element.android.features.login.impl.tokenlogin.SignInCodeStore
import io.element.android.features.preferences.api.PreferencesEntryPoint
import io.element.android.libraries.androidutils.browser.openUrlInChromeCustomTab
import io.element.android.libraries.architecture.BackstackView
import io.element.android.libraries.architecture.BaseFlowNode
import io.element.android.libraries.architecture.NodeInputs
import io.element.android.libraries.architecture.callback
import io.element.android.libraries.architecture.createNode
import io.element.android.libraries.architecture.inputs
import io.element.android.libraries.di.annotations.AppCoroutineScope
import io.element.android.libraries.matrix.api.auth.OAuthDetails
import io.element.android.libraries.matrix.api.core.MatrixPatterns
import io.element.android.libraries.oauth.api.OAuthAction
import io.element.android.libraries.oauth.api.OAuthActionFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

@ContributesNode(AppScope::class)
@AssistedInject
class LoginFlowNode(
    @Assisted buildContext: BuildContext,
    @Assisted plugins: List<Plugin>,
    private val accountProviderDataSource: AccountProviderDataSource,
    private val oAuthActionFlow: OAuthActionFlow,
    @AppCoroutineScope
    private val appCoroutineScope: CoroutineScope,
    private val elementClassicConnection: ElementClassicConnection,
    private val preferencesEntryPoint: PreferencesEntryPoint,
    private val signInCodeStore: SignInCodeStore,
) : BaseFlowNode<LoginFlowNode.NavTarget>(
    backstack = BackStack(
        // A control panel sign-in code is redeemed first; everything else starts as upstream does.
        initialElement = if (plugins.filterIsInstance<Params>().firstOrNull()?.hasSignInCode(signInCodeStore) == true) {
            NavTarget.TokenLogin
        } else {
            NavTarget.CheckClassicFlow
        },
        savedStateMap = buildContext.savedStateMap,
    ),
    buildContext = buildContext,
    plugins = plugins,
) {
    data class Params(
        val accountProvider: String?,
        val loginHint: String?,
        /** The host a sign-in code is redeemed against, and the server the password fallback then uses. */
        val hs: String? = null,
        /** Names the sign-in code in [SignInCodeStore]; the token itself is never part of the node inputs. */
        val signInCodeId: String? = null,
    ) : NodeInputs {
        fun hasSignInCode(signInCodeStore: SignInCodeStore): Boolean = !hs.isNullOrBlank() && signInCodeStore.contains(signInCodeId)
    }

    private val callback: LoginEntryPoint.Callback = callback()
    private var activity: Activity? = null
    private var darkTheme: Boolean = false

    private var externalAppStarted = false

    override fun onBuilt() {
        super.onBuilt()
        lifecycle.subscribe(
            onResume = {
                if (externalAppStarted) {
                    externalAppStarted = false
                    // Workaround to detect that the Custom Chrome Tab has been closed
                    // If there is no coming OidcAction (that would end this Node),
                    // consider that the user has cancelled the login
                    // by pressing back or by closing the Custom Chrome Tab.
                    lifecycleScope.launch {
                        delay(5000)
                        oAuthActionFlow.post(OAuthAction.GoBack(toUnblock = true))
                    }
                }
            }
        )
    }

    sealed interface NavTarget : Parcelable {
        @Parcelize
        data object CheckClassicFlow : NavTarget

        /**
         * Redeem the sign-in code from the link. Neither this parcelled target nor [Params] hold the code: it is in
         * [SignInCodeStore], in memory only, so a target restored after a process death finds no code and falls
         * back to [CheckClassicFlow].
         */
        @Parcelize
        data object TokenLogin : NavTarget

        @Parcelize
        data class OnBoarding(
            val showBackButton: Boolean,
        ) : NavTarget

        @Parcelize
        data object QrCode : NavTarget

        @Parcelize
        data object AppDeveloperSettings : NavTarget

        @Parcelize
        data class ConfirmAccountProvider(
            val isAccountCreation: Boolean,
        ) : NavTarget

        @Parcelize
        data object ChooseAccountProvider : NavTarget

        @Parcelize
        data class LoginPassword(
            val initialLogin: String = "",
        ) : NavTarget
    }

    override fun resolve(navTarget: NavTarget, buildContext: BuildContext): Node {
        return when (navTarget) {
            NavTarget.TokenLogin -> {
                val params = inputs<Params>()
                val hs = params.hs
                if (hs.isNullOrBlank() || !params.hasSignInCode(signInCodeStore)) {
                    // No code any more (the process was recreated, or it expired): this is the plain password flow.
                    return resolve(NavTarget.CheckClassicFlow, buildContext)
                }
                val callback = object : TokenLoginNode.Callback {
                    override fun onContinueWithPassword() {
                        // Hand over to the regular flow, which pre-fills the family's server and the login hint
                        // from the same link, so the user only has to type their password.
                        backstack.replace(NavTarget.CheckClassicFlow)
                    }
                }
                val inputs = TokenLoginNode.Inputs(
                    hs = hs,
                    // The family's server name; for a family on its own domain, not the host the code goes to.
                    accountProvider = params.accountProvider ?: hs,
                    loginHint = params.loginHint,
                    signInCodeId = params.signInCodeId.orEmpty(),
                )
                createNode<TokenLoginNode>(buildContext, plugins = listOf(inputs, callback))
            }
            NavTarget.CheckClassicFlow -> {
                val callback = object : ClassicFlowNode.Callback {
                    override fun navigateToOnBoarding(allowBackNavigation: Boolean) {
                        if (allowBackNavigation) {
                            backstack.push(NavTarget.OnBoarding(showBackButton = true))
                        } else {
                            backstack.replace(NavTarget.OnBoarding(showBackButton = false))
                        }
                    }

                    override fun navigateToLoginPassword() {
                        backstack.push(NavTarget.LoginPassword())
                    }

                    override fun navigateToOAuth(oAuthDetails: OAuthDetails) {
                        navigateToMas(oAuthDetails)
                    }
                }
                createNode<ClassicFlowNode>(buildContext, listOf(callback))
            }
            is NavTarget.OnBoarding -> {
                val callback = object : OnBoardingNode.Callback {
                    override fun navigateToSignUpFlow() {
                        backstack.push(
                            NavTarget.ConfirmAccountProvider(isAccountCreation = true)
                        )
                    }

                    override fun navigateToSignInFlow(mustChooseAccountProvider: Boolean) {
                        backstack.push(
                            if (mustChooseAccountProvider) {
                                NavTarget.ChooseAccountProvider
                            } else {
                                NavTarget.ConfirmAccountProvider(isAccountCreation = false)
                            }
                        )
                    }

                    override fun navigateToQrCode() {
                        backstack.push(NavTarget.QrCode)
                    }

                    override fun navigateToBugReport() {
                        callback.navigateToBugReport()
                    }

                    override fun navigateToOAuth(oAuthDetails: OAuthDetails) {
                        navigateToMas(oAuthDetails)
                    }

                    override fun navigateToDeveloperSettings() {
                        backstack.push(NavTarget.AppDeveloperSettings)
                    }

                    override fun navigateToLoginPassword() {
                        backstack.push(NavTarget.LoginPassword(initialLogin = linkUserId().orEmpty()))
                    }

                    override fun onDone() {
                        if (navTarget.showBackButton) {
                            backstack.pop()
                        } else {
                            callback.onDone()
                        }
                    }
                }
                val params = inputs<Params>()
                val inputs = OnBoardingNode.Params(
                    // After a sign-in code, the password fallback goes to the host the code was for: for a family
                    // with its own domain, `account_provider` only serves the well-known documents.
                    accountProvider = params.hs ?: params.accountProvider,
                    loginHint = params.loginHint,
                    showBackButton = navTarget.showBackButton,
                )
                createNode<OnBoardingNode>(buildContext, listOf(callback, inputs))
            }
            NavTarget.AppDeveloperSettings -> {
                val callback = object : PreferencesEntryPoint.DeveloperSettingsCallback {
                    override fun onDone() {
                        backstack.pop()
                    }
                }
                preferencesEntryPoint.createAppDeveloperSettingsNode(
                    parentNode = this,
                    buildContext = buildContext,
                    callback = callback,
                )
            }
            NavTarget.ChooseAccountProvider -> {
                val callback = object : ChooseAccountProviderNode.Callback {
                    override fun navigateToOAuth(oAuthDetails: OAuthDetails) {
                        navigateToMas(oAuthDetails)
                    }

                    override fun navigateToLoginPassword() {
                        backstack.push(NavTarget.LoginPassword())
                    }
                }
                createNode<ChooseAccountProviderNode>(buildContext, listOf(callback))
            }
            NavTarget.QrCode -> {
                val callback = object : QrCodeLoginFlowNode.Callback {
                    override fun navigateBack() {
                        backstack.pop()
                    }
                }
                createNode<QrCodeLoginFlowNode>(buildContext, listOf(callback))
            }
            is NavTarget.ConfirmAccountProvider -> {
                val inputs = ConfirmAccountProviderNode.Inputs(
                    isAccountCreation = navTarget.isAccountCreation,
                )
                val callback = object : ConfirmAccountProviderNode.Callback {
                    override fun navigateToOAuth(oAuthDetails: OAuthDetails) {
                        navigateToMas(oAuthDetails)
                    }

                    override fun navigateToLoginPassword() {
                        backstack.push(NavTarget.LoginPassword())
                    }
                }
                createNode<ConfirmAccountProviderNode>(buildContext, plugins = listOf(inputs, callback))
            }
            is NavTarget.LoginPassword -> {
                val inputs = LoginPasswordNode.Inputs(
                    initialLogin = navTarget.initialLogin,
                )
                createNode<LoginPasswordNode>(buildContext, plugins = listOf(inputs))
            }
        }
    }

    /** The Matrix ID named by the link's `login_hint`, used to pre-fill the password form. */
    private fun linkUserId(): String? = inputs<Params>().loginHint
        ?.removePrefix("mxid:")
        ?.takeIf { MatrixPatterns.isUserId(it) }

    private fun navigateToMas(oAuthDetails: OAuthDetails) {
        activity?.let {
            externalAppStarted = true
            it.openUrlInChromeCustomTab(null, darkTheme, oAuthDetails.url)
        }
    }

    @Composable
    override fun View(modifier: Modifier) {
        activity = requireNotNull(LocalActivity.current)
        darkTheme = !ElementTheme.isLightTheme

        DisposableEffect(Unit) {
            elementClassicConnection.start()
            onDispose {
                elementClassicConnection.stop()
            }
        }

        DisposableEffect(Unit) {
            onDispose {
                activity = null
                appCoroutineScope.launch {
                    accountProviderDataSource.reset()
                }
            }
        }
        BackstackView(transitionHandler = rememberLoginFlowTransitionHandler())
    }
}
