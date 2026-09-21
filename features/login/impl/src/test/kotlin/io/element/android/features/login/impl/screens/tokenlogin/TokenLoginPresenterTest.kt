/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.tokenlogin

import com.google.common.truth.Truth.assertThat
import io.element.android.features.login.api.accesscontrol.AccountProviderAccessControl
import io.element.android.features.login.impl.accountprovider.AccountProviderDataSource
import io.element.android.features.login.impl.accountprovider.anAccountProviderDataSource
import io.element.android.features.login.test.accesscontrol.FakeAccountProviderAccessControl
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.matrix.api.auth.AuthenticationException
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.auth.FakeMatrixAuthenticationService
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.lambda.lambdaRecorder
import io.element.android.tests.testutils.lambda.value
import io.element.android.tests.testutils.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

private const val A_HOST = "smith.safechat.family"
private const val A_TOKEN = "syl_secret_token"

class TokenLoginPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - redeems the code against https hs and succeeds`() = runTest {
        val loginWithTokenResult = lambdaRecorder<String, String, Result<SessionId>> { _, _ -> Result.success(A_SESSION_ID) }
        val authenticationService = FakeMatrixAuthenticationService(loginWithTokenResult = loginWithTokenResult)
        val accountProviderDataSource = anAccountProviderDataSource()
        createTokenLoginPresenter(
            authenticationService = authenticationService,
            accountProviderDataSource = accountProviderDataSource,
        ).test {
            val initialState = awaitItem()
            assertThat(initialState.homeserver).isEqualTo(A_HOST)
            assertThat(initialState.loginAction).isInstanceOf(AsyncData.Loading::class.java)
            val loggedInState = awaitItem()
            assertThat(loggedInState.loginAction).isEqualTo(AsyncData.Success(A_SESSION_ID))
            loginWithTokenResult.assertions().isCalledOnce().with(value("https://$A_HOST"), value(A_TOKEN))
            // The password fallback screens are pointed at the same server
            assertThat(accountProviderDataSource.flow.first().url).isEqualTo("https://$A_HOST")
        }
    }

    @Test
    fun `present - a used or expired code fails and continuing hands over to the password flow`() = runTest {
        val onTokenLoginFailed = lambdaRecorder<Unit> {}
        val authenticationService = FakeMatrixAuthenticationService(
            loginWithTokenResult = { _, _ -> Result.failure(AuthenticationException.Generic("M_FORBIDDEN: Invalid login token")) },
        )
        createTokenLoginPresenter(
            authenticationService = authenticationService,
            onTokenLoginFailed = onTokenLoginFailed,
        ).test {
            skipItems(1)
            val failedState = awaitItem()
            assertThat(failedState.loginAction).isInstanceOf(AsyncData.Failure::class.java)
            onTokenLoginFailed.assertions().isNeverCalled()
            failedState.eventSink(TokenLoginEvent.ContinueWithPassword)
            onTokenLoginFailed.assertions().isCalledOnce()
        }
    }

    @Test
    fun `present - a homeserver outside the allowlist never receives the code`() = runTest {
        val loginWithTokenResult = lambdaRecorder<String, String, Result<SessionId>> { _, _ -> Result.success(A_SESSION_ID) }
        val authenticationService = FakeMatrixAuthenticationService(loginWithTokenResult = loginWithTokenResult)
        createTokenLoginPresenter(
            authenticationService = authenticationService,
            accountProviderAccessControl = FakeAccountProviderAccessControl(isAllowedToConnectToAccountProviderResult = { false }),
        ).test {
            skipItems(1)
            val failedState = awaitItem()
            assertThat(failedState.loginAction).isInstanceOf(AsyncData.Failure::class.java)
            loginWithTokenResult.assertions().isNeverCalled()
        }
    }

    private fun createTokenLoginPresenter(
        hs: String = A_HOST,
        token: String = A_TOKEN,
        authenticationService: FakeMatrixAuthenticationService = FakeMatrixAuthenticationService(),
        accountProviderAccessControl: AccountProviderAccessControl = FakeAccountProviderAccessControl(isAllowedToConnectToAccountProviderResult = { true }),
        accountProviderDataSource: AccountProviderDataSource = anAccountProviderDataSource(),
        onTokenLoginFailed: () -> Unit = {},
    ) = TokenLoginPresenter(
        params = TokenLoginPresenter.Params(hs = hs, token = token),
        authenticationService = authenticationService,
        accountProviderAccessControl = accountProviderAccessControl,
        accountProviderDataSource = accountProviderDataSource,
        onTokenLoginFailed = onTokenLoginFailed,
    )
}
