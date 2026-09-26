/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.onboarding

import com.google.common.truth.Truth.assertThat
import io.element.android.appconfig.AuthenticationConfig
import io.element.android.appconfig.OnBoardingConfig
import io.element.android.features.enterprise.api.EnterpriseService
import io.element.android.features.enterprise.api.IsEnterpriseBuild
import io.element.android.features.enterprise.test.FakeEnterpriseService
import io.element.android.features.login.impl.accesscontrol.DefaultAccountProviderAccessControl
import io.element.android.features.login.impl.accountprovider.AccountProviderDataSource
import io.element.android.features.login.impl.accountprovider.SaveAccountProviderToHistory
import io.element.android.features.login.impl.accountprovider.anAccountProviderDataSource
import io.element.android.features.login.impl.error.ChangeServerError
import io.element.android.features.login.impl.localnetwork.LocalNetworkPermissionGate
import io.element.android.features.login.impl.login.LoginModePresenter
import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.core.meta.BuildMeta
import io.element.android.libraries.matrix.api.auth.AuthenticationException
import io.element.android.libraries.matrix.api.auth.MatrixAuthenticationService
import io.element.android.libraries.matrix.api.auth.MatrixHomeServerDetails
import io.element.android.libraries.matrix.test.AN_ACCOUNT_PROVIDER
import io.element.android.libraries.matrix.test.AN_ACCOUNT_PROVIDER_2
import io.element.android.libraries.matrix.test.AN_ACCOUNT_PROVIDER_3
import io.element.android.libraries.matrix.test.AN_EXCEPTION
import io.element.android.libraries.matrix.test.A_HOMESERVER_URL
import io.element.android.libraries.matrix.test.A_HOMESERVER_URL_2
import io.element.android.libraries.matrix.test.A_LOGIN_HINT
import io.element.android.libraries.matrix.test.auth.FakeMatrixAuthenticationService
import io.element.android.libraries.matrix.test.core.aBuildMeta
import io.element.android.libraries.oauth.api.OAuthActionFlow
import io.element.android.libraries.oauth.test.FakeOAuthActionFlow
import io.element.android.libraries.permissions.api.PermissionsPresenter
import io.element.android.libraries.permissions.api.localnetwork.LocalNetworkPermissionAdvisor
import io.element.android.libraries.permissions.test.FakeLocalNetworkPermissionAdvisor
import io.element.android.libraries.permissions.test.FakePermissionsPresenterFactory
import io.element.android.libraries.preferences.test.InMemoryAppPreferencesStore
import io.element.android.libraries.sessionstorage.api.SessionStore
import io.element.android.libraries.sessionstorage.test.InMemorySessionStore
import io.element.android.libraries.sessionstorage.test.aSessionData
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.awaitLastSequentialItem
import io.element.android.tests.testutils.consumeItemsUntilPredicate
import io.element.android.tests.testutils.lambda.lambdaRecorder
import io.element.android.tests.testutils.lambda.value
import io.element.android.tests.testutils.test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class OnBoardingPresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    companion object {
        private const val ACCOUNT_PROVIDER_FROM_LINK = AN_ACCOUNT_PROVIDER
        private const val ACCOUNT_PROVIDER_FROM_CONFIG = AN_ACCOUNT_PROVIDER_2
        private const val ACCOUNT_PROVIDER_FROM_CONFIG_2 = AN_ACCOUNT_PROVIDER_3
    }

    @Test
    fun `present - ensure initial conditions`() {
        assertThat(
            setOf(
                ACCOUNT_PROVIDER_FROM_LINK,
                ACCOUNT_PROVIDER_FROM_CONFIG,
                ACCOUNT_PROVIDER_FROM_CONFIG_2,
            ).size
        ).isEqualTo(3)
    }

    @Test
    fun `present - initial state`() = runTest {
        val buildMeta = aBuildMeta(
            applicationName = "A",
            productionApplicationName = "B",
            desktopApplicationName = "C",
        )
        val presenter = createPresenter(
            buildMeta = buildMeta,
            enterpriseService = FakeEnterpriseService(
                defaultHomeserverListResult = { listOf(ACCOUNT_PROVIDER_FROM_CONFIG, EnterpriseService.ANY_ACCOUNT_PROVIDER) },
            ),
        )
        presenter.test {
            val initialState = awaitItem()
            assertThat(initialState.showBackButton).isFalse()
            assertThat(initialState.defaultAccountProvider).isNull()
            assertThat(initialState.canLoginWithQrCode).isFalse()
            assertThat(initialState.productionApplicationName).isEqualTo("B")
            assertThat(initialState.canCreateAccount).isEqualTo(OnBoardingConfig.CAN_CREATE_ACCOUNT)
            assertThat(initialState.canReportBug).isFalse()
            assertThat(initialState.isAddingAccount).isFalse()
            val finalState = awaitItem()
            assertThat(finalState.canLoginWithQrCode).isTrue()
        }
    }

    @Test
    fun `present - initial state with back button`() = runTest {
        val presenter = createPresenter(
            params = OnBoardingNode.Params(
                accountProvider = null,
                loginHint = null,
                showBackButton = true,
            ),
        )
        presenter.test {
            val initialState = awaitItem()
            assertThat(initialState.showBackButton).isTrue()
            skipItems(1)
        }
    }

    @Test
    fun `present - initial state adding account`() = runTest {
        val presenter = createPresenter(
            sessionStore = InMemorySessionStore(
                initialList = listOf(
                    aSessionData()
                )
            )
        )
        presenter.test {
            skipItems(1)
            val initialState = awaitItem()
            assertThat(initialState.isAddingAccount).isTrue()
        }
    }

    @Test
    fun `present - on boarding logo`() = runTest {
        val presenter = createPresenter(
            onBoardingLogoResIdProvider = OnBoardingLogoResIdProvider { 42 },
        )
        presenter.test {
            skipItems(1)
            val initialState = awaitItem()
            assertThat(initialState.onBoardingLogoResId).isEqualTo(42)
        }
    }

    @Test
    fun `present - clicking on version 7 times has no effect if rageshake not available`() = runTest {
        val presenter = createPresenter(
            rageshakeFeatureAvailability = { flowOf(false) },
        )
        presenter.test {
            skipItems(1)
            awaitItem().also { state ->
                assertThat(state.canReportBug).isFalse()
                repeat(7) {
                    state.eventSink(OnBoardingEvent.OnVersionClick)
                }
            }
            expectNoEvents()
        }
    }

    @Test
    fun `present - clicking on version 7 times will reveal the report a problem button`() = runTest {
        val presenter = createPresenter()
        presenter.test {
            skipItems(1)
            awaitItem().also { state ->
                assertThat(state.canReportBug).isFalse()
                repeat(7) {
                    state.eventSink(OnBoardingEvent.OnVersionClick)
                }
            }
            assertThat(awaitItem().canReportBug).isTrue()
        }
    }

    @Test
    fun `present - opening the app using link with allowed account provider, and the app does not force account provider`() = runTest {
        val presenter = createPresenter(
            params = OnBoardingNode.Params(
                accountProvider = ACCOUNT_PROVIDER_FROM_LINK,
                loginHint = null,
                showBackButton = false,
            ),
            enterpriseService = FakeEnterpriseService(
                defaultHomeserverListResult = { listOf(ACCOUNT_PROVIDER_FROM_CONFIG, EnterpriseService.ANY_ACCOUNT_PROVIDER) },
                isAllowedToConnectToHomeserverResult = { true },
                isElementProEnforcedResult = { false },
            ),
        )
        presenter.test {
            skipItems(3)
            awaitItem().also {
                assertThat(it.defaultAccountProvider).isEqualTo(ACCOUNT_PROVIDER_FROM_LINK)
                assertThat(it.canLoginWithQrCode).isFalse()
                assertThat(it.canCreateAccount).isFalse()
            }
        }
    }

    @Test
    fun `present - opening the app using link with not allowed account provider, and the app does not force account provider`() = runTest {
        val presenter = createPresenter(
            params = OnBoardingNode.Params(
                accountProvider = ACCOUNT_PROVIDER_FROM_LINK,
                loginHint = null,
                showBackButton = false,
            ),
            enterpriseService = FakeEnterpriseService(
                defaultHomeserverListResult = { listOf(ACCOUNT_PROVIDER_FROM_CONFIG, ACCOUNT_PROVIDER_FROM_CONFIG_2) },
                isAllowedToConnectToHomeserverResult = { false },
            ),
        )
        presenter.test {
            skipItems(1)
            awaitItem().also {
                assertThat(it.defaultAccountProvider).isNull()
                assertThat(it.canLoginWithQrCode).isTrue()
                assertThat(it.canCreateAccount).isFalse()
            }
        }
    }

    @Test
    fun `present - opening the app using link, and the app forces account provider`() = runTest {
        val presenter = createPresenter(
            params = OnBoardingNode.Params(
                accountProvider = ACCOUNT_PROVIDER_FROM_LINK,
                loginHint = null,
                showBackButton = false,
            ),
            enterpriseService = FakeEnterpriseService(
                defaultHomeserverListResult = { listOf(ACCOUNT_PROVIDER_FROM_CONFIG) },
            )
        )
        presenter.test {
            skipItems(1)
            awaitItem().also {
                assertThat(it.defaultAccountProvider).isEqualTo(ACCOUNT_PROVIDER_FROM_CONFIG)
                assertThat(it.canLoginWithQrCode).isTrue()
                assertThat(it.canCreateAccount).isFalse()
            }
        }
    }

    @Test
    fun `present - opening the app using link with allowed account provider, and the app forces account provider`() = runTest {
        val presenter = createPresenter(
            params = OnBoardingNode.Params(
                accountProvider = ACCOUNT_PROVIDER_FROM_LINK,
                loginHint = A_LOGIN_HINT,
                showBackButton = false,
            ),
            enterpriseService = FakeEnterpriseService(
                defaultHomeserverListResult = { listOf(ACCOUNT_PROVIDER_FROM_CONFIG) },
                isAllowedToConnectToHomeserverResult = { true },
                isElementProEnforcedResult = { false },
            )
        )
        presenter.test {
            // The link's account provider passed the allowlist: it wins over the forced one
            val state = consumeItemsUntilPredicate { it.defaultAccountProvider == ACCOUNT_PROVIDER_FROM_LINK }.last()
            assertThat(state.defaultAccountProvider).isEqualTo(ACCOUNT_PROVIDER_FROM_LINK)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - a link for a family on its own domain signs in through discovery of that domain`() = runTest {
        val setHomeserverResult = lambdaRecorder<String, Result<MatrixHomeServerDetails>> {
            // smith.ie resolved to a homeserver outside the allowlist: the backstop refused it before any login
            Result.failure(AuthenticationException.HomeserverNotAllowed("https://smith.ie/"))
        }
        val presenter = createPresenter(
            params = OnBoardingNode.Params(
                accountProvider = "smith.ie",
                loginHint = "mxid:@kid:smith.ie",
                showBackButton = false,
            ),
            enterpriseService = FakeEnterpriseService(
                defaultHomeserverListResult = { listOf(ACCOUNT_PROVIDER_FROM_CONFIG) },
                forcedAccountProviderResult = { null },
                // Not a family homeserver itself, but a well-formed server name: judged on where it resolves
                isAllowedToConnectToHomeserverResult = { false },
                isAllowedAccountProviderResult = { it == "smith.ie" },
                isElementProEnforcedResult = { false },
            ),
            loginModePresenter = createLoginModePresenter(
                authenticationService = FakeMatrixAuthenticationService(setHomeserverResult = setHomeserverResult),
            ),
        )
        presenter.test {
            // The family's own domain is used as the link's account provider, as for any family server
            val state = consumeItemsUntilPredicate { it.defaultAccountProvider == "smith.ie" && !it.canLoginWithQrCode }.last()
            state.eventSink(OnBoardingEvent.OnSignIn("smith.ie"))
            val failure = consumeItemsUntilPredicate { it.loginModeState.loginMode is AsyncData.Failure }.last()
            assertThat(failure.loginModeState.loginMode.errorOrNull()).isEqualTo(ChangeServerError.HomeserverNotAllowed)
            setHomeserverResult.assertions().isCalledOnce().with(value("smith.ie"))
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `present - a link whose account provider is the safechat_family apex is not used`() = runTest {
        val presenter = createPresenter(
            params = OnBoardingNode.Params(
                accountProvider = "safechat.family",
                loginHint = null,
                showBackButton = false,
            ),
            enterpriseService = FakeEnterpriseService(
                defaultHomeserverListResult = { listOf(ACCOUNT_PROVIDER_FROM_CONFIG) },
                forcedAccountProviderResult = { null },
                isAllowedToConnectToHomeserverResult = { false },
                isAllowedAccountProviderResult = { false },
                isElementProEnforcedResult = { false },
            ),
        )
        presenter.test {
            awaitLastSequentialItem().also {
                assertThat(it.defaultAccountProvider).isNull()
                assertThat(it.canLoginWithQrCode).isTrue()
            }
        }
    }

    @Test
    fun `present - a single allowlist entry that is not forced lets the user enter their server`() = runTest {
        val presenter = createPresenter(
            enterpriseService = FakeEnterpriseService(
                defaultHomeserverListResult = { listOf(ACCOUNT_PROVIDER_FROM_CONFIG) },
                forcedAccountProviderResult = { null },
            )
        )
        presenter.test {
            awaitLastSequentialItem().also {
                assertThat(it.defaultAccountProvider).isNull()
                assertThat(it.mustChooseAccountProvider).isFalse()
                assertThat(it.canCreateAccount).isFalse()
            }
        }
    }

    @Test
    fun `present - default account provider - login and clear error`() = runTest {
        val authenticationService = FakeMatrixAuthenticationService(
            setHomeserverResult = {
                Result.failure(AN_EXCEPTION)
            },
        )
        val accountProviderDataSource = anAccountProviderDataSource()
        val presenter = createPresenter(
            params = OnBoardingNode.Params(
                accountProvider = A_HOMESERVER_URL,
                loginHint = A_LOGIN_HINT,
                showBackButton = false,
            ),
            enterpriseService = FakeEnterpriseService(
                isAllowedToConnectToHomeserverResult = { true },
                isElementProEnforcedResult = { false },
            ),
            loginModePresenter = createLoginModePresenter(
                authenticationService = authenticationService,
            ),
            accountProviderDataSource = accountProviderDataSource,
        )
        presenter.test {
            skipItems(3)
            awaitItem().also {
                assertThat(it.defaultAccountProvider).isEqualTo(A_HOMESERVER_URL)
                assertThat(accountProviderDataSource.flow.first().url).isEqualTo(AuthenticationConfig.MATRIX_ORG_URL)
                it.eventSink(OnBoardingEvent.OnSignIn(A_HOMESERVER_URL_2))
                skipItems(1) // Loading
                // Account data source has been updated
                assertThat(accountProviderDataSource.flow.first().url).isEqualTo(A_HOMESERVER_URL_2)
                // Check an error was returned
                val submittedState = awaitItem()
                assertThat(submittedState.loginModeState.loginMode).isInstanceOf(AsyncData.Failure::class.java)

                // Assert the error is then cleared
                submittedState.eventSink(OnBoardingEvent.ClearError)
                val clearedState = awaitItem()
                assertThat(clearedState.loginModeState.loginMode).isEqualTo(AsyncData.Uninitialized)
            }
        }
    }
}

private fun createPresenter(
    params: OnBoardingNode.Params = OnBoardingNode.Params(
        accountProvider = null,
        loginHint = null,
        showBackButton = false,
    ),
    buildMeta: BuildMeta = aBuildMeta(),
    enterpriseService: EnterpriseService = FakeEnterpriseService(),
    isEnterpriseBuild: IsEnterpriseBuild = { false },
    rageshakeFeatureAvailability: () -> Flow<Boolean> = { flowOf(true) },
    loginModePresenter: LoginModePresenter = createLoginModePresenter(),
    onBoardingLogoResIdProvider: OnBoardingLogoResIdProvider = OnBoardingLogoResIdProvider { null },
    sessionStore: SessionStore = InMemorySessionStore(),
    accountProviderDataSource: AccountProviderDataSource = anAccountProviderDataSource(),
) = OnBoardingPresenter(
    params = params,
    buildMeta = buildMeta,
    enterpriseService = enterpriseService,
    defaultAccountProviderAccessControl = DefaultAccountProviderAccessControl(
        enterpriseService = enterpriseService,
        isEnterpriseBuild = isEnterpriseBuild,
    ),
    rageshakeFeatureAvailability = rageshakeFeatureAvailability,
    loginModePresenter = loginModePresenter,
    onBoardingLogoResIdProvider = onBoardingLogoResIdProvider,
    sessionStore = sessionStore,
    accountProviderDataSource = accountProviderDataSource,
)

fun createLoginModePresenter(
    oAuthActionFlow: OAuthActionFlow = FakeOAuthActionFlow(),
    authenticationService: MatrixAuthenticationService = FakeMatrixAuthenticationService(),
    localNetworkPermissionAdvisor: LocalNetworkPermissionAdvisor =
        FakeLocalNetworkPermissionAdvisor(),
    permissionsPresenterFactory: PermissionsPresenter.Factory =
        FakePermissionsPresenterFactory(),
    saveAccountProviderToHistory: SaveAccountProviderToHistory =
        SaveAccountProviderToHistory(anAccountProviderDataSource(), InMemoryAppPreferencesStore()),
): LoginModePresenter = LoginModePresenter(
    oAuthActionFlow = oAuthActionFlow,
    authenticationService = authenticationService,
    localNetworkPermissionGate = LocalNetworkPermissionGate(
        advisor = localNetworkPermissionAdvisor,
        permissionsPresenterFactory = permissionsPresenterFactory,
    ),
    saveAccountProviderToHistory = saveAccountProviderToHistory,
)
