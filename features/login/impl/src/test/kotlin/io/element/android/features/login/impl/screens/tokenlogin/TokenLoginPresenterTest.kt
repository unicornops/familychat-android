/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.tokenlogin

import com.google.common.truth.Truth.assertThat
import io.element.android.features.login.api.accesscontrol.AccountProviderAccessControl
import io.element.android.features.login.impl.tokenlogin.SignInCodeStore
import io.element.android.features.login.test.accesscontrol.FakeAccountProviderAccessControl
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.matrix.api.auth.SignInCodeException
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.auth.FakeMatrixAuthenticationService
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.lambda.lambdaRecorder
import io.element.android.tests.testutils.lambda.value
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

private const val A_HOST = "smith.safechat.family"
private const val A_TOKEN = "syl_secret_token"
private const val A_LOGIN_HINT = "mxid:@ana:smith.safechat.family"

class TokenLoginPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - asks for confirmation first, naming the account, and sends nothing until then`() = runTest {
        val loginWithTokenResult = lambdaRecorder<String, String, String?, Result<SessionId>> { _, _, _ -> Result.success(A_SESSION_ID) }
        val signInCodeStore = SignInCodeStore()
        val id = signInCodeStore.put(A_TOKEN)
        createTokenLoginPresenter(
            signInCodeId = id,
            signInCodeStore = signInCodeStore,
            authenticationService = FakeMatrixAuthenticationService(loginWithTokenResult = loginWithTokenResult),
        ).test {
            val initialState = awaitItem()
            assertThat(initialState.homeserver).isEqualTo(A_HOST)
            assertThat(initialState.userId).isEqualTo("@ana:smith.safechat.family")
            assertThat(initialState.accountDisplayName).isEqualTo("@ana:smith.safechat.family")
            assertThat(initialState.loginAction).isEqualTo(AsyncData.Uninitialized)
            loginWithTokenResult.assertions().isNeverCalled()
            assertThat(signInCodeStore.contains(id)).isTrue()
        }
    }

    @Test
    fun `present - without a usable login hint the confirmation names the host`() = runTest {
        createTokenLoginPresenter(loginHint = "not-an-mxid").test {
            val initialState = awaitItem()
            assertThat(initialState.userId).isNull()
            assertThat(initialState.accountDisplayName).isEqualTo(A_HOST)
        }
    }

    @Test
    fun `present - confirming redeems the code once against https hs, for the account the link named`() = runTest {
        val loginWithTokenResult = lambdaRecorder<String, String, String?, Result<SessionId>> { _, _, _ -> Result.success(A_SESSION_ID) }
        val signInCodeStore = SignInCodeStore()
        val id = signInCodeStore.put(A_TOKEN)
        createTokenLoginPresenter(
            signInCodeId = id,
            signInCodeStore = signInCodeStore,
            authenticationService = FakeMatrixAuthenticationService(loginWithTokenResult = loginWithTokenResult),
        ).test {
            val initialState = awaitItem()
            initialState.eventSink(TokenLoginEvent.Confirm)
            // A second tap does not send the code again
            initialState.eventSink(TokenLoginEvent.Confirm)
            assertThat(awaitItem().loginAction).isInstanceOf(AsyncData.Loading::class.java)
            assertThat(awaitItem().loginAction).isEqualTo(AsyncData.Success(A_SESSION_ID))
            loginWithTokenResult.assertions().isCalledOnce()
                .with(value("https://$A_HOST"), value(A_TOKEN), value("@ana:smith.safechat.family"))
            // The code has left the store
            assertThat(signInCodeStore.contains(id)).isFalse()
        }
    }

    @Test
    fun `present - a host starting with http is still redeemed over https`() = runTest {
        val loginWithTokenResult = lambdaRecorder<String, String, String?, Result<SessionId>> { _, _, _ -> Result.success(A_SESSION_ID) }
        val signInCodeStore = SignInCodeStore()
        createTokenLoginPresenter(
            hs = "httpfamily.safechat.family",
            signInCodeId = signInCodeStore.put(A_TOKEN),
            signInCodeStore = signInCodeStore,
            authenticationService = FakeMatrixAuthenticationService(loginWithTokenResult = loginWithTokenResult),
        ).test {
            awaitItem().eventSink(TokenLoginEvent.Confirm)
            skipItems(2)
            loginWithTokenResult.assertions().isCalledOnce()
                .with(value("https://httpfamily.safechat.family"), value(A_TOKEN), value("@ana:smith.safechat.family"))
        }
    }

    @Test
    fun `present - cancelling forgets the code and hands over to the password flow`() = runTest {
        val onContinueWithPassword = lambdaRecorder<Unit> {}
        val loginWithTokenResult = lambdaRecorder<String, String, String?, Result<SessionId>> { _, _, _ -> Result.success(A_SESSION_ID) }
        val signInCodeStore = SignInCodeStore()
        val id = signInCodeStore.put(A_TOKEN)
        createTokenLoginPresenter(
            signInCodeId = id,
            signInCodeStore = signInCodeStore,
            authenticationService = FakeMatrixAuthenticationService(loginWithTokenResult = loginWithTokenResult),
            onContinueWithPassword = onContinueWithPassword,
        ).test {
            awaitItem().eventSink(TokenLoginEvent.ContinueWithPassword)
            onContinueWithPassword.assertions().isCalledOnce()
            loginWithTokenResult.assertions().isNeverCalled()
            assertThat(signInCodeStore.contains(id)).isFalse()
        }
    }

    @Test
    fun `present - a used or expired code fails and continuing hands over to the password flow`() = runTest {
        val onContinueWithPassword = lambdaRecorder<Unit> {}
        val signInCodeStore = SignInCodeStore()
        createTokenLoginPresenter(
            signInCodeId = signInCodeStore.put(A_TOKEN),
            signInCodeStore = signInCodeStore,
            authenticationService = FakeMatrixAuthenticationService(
                loginWithTokenResult = { _, _, _ -> Result.failure(SignInCodeException.Rejected(httpStatus = 403, errcode = "M_FORBIDDEN")) },
            ),
            onContinueWithPassword = onContinueWithPassword,
        ).test {
            awaitItem().eventSink(TokenLoginEvent.Confirm)
            skipItems(1)
            val failedState = awaitItem()
            assertThat((failedState.loginAction as AsyncData.Failure).error).isInstanceOf(SignInCodeException.Rejected::class.java)
            onContinueWithPassword.assertions().isNeverCalled()
            failedState.eventSink(TokenLoginEvent.ContinueWithPassword)
            onContinueWithPassword.assertions().isCalledOnce()
        }
    }

    @Test
    fun `present - a code no longer in memory fails without contacting the server`() = runTest {
        val loginWithTokenResult = lambdaRecorder<String, String, String?, Result<SessionId>> { _, _, _ -> Result.success(A_SESSION_ID) }
        createTokenLoginPresenter(
            signInCodeId = "unknown-id",
            authenticationService = FakeMatrixAuthenticationService(loginWithTokenResult = loginWithTokenResult),
        ).test {
            awaitItem().eventSink(TokenLoginEvent.Confirm)
            skipItems(1)
            assertThat((awaitItem().loginAction as AsyncData.Failure).error).isInstanceOf(SignInCodeException.Unavailable::class.java)
            loginWithTokenResult.assertions().isNeverCalled()
        }
    }

    @Test
    fun `present - a homeserver outside the allowlist never receives the code`() = runTest {
        val loginWithTokenResult = lambdaRecorder<String, String, String?, Result<SessionId>> { _, _, _ -> Result.success(A_SESSION_ID) }
        val signInCodeStore = SignInCodeStore()
        val id = signInCodeStore.put(A_TOKEN)
        createTokenLoginPresenter(
            signInCodeId = id,
            signInCodeStore = signInCodeStore,
            authenticationService = FakeMatrixAuthenticationService(loginWithTokenResult = loginWithTokenResult),
            accountProviderAccessControl = FakeAccountProviderAccessControl(isAllowedToConnectToAccountProviderResult = { false }),
        ).test {
            awaitItem().eventSink(TokenLoginEvent.Confirm)
            skipItems(1)
            assertThat((awaitItem().loginAction as AsyncData.Failure).error).isInstanceOf(SignInCodeException.HomeserverNotAllowed::class.java)
            loginWithTokenResult.assertions().isNeverCalled()
            assertThat(signInCodeStore.contains(id)).isFalse()
        }
    }

    private fun createTokenLoginPresenter(
        hs: String = A_HOST,
        loginHint: String? = A_LOGIN_HINT,
        signInCodeStore: SignInCodeStore = SignInCodeStore(),
        signInCodeId: String = signInCodeStore.put(A_TOKEN),
        authenticationService: FakeMatrixAuthenticationService = FakeMatrixAuthenticationService(),
        accountProviderAccessControl: AccountProviderAccessControl = FakeAccountProviderAccessControl(isAllowedToConnectToAccountProviderResult = { true }),
        onContinueWithPassword: () -> Unit = {},
    ) = TokenLoginPresenter(
        params = TokenLoginPresenter.Params(hs = hs, loginHint = loginHint, signInCodeId = signInCodeId),
        onContinueWithPassword = onContinueWithPassword,
        authenticationService = authenticationService,
        accountProviderAccessControl = accountProviderAccessControl,
        signInCodeStore = signInCodeStore,
    )
}
