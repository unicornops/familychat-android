/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.tokenlogin

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
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
import io.element.android.libraries.designsystem.atomic.molecules.IconTitleSubtitleMolecule
import io.element.android.libraries.designsystem.components.BigIcon
import io.element.android.libraries.designsystem.components.dialogs.ErrorDialog
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.CircularProgressIndicator
import io.element.android.libraries.designsystem.theme.components.Scaffold
import io.element.android.libraries.matrix.api.auth.AuthErrorCode
import io.element.android.libraries.matrix.api.auth.AuthenticationException
import io.element.android.libraries.matrix.api.auth.errorCode
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun TokenLoginView(
    state: TokenLoginState,
    modifier: Modifier = Modifier,
) {
    Scaffold(modifier = modifier) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconTitleSubtitleMolecule(
                    iconStyle = BigIcon.Style.Default(CompoundIcons.Lock()),
                    title = stringResource(R.string.screen_token_login_title),
                    subTitle = stringResource(R.string.screen_token_login_subtitle, state.homeserver),
                )
                Spacer(modifier = Modifier.height(32.dp))
                if (state.loginAction is AsyncData.Loading) {
                    CircularProgressIndicator()
                }
            }
        }
    }

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
 * A used or expired code is a 403 from the homeserver; anything else (unreachable server, unexpected answer)
 * gets a generic message. Both end in the same place: the password sign-in.
 */
private fun tokenLoginError(throwable: Throwable): Int {
    val authException = throwable as? AuthenticationException ?: return R.string.screen_token_login_error_generic
    return when {
        authException is AuthenticationException.ServerUnreachable -> R.string.screen_token_login_error_generic
        authException.errorCode == AuthErrorCode.FORBIDDEN -> R.string.screen_token_login_error_code_rejected
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
