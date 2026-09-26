/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.tokenlogin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.features.login.api.accesscontrol.AccountProviderAccessControl
import io.element.android.features.login.impl.tokenlogin.SignInCodeStore
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.matrix.api.auth.MatrixAuthenticationService
import io.element.android.libraries.matrix.api.auth.SignInCodeException
import io.element.android.libraries.matrix.api.core.MatrixPatterns
import io.element.android.libraries.matrix.api.core.SessionId
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Redeems the single-use sign-in code carried by a control panel link (`hs` + `token`), once the user has
 * confirmed the account it signs into: a link can be sent by anyone, and must not sign a device into an account
 * its user did not ask for (login CSRF).
 *
 * On success the session is stored and the app switches to the logged-in flow by itself; on any failure, or if the
 * user declines, they continue with the usual password sign-in, pre-filled from the same link. The token is taken
 * out of [SignInCodeStore] only to be handed to the authentication service; it is never logged.
 */
@AssistedInject
class TokenLoginPresenter(
    @Assisted private val params: Params,
    @Assisted private val onContinueWithPassword: () -> Unit,
    private val authenticationService: MatrixAuthenticationService,
    private val accountProviderAccessControl: AccountProviderAccessControl,
    private val signInCodeStore: SignInCodeStore,
) : Presenter<TokenLoginState> {
    data class Params(
        /** Bare host (optionally `:port`) answering the client-server API; the code is redeemed against `https://<hs>`. */
        val hs: String,
        /**
         * The link's `account_provider`: the family's server name. It differs from [hs] for a family on its own
         * domain; with no login hint, the code must sign into an account on it.
         */
        val accountProvider: String,
        /** The link's `login_hint`, `mxid:<Matrix ID>`. */
        val loginHint: String?,
        /** Names the code in [SignInCodeStore]. */
        val signInCodeId: String,
    )

    @AssistedFactory
    interface Factory {
        fun create(params: Params, onContinueWithPassword: () -> Unit): TokenLoginPresenter
    }

    /** The Matrix ID named by the link, if the hint is a valid one. */
    private val expectedUserId: String? = params.loginHint
        ?.removePrefix("mxid:")
        ?.takeIf { MatrixPatterns.isUserId(it) }

    @Composable
    override fun present(): TokenLoginState {
        val coroutineScope = rememberCoroutineScope()
        val loginAction: MutableState<AsyncData<SessionId>> = remember {
            mutableStateOf(AsyncData.Uninitialized)
        }

        fun handleEvent(event: TokenLoginEvent) {
            when (event) {
                TokenLoginEvent.Confirm -> {
                    if (loginAction.value !is AsyncData.Uninitialized) return
                    loginAction.value = AsyncData.Loading()
                    coroutineScope.launch {
                        loginAction.value = redeem().fold(
                            onSuccess = { AsyncData.Success(it) },
                            onFailure = { AsyncData.Failure(it) },
                        )
                    }
                }
                TokenLoginEvent.ContinueWithPassword -> {
                    signInCodeStore.discard(params.signInCodeId)
                    onContinueWithPassword()
                }
            }
        }

        return TokenLoginState(
            serverName = params.accountProvider,
            userId = expectedUserId,
            loginAction = loginAction.value,
            eventSink = ::handleEvent,
        )
    }

    private suspend fun redeem(): Result<SessionId> {
        // The code is handed out once, whatever happens next: it is never sent twice.
        val token = signInCodeStore.consume(params.signInCodeId)
            ?: return Result.failure(SignInCodeException.Unavailable())
        // `hs` is a validated bare host (see DefaultLoginIntentResolver): always https, never ensureProtocol(),
        // which leaves a host starting with "http" alone.
        val homeserverUrl = "https://${params.hs}"
        if (!accountProviderAccessControl.isAllowedToConnectToHomeserver(homeserverUrl)) {
            // Defence in depth: the root flow already strips codes for disallowed hosts.
            Timber.w("Sign-in code refused: not allowed to connect to its homeserver")
            return Result.failure(SignInCodeException.HomeserverNotAllowed())
        }
        // The authentication service completes the redemption even if this screen goes away meanwhile.
        return authenticationService.loginWithToken(
            homeserverUrl = homeserverUrl,
            token = token,
            expectedUserId = expectedUserId,
            accountProvider = params.accountProvider,
        )
    }
}
