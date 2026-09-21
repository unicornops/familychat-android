/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.tokenlogin

import io.element.android.libraries.architecture.AsyncData
import io.element.android.libraries.matrix.api.core.SessionId

data class TokenLoginState(
    /** The host the code is being redeemed against, shown to the user. */
    val homeserver: String,
    val loginAction: AsyncData<SessionId>,
    val eventSink: (TokenLoginEvent) -> Unit,
)
