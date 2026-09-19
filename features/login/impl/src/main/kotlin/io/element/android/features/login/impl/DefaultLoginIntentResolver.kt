/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl

import androidx.core.net.toUri
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import io.element.android.features.login.api.LoginIntentResolver
import io.element.android.features.login.api.LoginParams

@ContributesBinding(AppScope::class)
class DefaultLoginIntentResolver : LoginIntentResolver {
    override fun parse(uriString: String): LoginParams? {
        val uri = uriString.toUri()
        if (uri.host != LINK_HOST) return null
        if (uri.path.orEmpty().startsWith(LINK_PATH_PREFIX).not()) return null
        val accountProvider = uri.getQueryParameter("account_provider") ?: return null
        val loginHint = uri.getQueryParameter("login_hint")
        return LoginParams(
            accountProvider = accountProvider,
            loginHint = loginHint,
        )
    }

    companion object {
        /** Must be kept in sync with the app link intent filter in the application manifest. */
        private const val LINK_HOST = "safechat.family"
        private const val LINK_PATH_PREFIX = "/app/"
    }
}
