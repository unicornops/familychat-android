/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.tokenlogin

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.matrix.api.auth.AuthenticationException
import io.element.android.libraries.matrix.api.auth.SignInCodeException
import io.element.android.libraries.matrix.api.core.SessionId

open class TokenLoginStatePreviewParam : PreviewParameterProvider<TokenLoginState> {
    override val values: Sequence<TokenLoginState>
        get() = sequenceOf(
            aTokenLoginState(),
            aTokenLoginState(userId = null),
            aTokenLoginState(loginAction = AsyncData.Loading()),
            aTokenLoginState(loginAction = AsyncData.Failure(SignInCodeException.Rejected(httpStatus = 403, errcode = "M_FORBIDDEN"))),
            aTokenLoginState(loginAction = AsyncData.Failure(SignInCodeException.UserMismatch())),
            aTokenLoginState(loginAction = AsyncData.Failure(SignInCodeException.HomeserverNotAllowed())),
            aTokenLoginState(loginAction = AsyncData.Failure(AuthenticationException.AccountAlreadyLoggedIn("@ana:smith.safechat.family"))),
            aTokenLoginState(loginAction = AsyncData.Failure(AuthenticationException.ServerUnreachable("timeout"))),
        )
}

fun aTokenLoginState(
    homeserver: String = "smith.safechat.family",
    userId: String? = "@ana:smith.safechat.family",
    loginAction: AsyncData<SessionId> = AsyncData.Uninitialized,
    eventSink: (TokenLoginEvent) -> Unit = {},
) = TokenLoginState(
    homeserver = homeserver,
    userId = userId,
    loginAction = loginAction,
    eventSink = eventSink,
)
