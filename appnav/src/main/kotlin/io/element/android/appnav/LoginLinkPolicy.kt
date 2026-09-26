/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appnav

import io.element.android.features.login.api.LoginParams
import io.element.android.features.login.api.accesscontrol.AccountProviderAccessControl
import io.element.android.libraries.core.uri.ensureProtocol
import io.element.android.libraries.matrix.api.core.MatrixPatterns
import timber.log.Timber

/**
 * Family Chat: what the app acts on from a sign-in link (`docs/client-login-links.md` in unicornops/family-chat,
 * and unicornops/family-chat#254 for families on their own domain). Returns `null` when the whole link is ignored.
 *
 * - `account_provider` may be any well-formed server name, a family's own domain (e.g. `smith.ie`) included: it is
 *   only used through `.well-known` discovery, and the homeserver it resolves to is held to the allowlist before any
 *   credentials are sent. A malformed one (or the safechat.family apex) makes the whole link ignored.
 * - `login_hint` must be `mxid:` and a Matrix ID on `account_provider`; otherwise the hint and the sign-in code are
 *   dropped and only the account provider is used.
 * - A sign-in code is only kept when `hs`, the host it is redeemed against without any discovery, is itself an
 *   allowed homeserver (`*.safechat.family`).
 */
internal suspend fun LoginParams.sanitize(accessControl: AccountProviderAccessControl): LoginParams? {
    if (!accessControl.isAllowedToConnectToAccountProvider(accountProvider.ensureProtocol())) {
        Timber.w("Login link ignored, its account provider is not allowed")
        return null
    }
    var params = this
    val loginHint = params.loginHint
    if (loginHint != null && !isLoginHintFor(loginHint, params.accountProvider)) {
        Timber.w("Login link: login hint and sign-in code ignored, the hint is not an account on the account provider")
        params = params.copy(loginHint = null, hs = null, signInCodeId = null)
    }
    val hs = params.hs
    if (hs != null && !accessControl.isAllowedToConnectToHomeserver("https://$hs")) {
        Timber.w("Login link: sign-in code ignored, we are not allowed to connect to its homeserver")
        params = params.copy(hs = null, signInCodeId = null)
    }
    return params
}

/** Whether [loginHint] is `mxid:<Matrix ID>` for an account on [accountProvider]. */
private fun isLoginHintFor(loginHint: String, accountProvider: String): Boolean {
    if (!loginHint.startsWith(MXID_PREFIX)) return false
    val userId = loginHint.removePrefix(MXID_PREFIX)
    if (!MatrixPatterns.isUserId(userId)) return false
    val serverName = accountProvider.trim().removePrefix("https://").removeSuffix("/")
    return userId.substringAfter(':').equals(serverName, ignoreCase = true)
}

private const val MXID_PREFIX = "mxid:"
