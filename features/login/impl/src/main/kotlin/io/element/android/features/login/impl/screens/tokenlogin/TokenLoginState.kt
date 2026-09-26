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
    /**
     * The family's server name, as the link names it (`account_provider`): the family's own domain for a family
     * that brought one, rather than the host the code is redeemed against.
     */
    val serverName: String,
    /** The Matrix ID the link names, if it names one. */
    val userId: String?,
    /** [AsyncData.Uninitialized] while waiting for the user to confirm, then the redemption. */
    val loginAction: AsyncData<SessionId>,
    val eventSink: (TokenLoginEvent) -> Unit,
) {
    /** Who the confirmation says the user is about to sign in as. */
    val accountDisplayName: String = userId ?: serverName
}
