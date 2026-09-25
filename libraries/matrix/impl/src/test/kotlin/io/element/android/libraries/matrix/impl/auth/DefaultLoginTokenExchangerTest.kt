/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.auth

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.core.extensions.runCatchingExceptions
import io.element.android.libraries.matrix.api.auth.AuthenticationException
import io.element.android.libraries.matrix.api.auth.SignInCodeException
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Before
import org.junit.Test

private const val A_TOKEN = "syl_secret_login_token"
private const val A_DEVICE_NAME = "Family Chat Android"

class DefaultLoginTokenExchangerTest {
    private val server = MockWebServer()
    private val loggedLines = mutableListOf<String>()

    @Before
    fun setUp() {
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `posts m login token to the homeserver and maps the credentials`() = runTest {
        server.enqueue(
            MockResponse().setBody(
                """{"user_id":"@ana:smith.safechat.family","access_token":"syt_access","device_id":"DEVICE1","refresh_token":"syr_refresh"}"""
            )
        )
        val sut = createExchanger()

        val credentials = sut.exchange(server.url("/").toString(), A_TOKEN, A_DEVICE_NAME)

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/_matrix/client/v3/login")
        val body = Json.parseToJsonElement(request.body.readUtf8()).jsonObject
        assertThat(body["type"]!!.jsonPrimitive.content).isEqualTo("m.login.token")
        assertThat(body["token"]!!.jsonPrimitive.content).isEqualTo(A_TOKEN)
        assertThat(body["initial_device_display_name"]!!.jsonPrimitive.content).isEqualTo(A_DEVICE_NAME)
        assertThat(credentials).isEqualTo(
            LoginTokenCredentials(
                userId = "@ana:smith.safechat.family",
                accessToken = "syt_access",
                deviceId = "DEVICE1",
                refreshToken = "syr_refresh",
            )
        )
    }

    @Test
    fun `a used or expired token is a rejected code, carrying the HTTP status`() = runTest {
        server.enqueue(
            MockResponse().setResponseCode(403).setBody("""{"errcode":"M_FORBIDDEN","error":"Invalid login token"}""")
        )
        val sut = createExchanger()

        val failure = runCatchingExceptions { sut.exchange(server.url("/").toString(), A_TOKEN, A_DEVICE_NAME) }.exceptionOrNull()

        assertThat(failure).isInstanceOf(SignInCodeException.Rejected::class.java)
        assertThat((failure as SignInCodeException.Rejected).httpStatus).isEqualTo(403)
        assertThat(failure.errcode).isEqualTo("M_FORBIDDEN")
        assertThat(failure.message).doesNotContain(A_TOKEN)
    }

    @Test
    fun `a 401 is a rejected code whatever its errcode`() = runTest {
        server.enqueue(MockResponse().setResponseCode(401).setBody("""{"errcode":"M_UNKNOWN_TOKEN","error":"Unknown"}"""))
        val sut = createExchanger()

        val failure = runCatchingExceptions { sut.exchange(server.url("/").toString(), A_TOKEN, A_DEVICE_NAME) }.exceptionOrNull()

        assertThat(failure).isInstanceOf(SignInCodeException.Rejected::class.java)
        assertThat((failure as SignInCodeException.Rejected).httpStatus).isEqualTo(401)
    }

    @Test
    fun `any other refusal is a failure carrying the HTTP status`() = runTest {
        server.enqueue(MockResponse().setResponseCode(500).setBody("oops"))
        val sut = createExchanger()

        val failure = runCatchingExceptions { sut.exchange(server.url("/").toString(), A_TOKEN, A_DEVICE_NAME) }.exceptionOrNull()

        assertThat(failure).isInstanceOf(SignInCodeException.Failed::class.java)
        assertThat((failure as SignInCodeException.Failed).httpStatus).isEqualTo(500)
    }

    @Test
    fun `a redirect is never followed and is a failure`() = runTest {
        val elsewhere = MockWebServer().apply { start() }
        try {
            elsewhere.enqueue(MockResponse().setBody("""{"user_id":"@ana:x","access_token":"syt_access","device_id":"D"}"""))
            server.enqueue(
                MockResponse().setResponseCode(307).setHeader("Location", elsewhere.url("/_matrix/client/v3/login").toString())
            )
            val sut = createExchanger()

            val failure = runCatchingExceptions { sut.exchange(server.url("/").toString(), A_TOKEN, A_DEVICE_NAME) }.exceptionOrNull()

            assertThat(failure).isInstanceOf(SignInCodeException.Failed::class.java)
            assertThat((failure as SignInCodeException.Failed).httpStatus).isEqualTo(307)
            assertThat(server.requestCount).isEqualTo(1)
            // The token never went to the redirect target
            assertThat(elsewhere.requestCount).isEqualTo(0)
        } finally {
            elsewhere.shutdown()
        }
    }

    @Test
    fun `an unparseable answer is a failure`() = runTest {
        server.enqueue(MockResponse().setBody("<html>not json</html>"))
        val sut = createExchanger()

        val failure = runCatchingExceptions { sut.exchange(server.url("/").toString(), A_TOKEN, A_DEVICE_NAME) }.exceptionOrNull()

        assertThat(failure).isInstanceOf(SignInCodeException.Failed::class.java)
    }

    @Test
    fun `logout posts to the logout endpoint with the access token and never throws`() = runTest {
        server.enqueue(MockResponse().setBody("{}"))
        val sut = createExchanger()

        sut.logout(server.url("/").toString(), "syt_access")

        val request = server.takeRequest()
        assertThat(request.method).isEqualTo("POST")
        assertThat(request.path).isEqualTo("/_matrix/client/v3/logout")
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer syt_access")

        // Neither a refusal nor an unreachable server escapes
        server.enqueue(MockResponse().setResponseCode(401))
        sut.logout(server.url("/").toString(), "syt_access")
        val url = server.url("/").toString()
        server.shutdown()
        sut.logout(url, "syt_access")
        assertThat(loggedLines.joinToString("\n")).doesNotContain("syt_access")
    }

    @Test
    fun `the credentials never print their tokens`() {
        val credentials = LoginTokenCredentials(userId = "@ana:x", accessToken = "syt_access", deviceId = "D", refreshToken = "syr_refresh")
        assertThat(credentials.toString()).doesNotContain("syt_access")
        assertThat(credentials.toString()).doesNotContain("syr_refresh")
    }

    @Test
    fun `an unreachable homeserver is reported as such`() = runTest {
        val url = server.url("/").toString()
        server.shutdown()
        val sut = createExchanger()

        val failure = runCatchingExceptions { sut.exchange(url, A_TOKEN, A_DEVICE_NAME) }.exceptionOrNull()

        assertThat(failure).isInstanceOf(AuthenticationException.ServerUnreachable::class.java)
    }

    @Test
    fun `the shared client's body logging never sees the token or the access token`() = runTest {
        server.enqueue(MockResponse().setBody("""{"user_id":"@ana:x","access_token":"syt_access","device_id":"D"}"""))
        val sut = createExchanger()

        sut.exchange(server.url("/").toString(), A_TOKEN, A_DEVICE_NAME)

        val logged = loggedLines.joinToString("\n")
        assertThat(logged).doesNotContain(A_TOKEN)
        assertThat(logged).doesNotContain("syt_access")
    }

    private fun TestScope.createExchanger(): DefaultLoginTokenExchanger {
        // Mirror the production client: a body-logging interceptor that the exchanger must strip.
        val loggingInterceptor = Interceptor { chain ->
            val request = chain.request()
            val requestBody = okio.Buffer().also { request.body?.writeTo(it) }.readUtf8()
            loggedLines.add("--> ${request.method} ${request.url}\n$requestBody")
            val response = chain.proceed(request)
            val responseBody = response.peekBody(Long.MAX_VALUE).string()
            loggedLines.add("<-- ${response.code}\n$responseBody")
            response
        }
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .build()
        return DefaultLoginTokenExchanger(
            okHttpClient = okHttpClient,
            coroutineDispatchers = testCoroutineDispatchers(),
        )
    }
}
