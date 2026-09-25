/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.screens.tokenlogin

sealed interface TokenLoginEvent {
    /** The user confirmed they want to sign in with the code: redeem it. */
    data object Confirm : TokenLoginEvent

    /** The user declined the code, or acknowledged that it could not be used: continue with the password flow. */
    data object ContinueWithPassword : TokenLoginEvent
}
