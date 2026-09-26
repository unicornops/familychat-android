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
import io.element.android.libraries.matrix.api.auth.AuthenticationException
import io.element.android.libraries.matrix.api.auth.OAuthPrompt
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
                                homeserver = A_FAMILY_HOMESERVER_URL,
                                homeserverLoginDetailsResult = {
                                    FakeFfiHomeserverLoginDetails()
                                }
                            )
                        }
                    )
                }
            ),
        )
        assertThat(sut.setHomeserver("smith.safechat.family").isSuccess).isTrue()
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
                                homeserver = A_FAMILY_HOMESERVER_URL,
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
        assertThat(sut.setHomeserver("smith.safechat.family").isFailure).isTrue()
        closeResult.assertions().isCalledOnce()
    }

    @Test
    fun `setHomeserver accepts a family's own domain whose well-known resolves to a family server`() = runTest {
        val serverNameOrHomeserverUrlResult = lambdaRecorder<String, Unit> { }
        val loginResult = lambdaRecorder<String, String, Unit> { _, _ -> }
        val sut = createRustMatrixAuthenticationService(
            clientBuilderProvider = FakeClientBuilderProvider(
                provideResult = {
                    FakeFfiClientBuilder(
                        buildResult = {
                            FakeFfiClient(
                                // What discovery of smith.ie resolved to
                                homeserver = "https://smith-m1.safechat.family/",
                                homeserverLoginDetailsResult = { FakeFfiHomeserverLoginDetails() },
                                loginResult = loginResult,
                                withUtdHook = {},
                            )
                        },
                        serverNameOrHomeserverUrlResult = serverNameOrHomeserverUrlResult,
                    )
                }
            ),
            sessionStore = InMemorySessionStore(updateUserProfileResult = { _, _, _ -> }),
        )

        assertThat(sut.setHomeserver("smith.ie").isSuccess).isTrue()
        serverNameOrHomeserverUrlResult.assertions().isCalledOnce().with(value("smith.ie"))

        // The typed full Matrix ID on the family's own domain signs in on the resolved server
        assertThat(sut.login("@kid:smith.ie", "a password").isSuccess).isTrue()
        loginResult.assertions().isCalledOnce().with(value("@kid:smith.ie"), value("a password"))
    }

    @Test
    fun `setHomeserver refuses a domain resolving outside the allowlist, and no password is ever sent to it`() = runTest {
        val closeResult = lambdaRecorder<Unit> {}
        val loginResult = lambdaRecorder<String, String, Unit> { _, _ -> }
        val sut = createRustMatrixAuthenticationService(
            clientBuilderProvider = FakeClientBuilderProvider(
                provideResult = {
                    FakeFfiClientBuilder(
                        buildResult = {
                            FakeFfiClient(
                                // evil.com has no .well-known, or one pointing at itself
                                homeserver = "https://evil.com/",
                                // Not even the login flows are asked for
                                homeserverLoginDetailsResult = { lambdaError() },
                                loginResult = loginResult,
                                closeResult = closeResult,
                            )
                        },
                    )
                }
            ),
        )

        val result = sut.setHomeserver("evil.com")

        val error = result.exceptionOrNull()
        assertThat(error).isInstanceOf(AuthenticationException.HomeserverNotAllowed::class.java)
        assertThat((error as AuthenticationException.HomeserverNotAllowed).homeserverUrl).isEqualTo("https://evil.com/")
        closeResult.assertions().isCalledOnce()
        // The refused client is gone: a password login has nothing to go through
        assertThat(sut.login("@kid:evil.com", "a password").isFailure).isTrue()
        assertThat(sut.getOAuthUrl(prompt = OAuthPrompt.Login, loginHint = null).isFailure).isTrue()
        loginResult.assertions().isNeverCalled()
    }

    @Test
    fun `setHomeserver refuses a family server resolved over plain http`() = runTest {
        val sut = createRustMatrixAuthenticationService(
            clientBuilderProvider = FakeClientBuilderProvider(
                provideResult = {
                    FakeFfiClientBuilder(
                        buildResult = {
                            FakeFfiClient(homeserver = "http://smith.safechat.family/", homeserverLoginDetailsResult = { lambdaError() })
                        },
                    )
                }
            ),
        )

        assertThat(sut.setHomeserver("smith.ie").exceptionOrNull()).isInstanceOf(AuthenticationException.HomeserverNotAllowed::class.java)
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

        val result = sut.loginWithToken(
            homeserverUrl = "https://smith.safechat.family",
            token = "syl_token",
            expectedUserId = A_USER_ID.value,
            accountProvider = "smith.safechat.family",
        )

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

        val result = sut.loginWithToken(
            homeserverUrl = "https://smith.safechat.family",
            token = "syl_token",
            expectedUserId = null,
            accountProvider = "smith.safechat.family",
        )

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
            accountProvider = "smith.safechat.family",
        )

        assertThat(result.exceptionOrNull()).isInstanceOf(SignInCodeException.UserMismatch::class.java)
        assertThat(sessionStore.getAllSessions()).isEmpty()
        closeResult.assertions().isCalledOnce()
        logoutResult.assertions().isCalledOnce().with(value("https://smith.safechat.family"), value("syt_access"))
    }

    @Test
    fun `loginWithToken for a family on its own domain redeems against hs and accepts the account on the domain`() = runTest {
        listOf("@kid:smith.ie", null).forEach { expectedUserId ->
            val exchangeResult = lambdaRecorder<String, String, String, LoginTokenCredentials> { _, _, _ ->
                aLoginTokenCredentials(userId = "@kid:smith.ie")
            }
            val sut = createRustMatrixAuthenticationService(
                sessionStore = InMemorySessionStore(updateUserProfileResult = { _, _, _ -> }),
                clientBuilderProvider = aClientBuilderProvider(),
                loginTokenExchanger = FakeLoginTokenExchanger(exchangeResult = exchangeResult),
            )

            val result = sut.loginWithToken(
                homeserverUrl = "https://smith-m1.safechat.family",
                token = "syl_token",
                expectedUserId = expectedUserId,
                accountProvider = "smith.ie",
            )

            assertThat(result.isSuccess).isTrue()
            exchangeResult.assertions().isCalledOnce().with(value("https://smith-m1.safechat.family"), value("syl_token"), any())
        }
    }

    @Test
    fun `loginWithToken without a login hint refuses an account on another server than the account provider`() = runTest {
        listOf("@kid:evil.com", "@kid:smith-m1.safechat.family", "@kid:smith.ie.evil.com").forEach { userId ->
            val logoutResult = lambdaRecorder<String, String, Unit> { _, _ -> }
            val sessionStore = InMemorySessionStore()
            val sut = createRustMatrixAuthenticationService(
                sessionStore = sessionStore,
                clientBuilderProvider = aClientBuilderProvider(),
                loginTokenExchanger = FakeLoginTokenExchanger(
                    exchangeResult = { _, _, _ -> aLoginTokenCredentials(userId = userId) },
                    logoutResult = logoutResult,
                ),
            )

            val result = sut.loginWithToken(
                homeserverUrl = "https://smith-m1.safechat.family",
                token = "syl_token",
                expectedUserId = null,
                accountProvider = "smith.ie",
            )

            assertThat(result.exceptionOrNull()).isInstanceOf(SignInCodeException.UserMismatch::class.java)
            assertThat(sessionStore.getAllSessions()).isEmpty()
            logoutResult.assertions().isCalledOnce().with(value("https://smith-m1.safechat.family"), value("syt_access"))
        }
    }

    @Test
    fun `loginWithToken never sends the code to a homeserver outside the allowlist`() = runTest {
        listOf("https://smith.ie", "http://smith.safechat.family", "https://safechat.family").forEach { homeserverUrl ->
            val sut = createRustMatrixAuthenticationService(
                clientBuilderProvider = aClientBuilderProvider(),
                // The default exchange fails the test if the code is ever sent
                loginTokenExchanger = FakeLoginTokenExchanger(),
            )

            val result = sut.loginWithToken(homeserverUrl = homeserverUrl, token = "syl_token", expectedUserId = null, accountProvider = "smith.ie")

            assertThat(result.exceptionOrNull()).isInstanceOf(SignInCodeException.HomeserverNotAllowed::class.java)
        }
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
            sut.loginWithToken(
                homeserverUrl = "https://smith.safechat.family",
                token = "syl_token",
                expectedUserId = null,
                accountProvider = "server.org",
            )
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
        enterpriseService: EnterpriseService = FakeEnterpriseService(
            // The Family Chat rule for a resolved homeserver: https, and a family subdomain of safechat.family
            isAllowedResolvedHomeserverUrlResult = { url ->
                url.startsWith("https://") && url.removePrefix("https://").removeSuffix("/").endsWith(".safechat.family")
            },
        ),
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

private const val A_FAMILY_HOMESERVER_URL = "https://smith.safechat.family/"

private fun aLoginTokenCredentials(userId: String = A_USER_ID.value) = LoginTokenCredentials(
    userId = userId,
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
