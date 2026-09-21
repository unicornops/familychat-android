/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.features.login.api.LoginParams
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test

class DefaultLoginIntentResolverTest : RobolectricTest() {
    @Test
    fun `nominal case`() {
        val sut = DefaultLoginIntentResolver()
        val uriString = "https://safechat.family/app/?account_provider=example.org&login_hint=mxid:@alice:example.org"
        assertThat(sut.parse(uriString)).isEqualTo(
            LoginParams(
                accountProvider = "example.org",
                loginHint = "mxid:@alice:example.org",
            )
        )
    }

    @Test
    fun `extra unknown param`() {
        val sut = DefaultLoginIntentResolver()
        val uriString = "https://safechat.family/app/?account_provider=example.org&login_hint=mxid:@alice:example.org&extra=uknown"
        assertThat(sut.parse(uriString)).isEqualTo(
            LoginParams(
                accountProvider = "example.org",
                loginHint = "mxid:@alice:example.org",
            )
        )
    }

    @Test
    fun `no account provider`() {
        val sut = DefaultLoginIntentResolver()
        val uriString = "https://safechat.family/app/?login_hint=mxid:@alice:example.org"
        assertThat(sut.parse(uriString)).isNull()
    }

    @Test
    fun `no path`() {
        val sut = DefaultLoginIntentResolver()
        val uriString = "https://safechat.family?account_provider=example.org&login_hint=mxid:@alice:example.org"
        assertThat(sut.parse(uriString)).isNull()
    }

    @Test
    fun `wrong path`() {
        val sut = DefaultLoginIntentResolver()
        val uriString = "https://safechat.family/wrong?account_provider=example.org&login_hint=mxid:@alice:example.org"
        assertThat(sut.parse(uriString)).isNull()
    }

    @Test
    fun `wrong host`() {
        val sut = DefaultLoginIntentResolver()
        val uriString = "https://wrong.example.org/app/?account_provider=example.org&login_hint=mxid:@alice:example.org"
        assertThat(sut.parse(uriString)).isNull()
    }

    @Test
    fun `no login_hint param`() {
        val sut = DefaultLoginIntentResolver()
        val uriString = "https://safechat.family/app/?account_provider=example.org"
        assertThat(sut.parse(uriString)).isEqualTo(
            LoginParams(
                accountProvider = "example.org",
                loginHint = null,
            )
        )
    }

    @Test
    fun `sign-in code with hs and token`() {
        val sut = DefaultLoginIntentResolver()
        val uriString = "https://safechat.family/app/login?account_provider=smith.safechat.family" +
            "&login_hint=mxid:@ana:smith.safechat.family&hs=Smith.safechat.family&token=syl_abc_DEF-123"
        assertThat(sut.parse(uriString)).isEqualTo(
            LoginParams(
                accountProvider = "smith.safechat.family",
                loginHint = "mxid:@ana:smith.safechat.family",
                hs = "smith.safechat.family",
                token = "syl_abc_DEF-123",
            )
        )
    }

    @Test
    fun `sign-in code keeps an explicit port on hs`() {
        val sut = DefaultLoginIntentResolver()
        val uriString = "https://safechat.family/app/login?account_provider=example.org&hs=example.org:8448&token=abc"
        assertThat(sut.parse(uriString)?.hs).isEqualTo("example.org:8448")
        assertThat(sut.parse(uriString)?.token).isEqualTo("abc")
    }

    @Test
    fun `token without hs is dropped`() {
        val sut = DefaultLoginIntentResolver()
        val uriString = "https://safechat.family/app/login?account_provider=example.org&token=abc"
        assertThat(sut.parse(uriString)).isEqualTo(
            LoginParams(
                accountProvider = "example.org",
                loginHint = null,
                hs = null,
                token = null,
            )
        )
    }

    @Test
    fun `hs without token is dropped`() {
        val sut = DefaultLoginIntentResolver()
        val uriString = "https://safechat.family/app/login?account_provider=example.org&hs=example.org"
        assertThat(sut.parse(uriString)?.hs).isNull()
        assertThat(sut.parse(uriString)?.token).isNull()
    }

    @Test
    fun `malformed hs drops the code entirely`() {
        val sut = DefaultLoginIntentResolver()
        listOf(
            "https://example.org",
            "example.org/path",
            "user@example.org",
            "example.org:abc",
            "-example.org",
            "exa mple.org",
        ).forEach { hs ->
            val uriString = "https://safechat.family/app/login?account_provider=example.org&hs=${android.net.Uri.encode(hs)}&token=abc"
            val params = sut.parse(uriString)
            assertThat(params).isNotNull()
            assertThat(params!!.hs).isNull()
            assertThat(params.token).isNull()
        }
    }

    @Test
    fun `token is redacted from toString`() {
        val params = LoginParams(
            accountProvider = "example.org",
            loginHint = null,
            hs = "example.org",
            token = "secret-token",
        )
        assertThat(params.toString()).doesNotContain("secret-token")
        assertThat(params.toString()).contains("<redacted>")
    }
}
