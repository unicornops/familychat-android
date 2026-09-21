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
import io.element.android.libraries.matrix.api.auth.AuthErrorCode
import io.element.android.libraries.matrix.api.auth.AuthenticationException
import io.element.android.libraries.matrix.api.auth.errorCode
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
        val exchangeResult = lambdaRecorder<String, String, String, LoginTokenCredentials> { _, _, _ ->
            LoginTokenCredentials(
                userId = A_USER_ID.value,
                accessToken = "syt_access",
                deviceId = A_DEVICE_ID.value,
                refreshToken = null,
            )
        }
        val sut = createRustMatrixAuthenticationService(
            sessionStore = sessionStore,
            clientBuilderProvider = FakeClientBuilderProvider(
                provideResult = {
                    FakeFfiClientBuilder(
                        buildResult = { FakeFfiClient(withUtdHook = {}) }
                    )
                }
            ),
            loginTokenExchanger = FakeLoginTokenExchanger(exchangeResult),
        )

        val result = sut.loginWithToken(homeserverUrl = "https://smith.safechat.family", token = "syl_token")

        assertThat(result.getOrNull()).isEqualTo(A_SESSION_ID)
        exchangeResult.assertions().isCalledOnce().with(value("https://smith.safechat.family"), value("syl_token"), any())
        val stored = sessionStore.getSession(A_USER_ID.value)
        assertThat(stored).isNotNull()
        assertThat(stored!!.loginType).isEqualTo(LoginType.DIRECT)
        assertThat(stored.homeserverUrl).isEqualTo("https://smith.safechat.family")
        assertThat(stored.isTokenValid).isTrue()
    }

    @Test
    fun `loginWithToken reports a refused code and tears the temporary client down`() = runTest {
        val closeResult = lambdaRecorder<Unit> {}
        val sessionStore = InMemorySessionStore()
        val sut = createRustMatrixAuthenticationService(
            sessionStore = sessionStore,
            clientBuilderProvider = FakeClientBuilderProvider(
                provideResult = {
                    FakeFfiClientBuilder(
                        buildResult = { FakeFfiClient(withUtdHook = {}, closeResult = closeResult) }
                    )
                }
            ),
            loginTokenExchanger = FakeLoginTokenExchanger { _, _, _ ->
                throw AuthenticationException.Generic("M_FORBIDDEN: Invalid login token")
            },
        )

        val result = sut.loginWithToken(homeserverUrl = "https://smith.safechat.family", token = "syl_token")

        assertThat(result.isFailure).isTrue()
        assertThat((result.exceptionOrNull() as AuthenticationException).errorCode).isEqualTo(AuthErrorCode.FORBIDDEN)
        assertThat(sessionStore.getAllSessions()).isEmpty()
        closeResult.assertions().isCalledOnce()
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
        )
    }
}

private class FakeLoginTokenExchanger(
    private val exchangeResult: (String, String, String) -> LoginTokenCredentials = { _, _, _ -> lambdaError() },
) : LoginTokenExchanger {
    override suspend fun exchange(homeserverUrl: String, token: String, initialDeviceDisplayName: String): LoginTokenCredentials {
        return exchangeResult(homeserverUrl, token, initialDeviceDisplayName)
    }
}
