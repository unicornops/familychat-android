/*
 * Copyright (c) 2026 unicornops
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package config

/**
 * Build time configuration of the Family Chat Android application.
 *
 * Family Chat is a fork of Element X Android; upstream drives every one of these values from this
 * single object, so the fork keeps its branding here rather than in a private overlay module.
 */
object BuildTimeConfig {
    const val APPLICATION_ID = "family.safechat.android"
    const val APPLICATION_NAME = "Family Chat"

    /**
     * Reverse-DNS form of the App Links host, used as the base of the OAuth 2.0 redirect URI scheme.
     * Host is `safechat.family`, so the scheme base is `family.safechat`.
     */
    val METADATA_HOST_REVERSED: String? = "family.safechat"

    /** Path appended to [URL_WEBSITE] to build the OAuth `client_uri`. `null` means use the site root. */
    val OAUTH_CLIENT_URL_PATH: String? = null
    val URL_WEBSITE: String? = "https://safechat.family"
    val URL_LOGO: String? = "https://safechat.family/img/favicon.svg"
    val URL_COPYRIGHT: String? = "https://safechat.family/terms/"
    val URL_ACCEPTABLE_USE: String? = "https://safechat.family/terms/"
    val URL_PRIVACY: String? = "https://safechat.family/privacy/"
    val URL_POLICY: String? = "https://safechat.family/privacy/"

    // Location sharing stays disabled (issue #232 decision 4), so no MapTiler key ships.
    val SERVICES_MAPTILER_BASE_URL: String? = null
    val SERVICES_MAPTILER_APIKEY: String? = ""
    val SERVICES_MAPTILER_LIGHT_MAPID: String? = ""
    val SERVICES_MAPTILER_DARK_MAPID: String? = ""

    // No third-party analytics or crash reporting: empty values disable the providers entirely.
    val SERVICES_POSTHOG_HOST: String? = ""
    val SERVICES_POSTHOG_APIKEY: String? = ""
    val SERVICES_SENTRY_DSN: String? = ""
    val SERVICES_SENTRY_DSN_RUST: String? = ""

    // Rageshake is not hosted yet: an empty URL hides the bug report entry points.
    val BUG_REPORT_URL: String? = ""
    val BUG_REPORT_APP_NAME: String? = ""

    // No Firebase project exists for family.safechat.android yet (unicornops/family-chat#234), so FCM is
    // left out of the build and both store flavours use UnifiedPush. Flip this to `true` once the
    // project exists and the placeholder values in libraries/pushproviders/firebase/src/*/res/values/firebase.xml
    // have been replaced with the real ones.
    const val PUSH_CONFIG_INCLUDE_FIREBASE: Boolean = false
    const val PUSH_CONFIG_INCLUDE_UNIFIED_PUSH: Boolean = true

    // Pusher app ids registered with our Sygnal gateway (unicornops/family-chat#241).
    val PUSHER_APP_ID_RELEASE: String? = "family.safechat.android"
    val PUSHER_APP_ID_DEBUG: String? = "family.safechat.android.debug"
    val PUSHER_APP_ID_NIGHTLY: String? = "family.safechat.android.nightly"
}
