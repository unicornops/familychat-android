/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.tokenlogin

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.features.login.impl.R
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.designsystem.atomic.pages.FlowStepPage
import io.element.android.libraries.designsystem.components.BigIcon
import io.element.android.libraries.designsystem.components.dialogs.ErrorDialog
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.matrix.api.auth.AuthenticationException
import io.element.android.libraries.matrix.api.auth.SignInCodeException
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun TokenLoginView(
    state: TokenLoginState,
    modifier: Modifier = Modifier,
) {
    val isAwaitingConfirmation = state.loginAction is AsyncData.Uninitialized
    FlowStepPage(
        modifier = modifier,
        iconStyle = BigIcon.Style.Default(CompoundIcons.Lock()),
        title = if (isAwaitingConfirmation) {
            stringResource(R.string.screen_token_login_confirm_title, state.accountDisplayName)
        } else {
            stringResource(R.string.screen_token_login_title)
        },
        subTitle = if (isAwaitingConfirmation) {
            stringResource(R.string.screen_token_login_confirm_subtitle, state.homeserver)
        } else {
            stringResource(R.string.screen_token_login_subtitle, state.homeserver)
        },
        content = {
            if (state.loginAction is AsyncData.Loading || state.loginAction is AsyncData.Success) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        },
        buttons = {
            if (isAwaitingConfirmation) {
                Button(
                    text = stringResource(CommonStrings.action_continue),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { state.eventSink(TokenLoginEvent.Confirm) },
                )
                TextButton(
                    text = stringResource(CommonStrings.action_cancel),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { state.eventSink(TokenLoginEvent.ContinueWithPassword) },
                )
            }
        },
    )

    if (state.loginAction is AsyncData.Failure) {
        ErrorDialog(
            title = stringResource(R.string.screen_token_login_error_title),
            content = stringResource(tokenLoginError(state.loginAction.error)),
            submitText = stringResource(CommonStrings.action_continue),
            onSubmit = { state.eventSink(TokenLoginEvent.ContinueWithPassword) },
            canDismiss = false,
        )
    }
}

/**
 * A used or expired code is an HTTP 401 or 403 from the homeserver, whatever its errcode. Every failure ends in the
 * same place, the password sign-in, but the copy says why.
 */
@StringRes
private fun tokenLoginError(throwable: Throwable): Int {
    return when (throwable) {
        is SignInCodeException.Rejected,
        is SignInCodeException.Unavailable -> R.string.screen_token_login_error_code_rejected
        is SignInCodeException.UserMismatch -> R.string.screen_token_login_error_user_mismatch
        is SignInCodeException.HomeserverNotAllowed -> R.string.screen_token_login_error_homeserver_not_allowed
        is AuthenticationException.AccountAlreadyLoggedIn -> R.string.screen_token_login_error_already_signed_in
        else -> R.string.screen_token_login_error_generic
    }
}

@PreviewsDayNight
@Composable
internal fun TokenLoginViewPreview(@PreviewParameter(TokenLoginStatePreviewParam::class) state: TokenLoginState) = ElementPreview {
    TokenLoginView(
        state = state,
    )
}
