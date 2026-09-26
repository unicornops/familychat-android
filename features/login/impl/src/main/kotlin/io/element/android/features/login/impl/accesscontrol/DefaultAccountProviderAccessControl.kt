/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.accesscontrol

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.enterprise.api.EnterpriseService
import io.element.android.features.enterprise.api.IsEnterpriseBuild
import io.element.android.features.login.api.accesscontrol.AccountProviderAccessControl
import io.element.android.features.login.impl.changeserver.AccountProviderAccessException

@ContributesBinding(AppScope::class)
class DefaultAccountProviderAccessControl(
    private val isEnterpriseBuild: IsEnterpriseBuild,
    private val enterpriseService: EnterpriseService,
) : AccountProviderAccessControl {
    override suspend fun isAllowedToConnectToAccountProvider(accountProviderUrl: String) = try {
        assertIsAllowedToConnectToAccountProvider(
            title = accountProviderUrl,
            accountProviderUrl = accountProviderUrl,
        )
        true
    } catch (_: AccountProviderAccessException) {
        false
    }

    override suspend fun isAllowedToConnectToHomeserver(homeserverUrl: String) = enterpriseService.isAllowedToConnectToHomeserver(homeserverUrl)

    /**
     * Throws when the user may not start signing in with this account provider. For Family Chat any well-formed
     * server name passes, a family's own domain included: the matrix authentication service then refuses it if its
     * `.well-known` resolves outside the allowlist, before any credentials are sent.
     */
    @Throws(AccountProviderAccessException::class)
    suspend fun assertIsAllowedToConnectToAccountProvider(
        title: String,
        accountProviderUrl: String,
    ) {
        if (isEnterpriseBuild().not()) {
            // Ensure that Element Pro is not required for this account provider
            if (enterpriseService.isElementProEnforced(accountProviderUrl)) {
                throw AccountProviderAccessException.NeedElementProException(
                    unauthorisedAccountProviderTitle = title,
                    applicationId = ELEMENT_PRO_APPLICATION_ID,
                )
            }
        }
        if (enterpriseService.isAllowedAccountProvider(accountProviderUrl).not()) {
            throw AccountProviderAccessException.UnauthorizedAccountProviderException(
                unauthorisedAccountProviderTitle = title,
                authorisedAccountProviderTitles = enterpriseService.homeserverAllowList(),
            )
        }
    }

    companion object {
        const val ELEMENT_PRO_APPLICATION_ID = "io.element.enterprise"
    }
}
