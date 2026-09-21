/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 * Copyright 2026 Unicorn Operations Ltd.
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
        // A sign-in code is only usable together with the host to redeem it against. `hs` is a bare
        // hostname (optionally `:port`), never a URL, so the link cannot change the scheme or add a path.
        val hs = uri.getQueryParameter("hs")?.trim()?.lowercase()?.takeIf { it.matches(HOST_PORT_REGEX) }
        val token = uri.getQueryParameter("token")?.takeIf { it.isNotBlank() && hs != null }
        return LoginParams(
            accountProvider = accountProvider,
            loginHint = loginHint,
            hs = hs.takeIf { token != null },
            token = token,
        )
    }

    companion object {
        /** Must be kept in sync with the app link intent filter in the application manifest. */
        private const val LINK_HOST = "safechat.family"
        private const val LINK_PATH_PREFIX = "/app/"

        /** DNS hostname labels, optionally followed by a port. */
        private val HOST_PORT_REGEX = Regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?(\\.[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?)*(:[0-9]{1,5})?$")
    }
}
