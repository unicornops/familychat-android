/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.enterprise.impl

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.element.android.compound.colors.SemanticColorsLightDark
import io.element.android.features.enterprise.api.BugReportUrl
import io.element.android.libraries.matrix.test.A_HOMESERVER_URL
import io.element.android.libraries.matrix.test.A_SESSION_ID
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DefaultEnterpriseServiceTest {
    @Test
    fun `homeserverAllowList only contains the Family Chat account provider`() {
        val defaultEnterpriseService = DefaultEnterpriseService()
        assertThat(defaultEnterpriseService.homeserverAllowList()).containsExactly("safechat.family")
    }

    @Test
    fun `no account provider is forced, the user enters their family's server`() {
        val defaultEnterpriseService = DefaultEnterpriseService()
        assertThat(defaultEnterpriseService.forcedAccountProvider()).isNull()
    }

    @Test
    fun `isAllowedToConnectToHomeserver accepts the family subdomains of the account provider`() = runTest {
        val defaultEnterpriseService = DefaultEnterpriseService()
        listOf(
            "smith.safechat.family",
            "Smith.SafeChat.Family",
            "https://smith.safechat.family",
            "https://smith.safechat.family/",
            "https://smith.safechat.family:8448",
            "https://smith.safechat.family:8448/",
            " https://matrix.smith.safechat.family ",
        ).forEach {
            assertThat(defaultEnterpriseService.isAllowedToConnectToHomeserver(it)).isTrue()
        }
    }

    @Test
    fun `isAllowedToConnectToHomeserver rejects any other homeserver`() = runTest {
        val defaultEnterpriseService = DefaultEnterpriseService()
        listOf(
            A_HOMESERVER_URL,
            "matrix.org",
            // The apex serves the website, not a homeserver
            "safechat.family",
            "https://safechat.family",
            "https://safechat.family/",
            "https://safechat.family.evil.example",
            "https://evilsafechat.family",
            "",
        ).forEach {
            assertThat(defaultEnterpriseService.isAllowedToConnectToHomeserver(it)).isFalse()
        }
    }

    @Test
    fun `isAllowedToConnectToHomeserver rejects anything that is not a bare host, whatever it ends with`() = runTest {
        val defaultEnterpriseService = DefaultEnterpriseService()
        (notABareHost + "http://smith.safechat.family").forEach {
            assertThat(defaultEnterpriseService.isAllowedToConnectToHomeserver(it)).isFalse()
        }
    }

    @Test
    fun `isAllowedAccountProvider accepts any well-formed server name, a family's own domain included`() = runTest {
        val defaultEnterpriseService = DefaultEnterpriseService()
        listOf(
            "smith.safechat.family",
            "https://smith.safechat.family",
            // BYOD: the family's own domain, judged on where its .well-known resolves
            "smith.ie",
            "Smith.IE",
            "https://smith.ie",
            "https://smith.ie/",
            "smith.ie:8448",
            // Allowed to start discovery; refused once it resolves outside the allowlist
            "evil.com",
        ).forEach {
            assertThat(defaultEnterpriseService.isAllowedAccountProvider(it)).isTrue()
        }
    }

    @Test
    fun `isAllowedAccountProvider still refuses the apex and anything that is not a bare host`() = runTest {
        val defaultEnterpriseService = DefaultEnterpriseService()
        (
            notABareHost + listOf(
                "safechat.family",
                "https://safechat.family",
                "https://safechat.family/",
                "http://smith.ie",
                "smith.ie%2f",
                "@kid:smith.ie",
                "",
            )
        ).forEach {
            assertThat(defaultEnterpriseService.isAllowedAccountProvider(it)).isFalse()
        }
    }

    @Test
    fun `isAllowedResolvedHomeserverUrl only accepts an https family subdomain`() = runTest {
        val defaultEnterpriseService = DefaultEnterpriseService()
        listOf(
            "https://smith.safechat.family",
            "https://smith.safechat.family/",
            "https://smith-m1.safechat.family:8448/",
            "HTTPS://Smith.SafeChat.Family",
        ).forEach {
            assertThat(defaultEnterpriseService.isAllowedResolvedHomeserverUrl(it)).isTrue()
        }
        (
            notABareHost + listOf(
                // A domain resolving to itself, or anywhere else outside the allowlist
                "https://evil.com",
                "https://smith.ie",
                "https://safechat.family",
                // Never over plain http, and never without a scheme
                "http://smith.safechat.family",
                "smith.safechat.family",
                "",
            )
        ).forEach {
            assertThat(defaultEnterpriseService.isAllowedResolvedHomeserverUrl(it)).isFalse()
        }
    }

    @Test
    fun `isEnterpriseUser always return false`() = runTest {
        val defaultEnterpriseService = DefaultEnterpriseService()
        assertThat(defaultEnterpriseService.isEnterpriseUser(A_SESSION_ID)).isFalse()
    }

    @Test
    fun `semanticColorsFlow always emits the same value`() = runTest {
        val defaultEnterpriseService = DefaultEnterpriseService()
        defaultEnterpriseService.semanticColorsFlow(null).test {
            val initialState = awaitItem()
            assertThat(initialState).isEqualTo(SemanticColorsLightDark.default)
            awaitComplete()
        }
    }

    @Test
    fun `brandColorsFlow always emits null`() = runTest {
        val defaultEnterpriseService = DefaultEnterpriseService()
        defaultEnterpriseService.brandColorsFlow(null).test {
            val initialState = awaitItem()
            assertThat(initialState).isNull()
            awaitComplete()
        }
    }

    @Test
    fun `semanticColorsFlow always emits the same value for a session`() = runTest {
        val defaultEnterpriseService = DefaultEnterpriseService()
        defaultEnterpriseService.semanticColorsFlow(A_SESSION_ID).test {
            val initialState = awaitItem()
            assertThat(initialState).isEqualTo(SemanticColorsLightDark.default)
            awaitComplete()
        }
    }

    @Test
    fun `overrideBrandColor has no effect`() = runTest {
        val defaultEnterpriseService = DefaultEnterpriseService()
        defaultEnterpriseService.overrideBrandColor(A_SESSION_ID, "aColor")
    }

    @Test
    fun `firebasePushGateway returns null`() = runTest {
        val defaultEnterpriseService = DefaultEnterpriseService()
        assertThat(defaultEnterpriseService.firebasePushGateway()).isNull()
    }

    @Test
    fun `unifiedPushDefaultPushGateway returns null`() = runTest {
        val defaultEnterpriseService = DefaultEnterpriseService()
        assertThat(defaultEnterpriseService.unifiedPushDefaultPushGateway()).isNull()
    }

    @Test
    fun `bugReportUrlFlow only emits UseDefault`() = runTest {
        val defaultEnterpriseService = DefaultEnterpriseService()
        defaultEnterpriseService.bugReportUrlFlow(A_SESSION_ID).test {
            assertThat(awaitItem()).isEqualTo(BugReportUrl.UseDefault)
            awaitComplete()
        }
    }

    @Test
    fun `getNoisyNotificationChannelId returns null`() = runTest {
        val defaultEnterpriseService = DefaultEnterpriseService()
        assertThat(defaultEnterpriseService.getNoisyNotificationChannelId(A_SESSION_ID)).isNull()
    }

    /** Values whose real host is not the one they end with, or that are not a bare host at all: always refused. */
    private val notABareHost = listOf(
        // The real host of each of these is evil.com
        "https://evil.com?.safechat.family",
        "https://evil.com#.safechat.family",
        "https://evil.com\\.safechat.family",
        "https://evil.com/.safechat.family",
        "https://user@evil.com/x.safechat.family",
        "evil.com?.safechat.family",
        // Userinfo, paths, other schemes and malformed hosts
        "https://ana@smith.safechat.family",
        "https://smith.safechat.family/path",
        "https://smith.safechat.family//",
        "ftp://smith.safechat.family",
        "https://smith.safechat.family:port",
        "https://-smith.safechat.family",
        "https://smith..safechat.family",
        "https://smith .safechat.family",
        "https://smith.safe\tchat.family",
        "https://evil.com%2f.safechat.family",
    )
}
