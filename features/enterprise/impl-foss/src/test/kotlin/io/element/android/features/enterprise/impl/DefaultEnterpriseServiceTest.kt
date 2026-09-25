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
        listOf(
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
            "http://smith.safechat.family",
            "ftp://smith.safechat.family",
            "https://smith.safechat.family:port",
            "https://-smith.safechat.family",
            "https://smith..safechat.family",
            "https://smith .safechat.family",
        ).forEach {
            assertThat(defaultEnterpriseService.isAllowedToConnectToHomeserver(it)).isFalse()
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
}
