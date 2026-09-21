/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.auth

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.element.android.libraries.core.coroutine.CoroutineDispatchers
import io.element.android.libraries.core.data.tryOrNull
import io.element.android.libraries.matrix.api.auth.AuthenticationException
import io.element.android.libraries.network.interceptors.UserAgentInterceptor
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import timber.log.Timber
import java.io.IOException

/**
 * The credentials a homeserver hands back for a redeemed `m.login.token`.
 */
data class LoginTokenCredentials(
    val userId: String,
    val accessToken: String,
    val deviceId: String,
    val refreshToken: String?,
)

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
     * @throws AuthenticationException.Generic carrying the server `errcode` (e.g. `M_FORBIDDEN` for a used or expired code).
     * @throws AuthenticationException.ServerUnreachable when the homeserver could not be reached.
     */
    suspend fun exchange(homeserverUrl: String, token: String, initialDeviceDisplayName: String): LoginTokenCredentials
}

@ContributesBinding(AppScope::class)
@Inject
class DefaultLoginTokenExchanger(
    okHttpClient: OkHttpClient,
    private val coroutineDispatchers: CoroutineDispatchers,
) : LoginTokenExchanger {
    // The shared client logs request and response bodies at the DEBUG tracing level; this request carries the
    // login token and the answer carries the access token, neither of which may ever reach a log line. Keep the
    // shared client's timeouts and user agent, drop every other interceptor.
    private val client: OkHttpClient = okHttpClient.newBuilder()
        .apply {
            interceptors().removeAll { it !is UserAgentInterceptor }
            networkInterceptors().clear()
        }
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
                client.newCall(request).execute()
            } catch (e: IOException) {
                Timber.w("Login token exchange: homeserver unreachable (${e.javaClass.simpleName})")
                throw AuthenticationException.ServerUnreachable(e.message)
            }
            response.use {
                val responseBody = it.body.string()
                if (!it.isSuccessful) {
                    val error = tryOrNull { json.decodeFromString(ErrorResponse.serializer(), responseBody) }
                    val errcode = error?.errcode ?: "M_UNKNOWN"
                    Timber.w("Login token exchange refused: HTTP ${it.code} $errcode")
                    throw AuthenticationException.Generic("$errcode: ${error?.error ?: "HTTP ${it.code}"}")
                }
                val success = try {
                    json.decodeFromString(LoginResponse.serializer(), responseBody)
                } catch (e: Exception) {
                    Timber.w("Login token exchange: unreadable response (${e.javaClass.simpleName})")
                    throw AuthenticationException.Generic("M_UNKNOWN: malformed login response")
                }
                LoginTokenCredentials(
                    userId = success.userId,
                    accessToken = success.accessToken,
                    deviceId = success.deviceId,
                    refreshToken = success.refreshToken,
                )
            }
        }

    @Serializable
    private data class LoginRequest(
        val type: String,
        val token: String,
        @kotlinx.serialization.SerialName("initial_device_display_name")
        val initialDeviceDisplayName: String,
    )

    @Serializable
    private data class LoginResponse(
        @kotlinx.serialization.SerialName("user_id") val userId: String,
        @kotlinx.serialization.SerialName("access_token") val accessToken: String,
        @kotlinx.serialization.SerialName("device_id") val deviceId: String,
        @kotlinx.serialization.SerialName("refresh_token") val refreshToken: String? = null,
    )

    @Serializable
    private data class ErrorResponse(
        val errcode: String? = null,
        val error: String? = null,
    )

    companion object {
        private const val LOGIN_PATH = "/_matrix/client/v3/login"
        private const val LOGIN_TYPE = "m.login.token"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}
