/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.auth

sealed class AuthenticationException(message: String?) : Exception(message) {
    data class AccountAlreadyLoggedIn(
        val userId: String,
    ) : AuthenticationException(null)

    class InvalidServerName(message: String?) : AuthenticationException(message)
    class SlidingSyncVersion(message: String?) : AuthenticationException(message)
    class ServerUnreachable(message: String?) : AuthenticationException(message)
    class OAuth(message: String?) : AuthenticationException(message)
    class Generic(message: String?) : AuthenticationException(message)

    /**
     * The server name resolved (through `.well-known` discovery) to a homeserver the app is not allowed to use.
     * Nothing but that discovery was sent: no credentials.
     *
     * @property homeserverUrl the resolved homeserver URL.
     */
    class HomeserverNotAllowed(val homeserverUrl: String) : AuthenticationException("Homeserver not allowed: $homeserverUrl")
}
