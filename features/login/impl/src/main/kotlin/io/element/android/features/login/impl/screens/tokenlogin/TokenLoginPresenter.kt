/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.tokenlogin

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import io.element.android.features.login.api.accesscontrol.AccountProviderAccessControl
import io.element.android.features.login.impl.accountprovider.AccountProviderDataSource
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.core.uri.ensureProtocol
import io.element.android.libraries.matrix.api.auth.AuthenticationException
import io.element.android.libraries.matrix.api.auth.MatrixAuthenticationService
import io.element.android.libraries.matrix.api.core.SessionId
import timber.log.Timber

/**
 * Redeems the single-use sign-in code carried by a control panel link (`hs` + `token`) as soon as the screen
 * shows. On success the session is stored and the app switches to the logged-in flow by itself; on any failure
 * the user is told to ask for a new code and is handed over to the usual password sign-in, pre-filled from the
 * same link. The token is only ever passed to the authentication service; it is never logged.
 */
@AssistedInject
class TokenLoginPresenter(
    @Assisted private val params: Params,
    private val authenticationService: MatrixAuthenticationService,
    private val accountProviderAccessControl: AccountProviderAccessControl,
    private val accountProviderDataSource: AccountProviderDataSource,
    @Assisted private val onTokenLoginFailed: () -> Unit,
) : Presenter<TokenLoginState> {
    data class Params(
        /** Bare host (optionally `:port`) answering the client-server API; the code is redeemed against `https://<hs>`. */
        val hs: String,
        val token: String,
    )

    @AssistedFactory
    interface Factory {
        fun create(params: Params, onTokenLoginFailed: () -> Unit): TokenLoginPresenter
    }

    @Composable
    override fun present(): TokenLoginState {
        val loginAction: MutableState<AsyncData<SessionId>> = remember {
            mutableStateOf(AsyncData.Loading())
        }

        LaunchedEffect(Unit) {
            val homeserverUrl = params.hs.ensureProtocol()
            if (!accountProviderAccessControl.isAllowedToConnectToAccountProvider(homeserverUrl)) {
                // Defence in depth: the root flow already strips codes for disallowed hosts.
                Timber.w("Sign-in code refused: not allowed to connect to its homeserver")
                loginAction.value = AsyncData.Failure(AuthenticationException.Generic("M_FORBIDDEN: homeserver not allowed"))
                return@LaunchedEffect
            }
            // Keep the account provider in step for the password fallback screens.
            accountProviderDataSource.setUrl(homeserverUrl)
            authenticationService.loginWithToken(homeserverUrl = homeserverUrl, token = params.token)
                .onSuccess { sessionId ->
                    loginAction.value = AsyncData.Success(sessionId)
                }
                .onFailure { failure ->
                    loginAction.value = AsyncData.Failure(failure)
                }
        }

        fun handleEvent(event: TokenLoginEvent) {
            when (event) {
                TokenLoginEvent.ContinueWithPassword -> onTokenLoginFailed()
            }
        }

        return TokenLoginState(
            homeserver = params.hs,
            loginAction = loginAction.value,
            eventSink = ::handleEvent,
        )
    }
}
