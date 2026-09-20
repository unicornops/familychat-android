/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2024, 2025 New Vector Ltd.
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
     * Family Chat is locked to its own account provider. A single entry also makes it the default
     * account provider, which hides the server picker and the "create account" entry point.
     */
    override fun homeserverAllowList(): List<String> = listOf(ACCOUNT_PROVIDER)

    /**
     * Allows [ACCOUNT_PROVIDER] and every family subdomain of it. Custom domains brought by a
     * family (BYOD) are not accepted yet, see unicornops/family-chat#234.
     */
    override suspend fun isAllowedToConnectToHomeserver(homeserverUrl: String): Boolean {
        val host = homeserverUrl
            .substringAfter("://")
            .substringBefore('/')
            .substringBefore(':')
            .trim()
            .lowercase()
        return host == ACCOUNT_PROVIDER || host.endsWith(".$ACCOUNT_PROVIDER")
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
    }
}
