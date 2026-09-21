/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.api

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Parameters to start the login flow, when the application is opened
 * from a safechat.family app link (`docs/client-login-links.md` in unicornops/family-chat).
 *
 * [hs] and [token] come from a control panel sign-in code: the token is a single-use, short-lived
 * `m.login.token` to redeem against `https://<hs>`. They are always both present or both absent.
 */
@Parcelize
data class LoginParams(
    val accountProvider: String,
    val loginHint: String?,
    val hs: String? = null,
    val token: String? = null,
) : Parcelable {
    /** The token is a bearer credential: it must never reach a log line. */
    override fun toString(): String {
        return "LoginParams(accountProvider=$accountProvider, loginHint=$loginHint, hs=$hs, token=${if (token == null) "null" else "<redacted>"})"
    }
}
