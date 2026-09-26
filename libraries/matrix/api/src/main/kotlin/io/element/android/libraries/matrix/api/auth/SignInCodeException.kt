/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.api.auth

/**
 * Family Chat: why a control panel sign-in code (`m.login.token`) could not be used. None of these ever carries
 * the code or an access token. An unreachable homeserver is an [AuthenticationException.ServerUnreachable] and an
 * account that is already signed in an [AuthenticationException.AccountAlreadyLoggedIn], as for other logins.
 */
sealed class SignInCodeException(message: String) : Exception(message) {
    /** The homeserver refused the code with HTTP 401 or 403: already used, expired or unknown. */
    class Rejected(val httpStatus: Int, val errcode: String?) : SignInCodeException("HTTP $httpStatus ${errcode.orEmpty()}")

    /** The homeserver answered something else than a login (another status, a redirect, an unreadable body). */
    class Failed(val httpStatus: Int?, val errcode: String?) : SignInCodeException("HTTP ${httpStatus ?: "-"} ${errcode.orEmpty()}")

    /**
     * The code signed into another account than the link was for (the Matrix ID of its login hint, or with none, an
     * account on its account provider); the new device was signed out again.
     */
    class UserMismatch : SignInCodeException("The sign-in code is for another account than the link named")

    /** The link's homeserver is not one this app may connect to; the code was not sent anywhere. */
    class HomeserverNotAllowed : SignInCodeException("The sign-in code's homeserver is not allowed")

    /** The code is no longer held by the app (already handed out, or expired locally). */
    class Unavailable : SignInCodeException("The sign-in code is no longer available")
}
