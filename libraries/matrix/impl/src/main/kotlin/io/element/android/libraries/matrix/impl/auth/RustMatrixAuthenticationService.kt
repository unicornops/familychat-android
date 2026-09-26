/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.auth

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.SingleIn
import io.element.android.features.enterprise.api.ClientEnterpriseHook
import io.element.android.features.enterprise.api.EnterpriseService
import io.element.android.libraries.androidutils.crypto.ClientSecret
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.extensions.mapFailure
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.di.annotations.AppCoroutineScope
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.featureflag.api.FeatureFlags
import io.element.android.libraries.matrix.api.MatrixClient
import io.element.android.libraries.matrix.api.auth.AuthenticationException
import io.element.android.libraries.matrix.api.auth.ElementClassicSession
import io.element.android.libraries.matrix.api.auth.MatrixAuthenticationService
import io.element.android.libraries.matrix.api.auth.MatrixHomeServerDetails
import io.element.android.libraries.matrix.api.auth.OAuthDetails
import io.element.android.libraries.matrix.api.auth.OAuthPrompt
import io.element.android.libraries.matrix.api.auth.SessionRestorationException
import io.element.android.libraries.matrix.api.auth.SignInCodeException
import io.element.android.libraries.matrix.api.auth.qrlogin.MatrixQrCodeLoginData
import io.element.android.libraries.matrix.api.auth.qrlogin.QrCodeLoginStep
import io.element.android.libraries.matrix.api.auth.qrlogin.QrLoginException
import io.element.android.libraries.matrix.api.core.SessionId
import io.element.android.libraries.matrix.api.core.UserId
import io.element.android.libraries.matrix.api.paths.SessionPaths
import io.element.android.libraries.matrix.api.verification.SessionVerifiedStatus
import io.element.android.libraries.matrix.impl.ClientBuilderSlidingSync
import io.element.android.libraries.matrix.impl.RustMatrixClientFactory
import io.element.android.libraries.matrix.impl.RustTemporaryMatrixClient
import io.element.android.libraries.matrix.impl.auth.qrlogin.QrErrorMapper
import io.element.android.libraries.matrix.impl.auth.qrlogin.SdkQrCodeLoginData
import io.element.android.libraries.matrix.impl.auth.qrlogin.toStep
import io.element.android.libraries.matrix.impl.exception.mapClientException
import io.element.android.libraries.matrix.impl.keys.SecretGenerator
import io.element.android.libraries.matrix.impl.mapper.toSessionData
import io.element.android.libraries.matrix.impl.paths.SessionPathsFactory
import io.element.android.libraries.sessionstorage.api.LoginType
import io.element.android.libraries.sessionstorage.api.SessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.matrix.rustcomponents.sdk.Client
import org.matrix.rustcomponents.sdk.ClientBuilder
import org.matrix.rustcomponents.sdk.HumanQrLoginException
import org.matrix.rustcomponents.sdk.QrCodeData
import org.matrix.rustcomponents.sdk.QrCodeDecodeException
import org.matrix.rustcomponents.sdk.QrLoginProgress
import org.matrix.rustcomponents.sdk.QrLoginProgressListener
import org.matrix.rustcomponents.sdk.SecretsBundleWithUserId
import org.matrix.rustcomponents.sdk.Session
import org.matrix.rustcomponents.sdk.SlidingSyncVersion
import timber.log.Timber
import uniffi.matrix_sdk.OAuthAuthorizationData
import kotlin.time.Duration.Companion.seconds

