/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.auth

import com.google.common.truth.Truth.assertThat
import io.element.android.features.enterprise.api.EnterpriseService
import io.element.android.features.enterprise.test.FakeEnterpriseService
import io.element.android.libraries.featureflag.test.FakeFeatureFlagService
import io.element.android.libraries.matrix.api.auth.SignInCodeException
import io.element.android.libraries.matrix.impl.ClientBuilderProvider
import io.element.android.libraries.matrix.impl.FakeClientBuilderProvider
import io.element.android.libraries.matrix.impl.createRustMatrixClientFactory
import io.element.android.libraries.matrix.impl.fixtures.fakes.FakeFfiClient
import io.element.android.libraries.matrix.impl.fixtures.fakes.FakeFfiClientBuilder
import io.element.android.libraries.matrix.impl.fixtures.fakes.FakeFfiHomeserverLoginDetails
import io.element.android.libraries.matrix.impl.paths.SessionPathsFactory
import io.element.android.libraries.matrix.test.A_DEVICE_ID
import io.element.android.libraries.matrix.test.A_SESSION_ID
import io.element.android.libraries.matrix.test.A_USER_ID
import io.element.android.libraries.matrix.test.auth.FakeOAuthRedirectUrlProvider
import io.element.android.libraries.matrix.test.core.aBuildMeta
import io.element.android.libraries.sessionstorage.api.LoginType
import io.element.android.libraries.sessionstorage.api.SessionStore
import io.element.android.libraries.sessionstorage.test.InMemorySessionStore
import io.element.android.libraries.workmanager.test.FakeWorkManagerScheduler
import io.element.android.tests.testutils.lambda.any
import io.element.android.tests.testutils.lambda.lambdaError
import io.element.android.tests.testutils.lambda.lambdaRecorder
import io.element.android.tests.testutils.lambda.value
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.File

class RustMatrixAuthenticationServiceTest {
    @Test
    fun `setHomeserver is successful`() = runTest {
        val sut = createRustMatrixAuthenticationService(
            clientBuilderProvider = FakeClientBuilderProvider(
                provideResult = {
                    FakeFfiClientBuilder(
                        buildResult = {
                            FakeFfiClient(
                                homeserverLoginDetailsResult = {
                                    FakeFfiHomeserverLoginDetails()
                                }
                            )
                        }
                    )
                }
            ),
        )
        assertThat(sut.setHomeserver("matrix.org").isSuccess).isTrue()
    }

    @Test
    fun `setHomeserver can fail gracefully and clean up the temporary client`() = runTest {
        val closeResult = lambdaRecorder<Unit> {}
        val sut = createRustMatrixAuthenticationService(
            clientBuilderProvider = FakeClientBuilderProvider(
                provideResult = {
                    FakeFfiClientBuilder(
                        buildResult = {
                            FakeFfiClient(
                                homeserverLoginDetailsResult = {
                                    throw IllegalStateException("Failed to get homeserver login details")
                                },
                                closeResult = closeResult,
                            )
                        },
                    )
                },
            ),
        )
        assertThat(sut.setHomeserver("matrix.org").isFailure).isTrue()
        closeResult.assertions().isCalledOnce()
    }

    @Test
    fun `loginWithToken redeems the code against the given homeserver and stores the session`() = runTest {
        val sessionStore = InMemorySessionStore(updateUserProfileResult = { _, _, _ -> })
        val exchangeResult = lambdaRecorder<String, String, String, LoginTokenCredentials> { _, _, _ -> aLoginTokenCredentials() }
        val logoutResult = lambdaRecorder<String, String, Unit> { _, _ -> }
        val sut = createRustMatrixAuthenticationService(
            sessionStore = sessionStore,
            clientBuilderProvider = aClientBuilderProvider(),
            loginTokenExchanger = FakeLoginTokenExchanger(exchangeResult, logoutResult),
        )

        val result = sut.loginWithToken(homeserverUrl = "https://smith.safechat.family", token = "syl_token", expectedUserId = A_USER_ID.value)

        assertThat(result.getOrNull()).isEqualTo(A_SESSION_ID)
        exchangeResult.assertions().isCalledOnce().with(value("https://smith.safechat.family"), value("syl_token"), any())
        logoutResult.assertions().isNeverCalled()
        val stored = sessionStore.getSession(A_USER_ID.value)
        assertThat(stored).isNotNull()
        assertThat(stored!!.loginType).isEqualTo(LoginType.DIRECT)
        assertThat(stored.homeserverUrl).isEqualTo("https://smith.safechat.family")
        assertThat(stored.isTokenValid).isTrue()
    }

    @Test
    fun `loginWithToken reports a refused code and tears the temporary client down`() = runTest {
        val closeResult = lambdaRecorder<Unit> {}
        val logoutResult = lambdaRecorder<String, String, Unit> { _, _ -> }
        val sessionStore = InMemorySessionStore()
        val sut = createRustMatrixAuthenticationService(
            sessionStore = sessionStore,
            clientBuilderProvider = aClientBuilderProvider(closeResult),
            loginTokenExchanger = FakeLoginTokenExchanger(
                exchangeResult = { _, _, _ -> throw SignInCodeException.Rejected(httpStatus = 403, errcode = "M_FORBIDDEN") },
                logoutResult = logoutResult,
            ),
        )

        val result = sut.loginWithToken(homeserverUrl = "https://smith.safechat.family", token = "syl_token", expectedUserId = null)

        assertThat(result.exceptionOrNull()).isInstanceOf(SignInCodeException.Rejected::class.java)
        assertThat(sessionStore.getAllSessions()).isEmpty()
        closeResult.assertions().isCalledOnce()
        // No device was created, so there is nothing to sign out
        logoutResult.assertions().isNeverCalled()
    }

