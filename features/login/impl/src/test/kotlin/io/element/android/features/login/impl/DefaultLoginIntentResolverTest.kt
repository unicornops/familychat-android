/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl

import android.os.Parcel
import com.google.common.truth.Truth.assertThat
import io.element.android.features.login.api.LoginParams
import io.element.android.features.login.impl.tokenlogin.SignInCodeStore
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test

class DefaultLoginIntentResolverTest : RobolectricTest() {
    @Test
    fun `nominal case`() {
        val sut = DefaultLoginIntentResolver(SignInCodeStore())
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
        val sut = DefaultLoginIntentResolver(SignInCodeStore())
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
        val sut = DefaultLoginIntentResolver(SignInCodeStore())
        val uriString = "https://safechat.family/app/?login_hint=mxid:@alice:example.org"
        assertThat(sut.parse(uriString)).isNull()
    }

    @Test
    fun `no path`() {
        val sut = DefaultLoginIntentResolver(SignInCodeStore())
        val uriString = "https://safechat.family?account_provider=example.org&login_hint=mxid:@alice:example.org"
        assertThat(sut.parse(uriString)).isNull()
    }

    @Test
    fun `wrong path`() {
        val sut = DefaultLoginIntentResolver(SignInCodeStore())
        val uriString = "https://safechat.family/wrong?account_provider=example.org&login_hint=mxid:@alice:example.org"
        assertThat(sut.parse(uriString)).isNull()
    }

    @Test
    fun `wrong host`() {
        val sut = DefaultLoginIntentResolver(SignInCodeStore())
        val uriString = "https://wrong.example.org/app/?account_provider=example.org&login_hint=mxid:@alice:example.org"
        assertThat(sut.parse(uriString)).isNull()
    }

    @Test
    fun `no login_hint param`() {
        val sut = DefaultLoginIntentResolver(SignInCodeStore())
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
        val signInCodeStore = SignInCodeStore()
        val sut = DefaultLoginIntentResolver(signInCodeStore)
        val uriString = "https://safechat.family/app/login?account_provider=smith.safechat.family" +
            "&login_hint=mxid:@ana:smith.safechat.family&hs=Smith.safechat.family&token=syl_abc_DEF-123"
        val params = sut.parse(uriString)!!
        assertThat(params.accountProvider).isEqualTo("smith.safechat.family")
        assertThat(params.loginHint).isEqualTo("mxid:@ana:smith.safechat.family")
        assertThat(params.hs).isEqualTo("smith.safechat.family")
        // The params only name the code; the token itself stays in memory
        assertThat(params.signInCodeId).isNotNull()
        assertThat(signInCodeStore.consume(params.signInCodeId)).isEqualTo("syl_abc_DEF-123")
    }

    @Test
    fun `the parcelled params never carry the token`() {
        val sut = DefaultLoginIntentResolver(SignInCodeStore())
        val uriString = "https://safechat.family/app/login?account_provider=smith.safechat.family&hs=smith.safechat.family&token=syl_secret_token"
        val params = sut.parse(uriString)!!
        val parcel = Parcel.obtain()
        try {
            params.writeToParcel(parcel, 0)
            val bytes = parcel.marshall()
            assertThat(String(bytes, Charsets.UTF_16LE)).doesNotContain("syl_secret_token")
            assertThat(String(bytes, Charsets.UTF_8)).doesNotContain("syl_secret_token")
        } finally {
            parcel.recycle()
        }
        assertThat(params.toString()).doesNotContain("syl_secret_token")
    }

    @Test
    fun `sign-in code keeps an explicit port on hs`() {
        val sut = DefaultLoginIntentResolver(SignInCodeStore())
        val uriString = "https://safechat.family/app/login?account_provider=example.org&hs=example.org:8448&token=abc"
        val params = sut.parse(uriString)!!
        assertThat(params.hs).isEqualTo("example.org:8448")
        assertThat(params.signInCodeId).isNotNull()
    }

    @Test
    fun `token without hs is dropped`() {
        val signInCodeStore = SignInCodeStore()
        val sut = DefaultLoginIntentResolver(signInCodeStore)
        val uriString = "https://safechat.family/app/login?account_provider=example.org&token=abc"
        assertThat(sut.parse(uriString)).isEqualTo(
            LoginParams(
                accountProvider = "example.org",
                loginHint = null,
                hs = null,
                signInCodeId = null,
            )
        )
    }

    @Test
    fun `hs without token is dropped`() {
        val sut = DefaultLoginIntentResolver(SignInCodeStore())
        val uriString = "https://safechat.family/app/login?account_provider=example.org&hs=example.org"
        assertThat(sut.parse(uriString)?.hs).isNull()
        assertThat(sut.parse(uriString)?.signInCodeId).isNull()
    }

    @Test
    fun `malformed hs drops the code entirely`() {
        val sut = DefaultLoginIntentResolver(SignInCodeStore())
        listOf(
            "https://example.org",
            "example.org/path",
            "user@example.org",
            "example.org:abc",
            "-example.org",
            "exa mple.org",
            "evil.com?.safechat.family",
            "evil.com#.safechat.family",
            "evil.com\\.safechat.family",
        ).forEach { hs ->
            val uriString = "https://safechat.family/app/login?account_provider=example.org&hs=${android.net.Uri.encode(hs)}&token=abc"
            val params = sut.parse(uriString)
            assertThat(params).isNotNull()
            assertThat(params!!.hs).isNull()
            assertThat(params.signInCodeId).isNull()
        }
    }
}
