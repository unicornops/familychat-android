/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.api.accesscontrol

/**
 * Enforces the restriction an enterprise deployment can put on which account providers the user may sign in to.
 *
 * Family Chat: an account provider (a server name, typed or from a link) is only the start. It may be a family's own
 * domain, and what is held to the `*.safechat.family` allowlist is the homeserver URL it resolves to; the matrix
 * authentication service refuses a resolved homeserver outside of it before sending any credentials.
 */
interface AccountProviderAccessControl {
    /**
     * Whether sign-in to this provider is permitted; `true` on a build with no such restriction. For Family Chat,
     * any well-formed server name is, subject to where `.well-known` discovery resolves it to.
     *
     * @param accountProviderUrl the server the user is trying to use.
     */
    suspend fun isAllowedToConnectToAccountProvider(accountProviderUrl: String): Boolean

    /**
     * Whether the app may talk to this homeserver directly, without discovery: for instance to redeem a sign-in
     * code against the host a link names. For Family Chat, only a family subdomain of `safechat.family` is.
     *
     * @param homeserverUrl the homeserver URL.
     */
    suspend fun isAllowedToConnectToHomeserver(homeserverUrl: String): Boolean
}
