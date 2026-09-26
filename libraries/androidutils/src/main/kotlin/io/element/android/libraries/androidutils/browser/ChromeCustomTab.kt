/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.androidutils.browser

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.provider.Browser
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.browser.customtabs.CustomTabsSession
import androidx.core.net.toUri
import io.element.android.libraries.androidutils.R
import io.element.android.libraries.androidutils.system.openInAppIfHandled
import io.element.android.libraries.androidutils.system.toast
import io.element.android.libraries.parentalgate.api.ParentalGateExempt
import io.element.android.libraries.parentalgate.api.startActivityBehindParentalGate
import java.util.Locale

/**
 * Open url in custom tab or, if not available, in the default browser, behind the parental gate: nothing opens until
 * an adult has answered the gate's question. A link the app handles itself opens in-app, without the gate.
 * If several compatible browsers are installed, the user will be proposed to choose one.
 * Ref: https://developer.chrome.com/multidevice/android/customtabs.
 * See `libraries/parentalgate/README.md`.
 */
fun Activity.openUrlInChromeCustomTab(
    session: CustomTabsSession?,
    darkTheme: Boolean,
    url: String
) {
    val uri = url.toUri()
    if (openInAppIfHandled(uri)) return
    // What CustomTabsIntent.launchUrl() would start, handed to the gate instead.
    val customTabIntent = buildCustomTabsIntent(session, darkTheme).intent.setData(uri)
    startActivityBehindParentalGate(
        target = customTabIntent,
        fallback = Intent(Intent.ACTION_VIEW, uri),
        noActivityFoundMessage = getString(R.string.error_no_compatible_app_found),
    )
}

/**
 * Open an authentication page of the family's own account provider (OAuth sign-in, identity reset approval, new
 * device approval) in a Custom Tab, WITHOUT the parental gate. The app waits for the page to hand back through its
 * redirect, so this is sign-in, not a link out; gating it would lock children out of their own account.
 * Anything else must use [openUrlInChromeCustomTab].
 */
@ParentalGateExempt
fun Activity.openAuthenticationUrlInChromeCustomTab(
    session: CustomTabsSession?,
    darkTheme: Boolean,
    url: String
) {
    val uri = url.toUri()
    try {
        buildCustomTabsIntent(session, darkTheme).launchUrl(this, uri)
    } catch (_: ActivityNotFoundException) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            toast(R.string.error_no_compatible_app_found)
        }
    }
}

private fun buildCustomTabsIntent(
    session: CustomTabsSession?,
    darkTheme: Boolean,
): CustomTabsIntent {
    return CustomTabsIntent.Builder()
        .setDefaultColorSchemeParams(
            CustomTabColorSchemeParams.Builder()
                // TODO .setToolbarColor(ThemeUtils.getColor(context, android.R.attr.colorBackground))
                // TODO .setNavigationBarColor(ThemeUtils.getColor(context, android.R.attr.colorBackground))
                .build()
        )
        .setColorScheme(
            when (darkTheme) {
                false -> CustomTabsIntent.COLOR_SCHEME_LIGHT
                true -> CustomTabsIntent.COLOR_SCHEME_DARK
            }
        )
        .setShareIdentityEnabled(false)
        // Note: setting close button icon does not work
        // .setCloseButtonIcon(BitmapFactory.decodeResource(context.resources, R.drawable.ic_back_24dp))
        // .setStartAnimations(context, R.anim.enter_fade_in, R.anim.exit_fade_out)
        // .setExitAnimations(context, R.anim.enter_fade_in, R.anim.exit_fade_out)
        .apply { session?.let { setSession(it) } }
        .build()
        .apply {
            // Disable download button
            intent.putExtra("org.chromium.chrome.browser.customtabs.EXTRA_DISABLE_DOWNLOAD_BUTTON", true)
            // Disable bookmark button
            intent.putExtra("org.chromium.chrome.browser.customtabs.EXTRA_DISABLE_STAR_BUTTON", true)
            intent.putExtra(Browser.EXTRA_HEADERS, Bundle().apply {
                putString("Accept-Language", Locale.getDefault().toLanguageTag())
            })
        }
}
