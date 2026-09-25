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
 * [hs] and [signInCodeId] come from a control panel sign-in code, a single-use, short-lived `m.login.token` to
 * redeem against `https://<hs>`. They are always both present or both absent. This class is parcelled into the
 * navigation state, so it never carries the token itself: [signInCodeId] only names it in the login feature's
 * in-memory store, and does not resolve any more once the process is gone.
 */
@Parcelize
data class LoginParams(
    val accountProvider: String,
    val loginHint: String?,
    val hs: String? = null,
    val signInCodeId: String? = null,
) : Parcelable