    @Test
    fun `loginWithToken refuses a code for another account than the link named, and signs the new device out`() = runTest {
        val closeResult = lambdaRecorder<Unit> {}
        val logoutResult = lambdaRecorder<String, String, Unit> { _, _ -> }
        val sessionStore = InMemorySessionStore()
        val sut = createRustMatrixAuthenticationService(
            sessionStore = sessionStore,
            clientBuilderProvider = aClientBuilderProvider(closeResult),
            loginTokenExchanger = FakeLoginTokenExchanger(
                exchangeResult = { _, _, _ -> aLoginTokenCredentials() },
                logoutResult = logoutResult,
            ),
        )

        val result = sut.loginWithToken(
            homeserverUrl = "https://smith.safechat.family",
            token = "syl_token",
            expectedUserId = "@someone_else:smith.safechat.family",
        )

        assertThat(result.exceptionOrNull()).isInstanceOf(SignInCodeException.UserMismatch::class.java)
        assertThat(sessionStore.getAllSessions()).isEmpty()
        closeResult.assertions().isCalledOnce()
        logoutResult.assertions().isCalledOnce().with(value("https://smith.safechat.family"), value("syt_access"))
    }

    @Test
    fun `loginWithToken completes even when the caller is cancelled`() = runTest {
        val sessionStore = InMemorySessionStore(updateUserProfileResult = { _, _, _ -> })
        val exchangeStarted = CompletableDeferred<Unit>()
        val releaseExchange = CompletableDeferred<Unit>()
        val sut = createRustMatrixAuthenticationService(
            sessionStore = sessionStore,
            clientBuilderProvider = aClientBuilderProvider(),
            loginTokenExchanger = object : LoginTokenExchanger {
                override suspend fun exchange(homeserverUrl: String, token: String, initialDeviceDisplayName: String): LoginTokenCredentials {
                    exchangeStarted.complete(Unit)
                    releaseExchange.await()
                    return aLoginTokenCredentials()
                }

                override suspend fun logout(homeserverUrl: String, accessToken: String) = lambdaError()
            },
        )

        val caller = launch {
            sut.loginWithToken(homeserverUrl = "https://smith.safechat.family", token = "syl_token", expectedUserId = null)
        }
        exchangeStarted.await()
        caller.cancelAndJoin()
        releaseExchange.complete(Unit)

        // The server consumed the code: the session it issued is kept, not orphaned
        val sessions = sessionStore.sessionsFlow().first { it.isNotEmpty() }
        assertThat(sessions.single().userId).isEqualTo(A_USER_ID.value)
    }

    private fun TestScope.createRustMatrixAuthenticationService(
        sessionStore: SessionStore = InMemorySessionStore(),
        clientBuilderProvider: ClientBuilderProvider = FakeClientBuilderProvider(),
        enterpriseService: EnterpriseService = FakeEnterpriseService(),
        loginTokenExchanger: LoginTokenExchanger = FakeLoginTokenExchanger(),
    ): RustMatrixAuthenticationService {
        val baseDirectory = File("/base")
        val cacheDirectory = File("/cache")
        val rustMatrixClientFactory = createRustMatrixClientFactory(
            cacheDirectory = cacheDirectory,
            sessionStore = sessionStore,
            clientBuilderProvider = clientBuilderProvider,
            // A successful login schedules the session's background work; that is not what these tests check.
            workManagerScheduler = FakeWorkManagerScheduler(submitLambda = {}),
        )
        return RustMatrixAuthenticationService(
            sessionPathsFactory = SessionPathsFactory(baseDirectory, cacheDirectory),
            coroutineDispatchers = testCoroutineDispatchers(),
            sessionStore = sessionStore,
            rustMatrixClientFactory = rustMatrixClientFactory,
            secretGenerator = FakeSecretGenerator(),
            oAuthConfigurationProvider = OAuthConfigurationProvider(
                buildMeta = aBuildMeta(),
                oAuthRedirectUrlProvider = FakeOAuthRedirectUrlProvider(),
            ),
            enterpriseService = enterpriseService,
            featureFlagService = FakeFeatureFlagService(),
            clientEnterpriseHook = {},
            loginTokenExchanger = loginTokenExchanger,
            appCoroutineScope = backgroundScope,
        )
    }
}

private fun aLoginTokenCredentials() = LoginTokenCredentials(
    userId = A_USER_ID.value,
    accessToken = "syt_access",
    deviceId = A_DEVICE_ID.value,
    refreshToken = null,
)

private fun aClientBuilderProvider(closeResult: () -> Unit = {}) = FakeClientBuilderProvider(
    provideResult = {
        FakeFfiClientBuilder(
            buildResult = { FakeFfiClient(withUtdHook = {}, closeResult = closeResult) }
        )
    }
)

private class FakeLoginTokenExchanger(
    private val exchangeResult: (String, String, String) -> LoginTokenCredentials = { _, _, _ -> lambdaError() },
    private val logoutResult: (String, String) -> Unit = { _, _ -> lambdaError() },
) : LoginTokenExchanger {
    override suspend fun exchange(homeserverUrl: String, token: String, initialDeviceDisplayName: String): LoginTokenCredentials {
        return exchangeResult(homeserverUrl, token, initialDeviceDisplayName)
    }

    override suspend fun logout(homeserverUrl: String, accessToken: String) {
        logoutResult(homeserverUrl, accessToken)
    }
}
