/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.auth

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.data.tryOrNull
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.auth.AuthenticationException
import io.element.android.libraries.matrix.api.auth.SignInCodeException
import io.element.android.libraries.network.interceptors.UserAgentInterceptor
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import timber.log.Timber
import java.io.IOException
import kotlin.coroutines.resumeWithException

/**
 * The credentials a homeserver hands back for a redeemed `m.login.token`.
 */
data class LoginTokenCredentials(
    val userId: String,
    val accessToken: String,
    val deviceId: String,
    val refreshToken: String?,
) {
    /** The tokens are bearer credentials: they must never reach a log line. */
    override fun toString(): String {
        return "LoginTokenCredentials(userId=$userId, accessToken=<redacted>, deviceId=$deviceId, " +
            "refreshToken=${if (refreshToken == null) "null" else "<redacted>"})"
    }
}

/**
 * Redeems a control panel sign-in code (`m.login.token`) with the family's homeserver.
 *
 * The Rust SDK bindings expose password, JWT, OAuth and QR logins but not `m.login.token`, so the exchange is a
 * plain `POST /_matrix/client/v3/login` done here; the resulting credentials are then handed to the SDK as a
 * restored session.
 */
interface LoginTokenExchanger {
    /**
     * @param homeserverUrl the `https://<hs>` base URL of the homeserver.
     * @param token the single-use login token.
     * @param initialDeviceDisplayName the device name shown to the user in their session list.
     * @throws SignInCodeException.Rejected for an HTTP 401 or 403: a used, expired or unknown code.
     * @throws SignInCodeException.Failed for any other answer than a 200 carrying credentials, redirects included.
     * @throws AuthenticationException.ServerUnreachable when the homeserver could not be reached.
     */
    suspend fun exchange(homeserverUrl: String, token: String, initialDeviceDisplayName: String): LoginTokenCredentials

    /**
     * Best effort `POST /_matrix/client/v3/logout` for [accessToken], to sign out a device created by [exchange]
     * that the app is not going to use. Never throws.
     */
    suspend fun logout(homeserverUrl: String, accessToken: String)
}

@ContributesBinding(AppScope::class)
class DefaultLoginTokenExchanger(
    okHttpClient: OkHttpClient,
    private val coroutineDispatchers: CoroutineDispatchers,
) : LoginTokenExchanger {
    // The shared client logs request and response bodies at the DEBUG tracing level; this request carries the
    // login token and the answer carries the access token, neither of which may ever reach a log line. Keep the
    // shared client's timeouts and user agent, drop every other interceptor.
    // Never follow a redirect (OkHttp would re-send the POST, token included, to wherever it points) and never
    // retry on its own (a retry could replay a code the server already consumed).
    private val client: OkHttpClient = okHttpClient.newBuilder()
        .apply {
            interceptors().removeAll { it !is UserAgentInterceptor }
            networkInterceptors().clear()
        }
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .build()

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun exchange(homeserverUrl: String, token: String, initialDeviceDisplayName: String): LoginTokenCredentials =
        withContext(coroutineDispatchers.io) {
            val body = json.encodeToString(
                LoginRequest.serializer(),
                LoginRequest(type = LOGIN_TYPE, token = token, initialDeviceDisplayName = initialDeviceDisplayName),
            )
            val request = Request.Builder()
                .url(homeserverUrl.trimEnd('/') + LOGIN_PATH)
                .post(body.toRequestBody(JSON_MEDIA_TYPE))
                .build()
            val response = try {
                client.newCall(request).await()
            } catch (e: IOException) {
                Timber.w("Login token exchange: homeserver unreachable (${e.javaClass.simpleName})")
                throw AuthenticationException.ServerUnreachable(e.message)
            }
            response.use {
                val responseBody = it.body.string()
                // Only a 200 is a login: a 3xx is not followed, and anything else is a refusal.
                if (it.code != HTTP_OK) {
                    val errcode = tryOrNull { json.decodeFromString(ErrorResponse.serializer(), responseBody) }?.errcode
                    Timber.w("Login token exchange refused: HTTP ${it.code} ${errcode.orEmpty()}")
                    throw if (it.code == HTTP_UNAUTHORIZED || it.code == HTTP_FORBIDDEN) {
                        SignInCodeException.Rejected(httpStatus = it.code, errcode = errcode)
                    } else {
                        SignInCodeException.Failed(httpStatus = it.code, errcode = errcode)
                    }
                }
                val success = try {
                    json.decodeFromString(LoginResponse.serializer(), responseBody)
                } catch (e: Exception) {
                    Timber.w("Login token exchange: unreadable response (${e.javaClass.simpleName})")
                    throw SignInCodeException.Failed(httpStatus = it.code, errcode = null)
                }
                LoginTokenCredentials(
                    userId = success.userId,
                    accessToken = success.accessToken,
                    deviceId = success.deviceId,
                    refreshToken = success.refreshToken,
                )
            }
        }

    override suspend fun logout(homeserverUrl: String, accessToken: String) = withContext(coroutineDispatchers.io) {
        val request = Request.Builder()
            .url(homeserverUrl.trimEnd('/') + LOGOUT_PATH)
            .header("Authorization", "Bearer $accessToken")
            .post("{}".toRequestBody(JSON_MEDIA_TYPE))
            .build()
        runCatchingExceptions {
            client.newCall(request).await().use { it.code }
        }.fold(
            onSuccess = { code ->
                if (code == HTTP_OK) {
                    Timber.i("Signed out the device created by a sign-in code")
                } else {
                    Timber.w("Could not sign out the device created by a sign-in code: HTTP $code")
                }
            },
            onFailure = { e ->
                Timber.w("Could not sign out the device created by a sign-in code (${e.javaClass.simpleName})")
            },
        )
    }

    /** Runs the call without blocking a thread, and cancels it when the coroutine is cancelled. */
    private suspend fun Call.await(): Response = suspendCancellableCoroutine { continuation ->
        continuation.invokeOnCancellation { cancel() }
        enqueue(
            object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response) { _, value, _ -> value.close() }
                }

                override fun onFailure(call: Call, e: IOException) {
                    continuation.resumeWithException(e)
                }
            }
        )
    }

    @Serializable
    private data class LoginRequest(
        val type: String,
        val token: String,
        @SerialName("initial_device_display_name")
        val initialDeviceDisplayName: String,
    ) {
        override fun toString() = "LoginRequest(type=$type, token=<redacted>)"
    }

    @Serializable
    private data class LoginResponse(
        @SerialName("user_id") val userId: String,
        @SerialName("access_token") val accessToken: String,
        @SerialName("device_id") val deviceId: String,
        @SerialName("refresh_token") val refreshToken: String? = null,
    ) {
        override fun toString() = "LoginResponse(userId=$userId, deviceId=$deviceId, tokens=<redacted>)"
    }

    @Serializable
    private data class ErrorResponse(
        val errcode: String? = null,
        val error: String? = null,
    )

    companion object {
        private const val LOGIN_PATH = "/_matrix/client/v3/login"
        private const val LOGOUT_PATH = "/_matrix/client/v3/logout"
        private const val LOGIN_TYPE = "m.login.token"
        private const val HTTP_OK = 200
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
