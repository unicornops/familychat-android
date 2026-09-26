/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.enterprise.impl

import androidx.compose.ui.graphics.Color
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.compound.colors.SemanticColorsLightDark
import io.element.android.features.enterprise.api.BugReportUrl
import io.element.android.features.enterprise.api.EnterpriseService
import io.element.android.libraries.matrix.api.ClientUrlContentFetcher
import io.element.android.libraries.matrix.api.core.SessionId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@ContributesBinding(AppScope::class)
class DefaultEnterpriseService : EnterpriseService {
    override suspend fun isEnterpriseUser(sessionId: SessionId) = false
    override suspend fun tweakMasUrl(url: String, urlContentFetcher: ClientUrlContentFetcher) = url

    /**
     * Family Chat only connects to family servers under [ACCOUNT_PROVIDER]. It is the configured account
     * provider (the error messages name it), but it is not itself a homeserver, see [forcedAccountProvider].
     */
    override fun homeserverAllowList(): List<String> = listOf(ACCOUNT_PROVIDER)

    /**
     * Nothing is forced: every family has its own server (`<family>.safechat.family`, or the family's own domain
     * delegating to one), so the user enters theirs, or a sign-in link names it. [isAllowedAccountProvider] and
     * [isAllowedResolvedHomeserverUrl] keep that within the allowlist.
     */
    override fun forcedAccountProvider(): String? = null

    /**
     * Allows every family subdomain of [ACCOUNT_PROVIDER] and nothing else, not even the apex, which serves the
     * website rather than a homeserver. This is the check for a homeserver the app talks to directly: the URL
     * `.well-known` discovery resolved to, or the `hs` host a sign-in code is redeemed against.
     *
     * The input is a bare `host[:port]`, optionally prefixed with `https://` and followed by a single `/`.
     * Anything else (another scheme, a path, a query, a fragment, user info, a backslash) is refused rather than
     * guessed at, so that a value such as `https://evil.example?.safechat.family` cannot pass for a family host.
     */
    override suspend fun isAllowedToConnectToHomeserver(homeserverUrl: String): Boolean {
        val host = parseHost(homeserverUrl) ?: return false
        return host.endsWith(".$ACCOUNT_PROVIDER")
    }

    /**
     * Any well-formed server name, as the family's own domain (BYOD, e.g. `smith.ie`) is not under
     * [ACCOUNT_PROVIDER]: it only serves the `.well-known` documents pointing at the family's server. What decides is
     * where discovery resolves it to, see [isAllowedResolvedHomeserverUrl], and no credentials are sent before that.
     * The input is held to the same strict shape as in [isAllowedToConnectToHomeserver]. The apex
     * [ACCOUNT_PROVIDER] is still refused: it is the website, never a family's server.
     */
    override suspend fun isAllowedAccountProvider(accountProvider: String): Boolean {
        val host = parseHost(accountProvider) ?: return false
        return host != ACCOUNT_PROVIDER
    }

    /**
     * The homeserver URL a server name resolved to must be `https://` and a family subdomain of
     * [ACCOUNT_PROVIDER]. A domain whose `.well-known` points at some family's server is then no different from
     * typing that family's server directly.
     */
    override suspend fun isAllowedResolvedHomeserverUrl(homeserverUrl: String): Boolean {
        val url = homeserverUrl.trim()
        return url.startsWith(HTTPS_SCHEME, ignoreCase = true) && isAllowedToConnectToHomeserver(url)
    }

    private fun parseHost(homeserverUrl: String): String? {
        val hostAndPort = homeserverUrl
            .trim()
            .lowercase()
            .removePrefix(HTTPS_SCHEME)
            .removeSuffix("/")
        return hostAndPort
            .takeIf { HOST_PORT_REGEX.matches(it) }
            ?.substringBefore(':')
    }

    override suspend fun isElementProEnforced(serverName: String): Boolean = false

    override suspend fun overrideBrandColor(sessionId: SessionId?, brandColor: String?) = Unit

    override fun brandColorsFlow(sessionId: SessionId?): Flow<Color?> {
        return flowOf(null)
    }

    override fun semanticColorsFlow(sessionId: SessionId?): Flow<SemanticColorsLightDark> {
        return flowOf(SemanticColorsLightDark.default)
    }

    override fun firebasePushGateway(): String? = null
    override fun unifiedPushDefaultPushGateway(): String? = null

    override fun bugReportUrlFlow(sessionId: SessionId?): Flow<BugReportUrl> {
        return flowOf(BugReportUrl.UseDefault)
    }

    override fun getNoisyNotificationChannelId(sessionId: SessionId): String? = null

    companion object {
        /** The only account provider Family Chat signs in to. */
        const val ACCOUNT_PROVIDER = "safechat.family"

        private const val HTTPS_SCHEME = "https://"

        /** DNS hostname labels, optionally followed by a port; the same shape the login link accepts for `hs`. */
        private val HOST_PORT_REGEX = Regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)*(:[0-9]{1,5})?$")
    }
}