@ContributesBinding(AppScope::class)
@SingleIn(AppScope::class)
class RustMatrixAuthenticationService(
    private val sessionPathsFactory: SessionPathsFactory,
    private val coroutineDispatchers: CoroutineDispatchers,
    private val sessionStore: SessionStore,
    private val rustMatrixClientFactory: RustMatrixClientFactory,
    private val secretGenerator: SecretGenerator,
    private val oAuthConfigurationProvider: OAuthConfigurationProvider,
    private val enterpriseService: EnterpriseService,
    private val featureFlagService: FeatureFlagService,
    private val clientEnterpriseHook: ClientEnterpriseHook,
    private val loginTokenExchanger: LoginTokenExchanger,
    @AppCoroutineScope private val appCoroutineScope: CoroutineScope,
) : MatrixAuthenticationService {
    // Family Chat: sign-in code redemptions run one at a time, see [loginWithToken].
    private val tokenLoginMutex = Mutex()

    // Any existing Element Classic session that we want to try to import secrets from during login.
    private var elementClassicSession: ElementClassicSession? = null

    // Passphrase which will be used for new sessions. Existing sessions will use the passphrase
    // stored in the SessionData.
    private val pendingKey by lazy { getDatabaseKey() }

    // Need to keep a copy of the current session path to eventually delete it.
    // Ideally it would be possible to get the sessionPath from the Client to avoid doing this.
    private var sessionPaths: SessionPaths? = null
    private var currentClient: Client? = null

    private val newMatrixClientObservers = mutableListOf<(MatrixClient) -> Unit>()
    override fun listenToNewMatrixClients(lambda: (MatrixClient) -> Unit) {
        newMatrixClientObservers.add(lambda)
    }

    private fun rotateSessionPath(): SessionPaths {
        sessionPaths?.deleteRecursively()
        return sessionPathsFactory.create()
            .also { sessionPaths = it }
    }

    override suspend fun restoreSession(sessionId: SessionId): Result<MatrixClient> = withContext(coroutineDispatchers.io) {
        runCatchingExceptions {
            val sessionData = sessionStore.getSession(sessionId.value)
            if (sessionData != null) {
                if (sessionData.isTokenValid) {
                    // Use the sessionData.passphrase, which can be null for a previously created session
                    if (sessionData.passphrase == null) {
                        Timber.w("Restoring a session without a passphrase")
                    } else {
                        Timber.w("Restoring a session with a passphrase")
                    }
                    rustMatrixClientFactory.create(sessionData)
                } else {
                    throw SessionRestorationException.InvalidToken()
                }
            } else {
                throw SessionRestorationException.MissingSession(sessionId)
            }
        }.mapFailure { failure ->
            failure.mapClientException()
        }
    }

    private fun getDatabaseKey(): ClientSecret {
        Timber.d("New sessions will be encrypted with a raw key")
        return secretGenerator.generateKey()
    }

    override suspend fun setHomeserver(homeserver: String): Result<MatrixHomeServerDetails> =
        withContext(coroutineDispatchers.io) {
            val emptySessionPath = rotateSessionPath()
            runCatchingExceptions {
                // Resolves a server name through `.well-known` discovery: a family's own domain (e.g. `smith.ie`)
                // becomes the family's server (`https://<family>.safechat.family`).
                val client = makeClient(sessionPaths = emptySessionPath) {
                    serverNameOrHomeserverUrl(homeserver)
                }

                currentClient = client

                // Family Chat: judge the homeserver it resolved to, not the name that was entered, before asking it
                // anything else. The failure path below closes the client, so no login can go through it.
                client.ensureAllowedHomeserver()

                client.homeserverLoginDetails().map()
            }.onFailure {
                clear(destroyClient = true)
            }.mapFailure { failure ->
                Timber.e(failure, "Failed to set homeserver to $homeserver")
                failure.mapAuthenticationException()
            }
        }

    override suspend fun login(username: String, password: String): Result<SessionId> =
        withContext(coroutineDispatchers.io) {
            runCatchingExceptions {
                val client = currentClient ?: error("You need to call `setHomeserver()` first")
                val currentSessionPaths = sessionPaths ?: error("You need to call `setHomeserver()` first")
                // Never send a password to a homeserver outside the allowlist; setHomeserver() already refused it.
                client.ensureAllowedHomeserver()
                client.login(
                    username = username,
                    password = password,
                    initialDeviceName = INITIAL_DEVICE_NAME,
                    deviceId = null,
                )
                // The login response's well_known can re-point the client: check again before keeping the session.
                client.ensureStillAllowedHomeserverAfterLogin()
                // Ensure that the user is not already logged in with the same account
                ensureNotAlreadyLoggedIn(client)
                tryToImportSecretForElementClassicSession(client)
                val sessionData = client.session()
                    .toSessionData(
                        isTokenValid = true,
                        loginType = LoginType.PASSWORD,
                        passphrase = pendingKey.formattedAsString(),
                        sessionPaths = currentSessionPaths,
                    )
                val matrixClient = rustMatrixClientFactory.create(client, sessionData, isMessageSearchAvailable())

                // Apply enterprise hooks to the newly created client as soon as possible
                clientEnterpriseHook(matrixClient)

                newMatrixClientObservers.forEach { it.invoke(matrixClient) }
                sessionStore.addSession(sessionData)

                // Clean up the strong reference held here since it's no longer necessary
                clear(destroyClient = false)

                SessionId(sessionData.userId)
            }.mapFailure { failure ->
                Timber.e(failure, "Failed to login")
                failure.mapAuthenticationException()
            }
        }

    override suspend fun loginWithToken(
        homeserverUrl: String,
        token: String,
        expectedUserId: String?,
        accountProvider: String,
    ): Result<SessionId> {
        // The server consumes the code as soon as it receives it. Run the redemption in the application scope so
        // that a caller going away (the screen left, the activity recreated) does not abandon a half-made session,
        // and one at a time so that a second link cannot interleave with the first.
        val redemption = appCoroutineScope.async(coroutineDispatchers.io) {
            tokenLoginMutex.withLock {
                redeemLoginToken(
                    homeserverUrl = homeserverUrl,
                    token = token,
                    expectedUserId = expectedUserId,
                    accountProvider = accountProvider,
                )
            }
        }
        return redemption.await()
    }

    private suspend fun redeemLoginToken(
        homeserverUrl: String,
        token: String,
        expectedUserId: String?,
        accountProvider: String,
    ): Result<SessionId> {
        // Its own session paths and client, not the shared ones used by the other login flows: a password login
        // started meanwhile must not delete this session's directory, nor this one close that login's client.
        val tokenSessionPaths = sessionPathsFactory.create()
        var client: Client? = null
        var credentials: LoginTokenCredentials? = null
        return runCatchingExceptions {
            // The code is sent to this host as it is, without discovery: it must be an allowed homeserver itself.
            if (!enterpriseService.isAllowedResolvedHomeserverUrl(homeserverUrl)) {
                Timber.w("Sign-in code refused: its homeserver is outside the allowlist")
                throw SignInCodeException.HomeserverNotAllowed()
            }
            val newClient = makeClient(sessionPaths = tokenSessionPaths) {
                homeserverUrl(homeserverUrl)
            }
            client = newClient
            // The SDK has no `m.login.token` entry point; redeem the code ourselves and hand the
            // credentials over as a restored session, exactly as a stored session is reopened.
            val newCredentials = loginTokenExchanger.exchange(
                homeserverUrl = homeserverUrl,
                token = token,
                initialDeviceDisplayName = INITIAL_DEVICE_NAME,
            )
            credentials = newCredentials
            // Login CSRF: a code for someone else's account must not sign this device into it. With no Matrix ID
            // named, the account must at least belong to the link's account provider: for a family on its own
            // domain that is the domain (`@kid:smith.ie`), not the host the code was redeemed against.
            if (!isExpectedTokenLoginUser(userId = newCredentials.userId, expectedUserId = expectedUserId, accountProvider = accountProvider)) {
                Timber.w("Sign-in code redeemed for another account than the link named")
                throw SignInCodeException.UserMismatch()
            }
            newClient.restoreSession(
                Session(
                    accessToken = newCredentials.accessToken,
                    refreshToken = newCredentials.refreshToken,
                    userId = newCredentials.userId,
                    deviceId = newCredentials.deviceId,
                    homeserverUrl = homeserverUrl,
                    oauthData = null,
                    // The client was built with DISCOVER_NATIVE; the family homeservers all serve native sliding sync.
                    slidingSyncVersion = SlidingSyncVersion.NATIVE,
                )
            )
            // As after any login, the client must still point at an allowed homeserver. The failure path below
            // signs the new device out.
            if (!enterpriseService.isAllowedResolvedHomeserverUrl(newClient.homeserver())) {
                Timber.w("Sign-in code refused: the client no longer points at an allowed homeserver")
                throw SignInCodeException.HomeserverNotAllowed()
            }
            // Ensure that the user is not already logged in with the same account
            ensureNotAlreadyLoggedIn(newClient)
            val sessionData = newClient.session()
                .toSessionData(
                    isTokenValid = true,
                    loginType = LoginType.DIRECT,
                    passphrase = pendingKey.formattedAsString(),
                    sessionPaths = tokenSessionPaths,
                    homeserverUrl = homeserverUrl,
                )
            val matrixClient = rustMatrixClientFactory.create(newClient, sessionData, isMessageSearchAvailable())

            // Apply enterprise hooks to the newly created client as soon as possible
            clientEnterpriseHook(matrixClient)

            newMatrixClientObservers.forEach { it.invoke(matrixClient) }
            sessionStore.addSession(sessionData)

            SessionId(sessionData.userId)
        }.onFailure {
            client?.close()
            // The server issued a device that the app is not going to use: sign it out again rather than leave
            // a live, unknown session on the account.
            credentials?.let { issued ->
                withContext(NonCancellable) {
                    loginTokenExchanger.logout(homeserverUrl = homeserverUrl, accessToken = issued.accessToken)
                }
            }
            tokenSessionPaths.deleteRecursively()
        }.mapFailure { failure ->
            // The failure never carries the token; the exchanger only reports HTTP status and errcode.
            Timber.e(failure, "Failed to login with a sign-in code")
            failure as? SignInCodeException ?: failure.mapAuthenticationException()
        }
    }

    /**
     * Family Chat: the single backstop every password and OAuth login goes through. The homeserver the client
     * resolved to (after `.well-known` discovery of what the user typed or a link named) must be allowed, see
     * [EnterpriseService.isAllowedResolvedHomeserverUrl]; otherwise no credentials are sent to it.
     */
    @Throws(AuthenticationException.HomeserverNotAllowed::class)
    private suspend fun Client.ensureAllowedHomeserver() {
        val resolvedHomeserverUrl = homeserver()
        if (!enterpriseService.isAllowedResolvedHomeserverUrl(resolvedHomeserverUrl)) {
            Timber.w("Refusing to sign in: the server resolved to a homeserver outside the allowlist ($resolvedHomeserverUrl)")
            throw AuthenticationException.HomeserverNotAllowed(resolvedHomeserverUrl)
        }
    }

    /**
     * Family Chat: the SDK may re-point the client at the homeserver named by the login response's `well_known`
     * (`respect_login_well_known`, on by default and not exposed over FFI). Check the homeserver again once logged
     * in, before the session is kept; on a mismatch sign the new device out (best effort) and drop the client.
     */
    @Throws(AuthenticationException.HomeserverNotAllowed::class)
    private suspend fun Client.ensureStillAllowedHomeserverAfterLogin() {
        val resolvedHomeserverUrl = homeserver()
        if (!enterpriseService.isAllowedResolvedHomeserverUrl(resolvedHomeserverUrl)) {
            Timber.w("Refusing the new session: the login re-pointed the client outside the allowlist ($resolvedHomeserverUrl)")
            runCatchingExceptions { logout() }
            clear(destroyClient = true)
            throw AuthenticationException.HomeserverNotAllowed(resolvedHomeserverUrl)
        }
    }

    private suspend fun tryToImportSecretForElementClassicSession(client: Client) {
        elementClassicSession
            ?.takeIf {
                // Note: the SDK will also do this check
                it.userId.value == client.userId()
            }
            ?.let {
                val secrets = it.secrets
                val roomKeysVersion = it.roomKeysVersion
                if (secrets == null || roomKeysVersion == null) {
                    Timber.d("No secrets or roomKeysVersion found for Element Classic session ${it.userId}, skipping import")
                } else {
                    Timber.d("Trying to import secrets for Element Classic session ${it.userId}")
                    runCatchingExceptions {
                        SecretsBundleWithUserId.fromStr(
                            userId = it.userId.value,
                            bundle = secrets,
                            backupInfo = roomKeysVersion,
                        ).use { secretsBundle ->
                            client.encryption().importSecretsBundle(secretsBundle)
                        }
                    }.onFailure { failure ->
                        Timber.e(failure, "Failed to import secrets for Element Classic session ${it.userId}")
                    }
                }
            }
    }

    override fun doSecretsContainBackupKey(
        userId: UserId,
        secrets: String,
        backupInfo: String,
    ): Boolean {
        return try {
            SecretsBundleWithUserId.fromStr(
                userId = userId.value,
                bundle = secrets,
                backupInfo = backupInfo,
            ).use { secretsBundle ->
                secretsBundle.containsBackupKey()
            }
        } catch (failure: Exception) {
            Timber.e(failure, "Failed to parse secrets for Element Classic session $userId")
            false
        }
    }

    private var pendingOAuthAuthorizationData: OAuthAuthorizationData? = null

    override suspend fun getOAuthUrl(
        prompt: OAuthPrompt,
        loginHint: String?,
    ): Result<OAuthDetails> {
        return withContext(coroutineDispatchers.io) {
            runCatchingExceptions {
                val client = currentClient ?: error("You need to call `setHomeserver()` first")
                client.ensureAllowedHomeserver()
                val oAuthAuthorizationData = client.urlForOauth(
                    oauthConfiguration = oAuthConfigurationProvider.get(),
                    prompt = prompt.toRustPrompt(),
                    loginHint = loginHint,
                    // If we want to restore a previous session for which we have encryption keys, we can pass the deviceId here. At the moment, we don't
                    deviceId = null,
                    additionalScopes = emptyList(),
                )
                val getUrlResolver = RustTemporaryMatrixClient(client, sessionPaths)
                val url = oAuthAuthorizationData.loginUrl()
                    .let {
                        enterpriseService.tweakMasUrl(
                            url = it,
                            urlContentFetcher = getUrlResolver,
                        )
                    }
                pendingOAuthAuthorizationData = oAuthAuthorizationData
                OAuthDetails(url)
            }.mapFailure { failure ->
                Timber.e(failure, "Failed to get OAuth URL")
                failure.mapAuthenticationException()
            }
        }
    }

    override suspend fun cancelOAuthLogin(): Result<Unit> {
        return withContext(coroutineDispatchers.io) {
            runCatchingExceptions {
                pendingOAuthAuthorizationData?.use {
                    currentClient?.abortOauthAuth(it)
                }
                pendingOAuthAuthorizationData = null
            }.mapFailure { failure ->
                Timber.e(failure, "Failed to cancel OAuth login")
                failure.mapAuthenticationException()
            }
        }
    }

    override fun setElementClassicSession(session: ElementClassicSession?) {
        elementClassicSession = session
    }

    /**
     * callbackUrl should be the `url` from `OAuthAction` (with all the parameters).
     */
    override suspend fun loginWithOAuth(callbackUrl: String): Result<SessionId> {
        return withContext(coroutineDispatchers.io) {
            runCatchingExceptions {
                val client = currentClient ?: error("You need to call `setHomeserver()` first")
                val currentSessionPaths = sessionPaths ?: error("You need to call `setHomeserver()` first")
                client.ensureAllowedHomeserver()
                client.loginWithOauthCallback(
                    callbackUrl = callbackUrl,
                )
                client.ensureStillAllowedHomeserverAfterLogin()
                // Free the pending data since we won't use it to abort the flow anymore
                pendingOAuthAuthorizationData?.close()
                pendingOAuthAuthorizationData = null
                // Ensure that the user is not already logged in with the same account
                ensureNotAlreadyLoggedIn(client)
                tryToImportSecretForElementClassicSession(client)
                val sessionData = client.session().toSessionData(
                    isTokenValid = true,
                    loginType = LoginType.OIDC,
                    passphrase = pendingKey.formattedAsString(),
                    sessionPaths = currentSessionPaths,
                )
                val matrixClient = rustMatrixClientFactory.create(client, sessionData, isMessageSearchAvailable())

                // Apply enterprise hooks to the newly created client as soon as possible
                clientEnterpriseHook(matrixClient)

                matrixClient.waitForKnownVerificationState()

                newMatrixClientObservers.forEach { it.invoke(matrixClient) }
                sessionStore.addSession(sessionData)

                // Clean up the strong reference held here since it's no longer necessary
                clear(destroyClient = false)

                SessionId(sessionData.userId)
            }.mapFailure { failure ->
                Timber.e(failure, "Failed to login with OAuth")
                failure.mapAuthenticationException()
            }
        }
    }

    @Throws(AuthenticationException.AccountAlreadyLoggedIn::class)
    private suspend fun ensureNotAlreadyLoggedIn(client: Client) {
        val newUserId = client.userId()
        val accountAlreadyLoggedIn = sessionStore.getAllSessions().any {
            it.userId == newUserId
        }
        if (accountAlreadyLoggedIn) {
            // Sign out the client, ignoring any error
            runCatchingExceptions {
                client.logout()
            }
            throw AuthenticationException.AccountAlreadyLoggedIn(newUserId)
        }
    }

    override suspend fun loginWithQrCode(qrCodeData: MatrixQrCodeLoginData, progress: (QrCodeLoginStep) -> Unit) =
        withContext(coroutineDispatchers.io) {
            val sdkQrCodeLoginData = (qrCodeData as SdkQrCodeLoginData).rustQrCodeData
            val emptySessionPaths = rotateSessionPath()
            val oAuthConfiguration = oAuthConfigurationProvider.get()
            val progressListener = object : QrLoginProgressListener {
                override fun onUpdate(state: QrLoginProgress) {
                    Timber.d("QR Code login progress: $state")
                    progress(state.toStep())
                }
            }
            runCatchingExceptions {
                val client = makeQrCodeLoginClient(
                    sessionPaths = emptySessionPaths,
                    qrCodeData = sdkQrCodeLoginData,
                )
                // The QR code names the server; it is held to the same allowlist as a typed one.
                if (!enterpriseService.isAllowedResolvedHomeserverUrl(client.homeserver())) {
                    Timber.w("QR code login refused: its homeserver is outside the allowlist")
                    client.close()
                    emptySessionPaths.deleteRecursively()
                    throw QrLoginException.HomeserverNotAllowed
                }
                client.newLoginWithQrCodeHandler(
                    oauthConfiguration = oAuthConfiguration,
                ).use {
                    it.scan(
                        qrCodeData = qrCodeData.rustQrCodeData,
                        progressListener = progressListener,
                    )
                }
                if (!enterpriseService.isAllowedResolvedHomeserverUrl(client.homeserver())) {
                    Timber.w("QR code login refused: the homeserver changed to one outside the allowlist during login")
                    runCatchingExceptions { client.logout() }
                    client.close()
                    emptySessionPaths.deleteRecursively()
                    throw QrLoginException.HomeserverNotAllowed
                }
                // Ensure that the user is not already logged in with the same account
                ensureNotAlreadyLoggedIn(client)
                val sessionData = client.session()
                    .toSessionData(
                        isTokenValid = true,
                        loginType = LoginType.QR,
                        passphrase = pendingKey.formattedAsString(),
                        sessionPaths = emptySessionPaths,
                    )
                val matrixClient = rustMatrixClientFactory.create(client, sessionData, isMessageSearchAvailable())

                // Apply enterprise hooks to the newly created client as soon as possible
                clientEnterpriseHook(matrixClient)

                newMatrixClientObservers.forEach { it.invoke(matrixClient) }
                sessionStore.addSession(sessionData)

                // Clean up the strong reference held here since it's no longer necessary
                clear(destroyClient = false)

                SessionId(sessionData.userId)
            }.mapFailure {
                when (it) {
                    is QrCodeDecodeException -> QrErrorMapper.map(it)
                    is HumanQrLoginException -> QrErrorMapper.map(it)
                    else -> it
                }
            }.onFailure { throwable ->
                if (throwable is CancellationException) {
                    throw throwable
                }
                Timber.e(throwable, "Failed to login with QR code")
            }
        }

    private suspend fun makeClient(
        sessionPaths: SessionPaths,
        config: suspend ClientBuilder.() -> ClientBuilder,
    ): Client {
        Timber.d("Creating client with simplified sliding sync")
        return rustMatrixClientFactory
            .getBaseClientBuilder(
                sessionPaths = sessionPaths,
                clientSecret = pendingKey,
                slidingSyncType = ClientBuilderSlidingSync.Discovered,
                isMessageSearchAvailable = isMessageSearchAvailable(),
            )
            .config()
            .build()
    }

    private suspend fun makeQrCodeLoginClient(
        sessionPaths: SessionPaths,
        qrCodeData: QrCodeData,
    ): Client {
        Timber.d("Creating client for QR Code login with simplified sliding sync")
        // The 2025 version of MSC4108 provides baseUrl; the 2024 version has null baseUrl and uses
        // serverName instead, which can be null or malformed. We only enforce presence/non-blankness
        // here and rely on serverNameOrHomeserverUrl()/the Rust builder layer to validate structure.
        val baseUrlOrServerName = qrCodeData.baseUrl() ?: qrCodeData.serverName()

        if (baseUrlOrServerName == null) {
            // With the 2024 version of MSC4108 we treat the absence of serverName as meaning that
            // the other device is not signed in.
            Timber.e("The QR code is from a device that is not yet signed in")
            throw HumanQrLoginException.OtherDeviceNotSignedIn()
        }

        if (baseUrlOrServerName.isBlank()) {
            Timber.e("The QR code contains an empty base URL or server name, which is invalid")
            throw HumanQrLoginException.Unknown()
        }

        return rustMatrixClientFactory
            .getBaseClientBuilder(
                sessionPaths = sessionPaths,
                clientSecret = pendingKey,
                slidingSyncType = ClientBuilderSlidingSync.Discovered,
                isMessageSearchAvailable = isMessageSearchAvailable(),
            )
            .serverNameOrHomeserverUrl(baseUrlOrServerName)
            .build()
    }

    private fun clear(destroyClient: Boolean) {
        if (destroyClient) {
            currentClient?.close()
        }
        currentClient = null
    }

    private suspend fun isMessageSearchAvailable(): Boolean =
        featureFlagService.isFeatureEnabled(FeatureFlags.MessageSearch)

    private suspend fun MatrixClient.waitForKnownVerificationState() {
        withTimeoutOrNull(10.seconds) {
            Timber.d("Waiting for a known verification status...")
            val status = sessionVerificationService.sessionVerifiedStatus.first { it != SessionVerifiedStatus.Unknown }
            Timber.d("Finished waiting for a known verification status: $status")
        } ?: Timber.w("Timed out waiting for a known verification status")
    }

    companion object {
        private const val INITIAL_DEVICE_NAME = "Family Chat Android"
    }
}

/**
 * Whether a sign-in code signed in the account the link was for: the Matrix ID it named, or with none, an account
 * on its account provider (`@kid:smith.ie` for a link with `account_provider=smith.ie`).
 */
internal fun isExpectedTokenLoginUser(userId: String, expectedUserId: String?, accountProvider: String): Boolean {
    if (expectedUserId != null) return userId == expectedUserId
    val serverName = accountProvider.trim().removePrefix("https://").removeSuffix("/")
    return serverName.isNotEmpty() && userId.substringAfter(':', missingDelimiterValue = "").equals(serverName, ignoreCase = true)
}
