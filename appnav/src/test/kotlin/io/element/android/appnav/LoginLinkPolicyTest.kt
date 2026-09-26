/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appnav

import com.google.common.truth.Truth.assertThat
import io.element.android.features.login.api.LoginParams
import io.element.android.features.login.test.accesscontrol.FakeAccountProviderAccessControl
import io.element.android.tests.testutils.lambda.lambdaRecorder
import io.element.android.tests.testutils.lambda.value
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LoginLinkPolicyTest {
    // The Family Chat rules, reduced to what these tests need: any server name but the apex may start discovery, and
    // only a family subdomain is a homeserver the app talks to directly.
    private val isAllowedAccountProvider = lambdaRecorder<String, Boolean> { it != "https://safechat.family" && !it.contains('@') }
    private val isAllowedHomeserver = lambdaRecorder<String, Boolean> { it.endsWith(".safechat.family") }
    private val accessControl = FakeAccountProviderAccessControl(
        isAllowedToConnectToAccountProviderResult = isAllowedAccountProvider,
        isAllowedToConnectToHomeserverResult = isAllowedHomeserver,
    )

    @Test
    fun `a link for a family on its own domain keeps its sign-in code for the family's server`() = runTest {
        val link = LoginParams(
            accountProvider = "smith.ie",
            loginHint = "mxid:@kid:smith.ie",
            hs = "smith.safechat.family",
            signInCodeId = "code-id",
        )

        assertThat(link.sanitize(accessControl)).isEqualTo(link)
        isAllowedAccountProvider.assertions().isCalledOnce().with(value("https://smith.ie"))
        isAllowedHomeserver.assertions().isCalledOnce().with(value("https://smith.safechat.family"))
    }

    @Test
    fun `a link with a code and no login hint is kept`() = runTest {
        val link = LoginParams(accountProvider = "smith.ie", loginHint = null, hs = "smith.safechat.family", signInCodeId = "code-id")

        assertThat(link.sanitize(accessControl)).isEqualTo(link)
    }

    @Test
    fun `a link without a code for a family's own domain is kept, subject to discovery`() = runTest {
        val link = LoginParams(accountProvider = "smith.ie", loginHint = "mxid:@kid:smith.ie")

        assertThat(link.sanitize(accessControl)).isEqualTo(link)
        isAllowedHomeserver.assertions().isNeverCalled()
    }

    @Test
    fun `a sign-in code for a host outside the allowlist is dropped, the rest of the link is kept`() = runTest {
        val link = LoginParams(accountProvider = "smith.ie", loginHint = "mxid:@kid:smith.ie", hs = "smith.ie", signInCodeId = "code-id")

        assertThat(link.sanitize(accessControl)).isEqualTo(LoginParams(accountProvider = "smith.ie", loginHint = "mxid:@kid:smith.ie"))
    }

    @Test
    fun `a link whose account provider is refused is ignored entirely`() = runTest {
        listOf("safechat.family", "https://ana@smith.safechat.family").forEach { accountProvider ->
            val link = LoginParams(accountProvider = accountProvider, loginHint = null, hs = "smith.safechat.family", signInCodeId = "code-id")

            assertThat(link.sanitize(accessControl)).isNull()
        }
        isAllowedHomeserver.assertions().isNeverCalled()
    }

    @Test
    fun `a login hint for an account on another server drops the hint and the sign-in code`() = runTest {
        listOf(
            "mxid:@kid:evil.com",
            "mxid:@kid:smith.safechat.family",
            "@kid:smith.ie",
            "mxid:kid",
        ).forEach { loginHint ->
            val link = LoginParams(accountProvider = "smith.ie", loginHint = loginHint, hs = "smith.safechat.family", signInCodeId = "code-id")

            assertThat(link.sanitize(accessControl)).isEqualTo(LoginParams(accountProvider = "smith.ie", loginHint = null))
        }
    }
}
