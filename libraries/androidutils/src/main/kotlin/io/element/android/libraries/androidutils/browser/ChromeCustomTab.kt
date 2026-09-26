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
 * Open the family's account provider for the FIRST sign-in on this device (OAuth), in a locked-down Custom Tab and
 * WITHOUT the parental gate. The app waits for the page to hand back through its redirect, so this is sign-in, not a
 * link out; gating it would lock children out of their own account. The tab has no "Open in browser", share, bookmark
 * or download entries, and does not hand links to other apps.
 *
 * Anything else, including account actions while someone is signed in (adding an account, resetting the identity,
 * approving a new device), must use [openAccountUrlBehindParentalGate]; plain links use [openUrlInChromeCustomTab].
 */
@ParentalGateExempt
fun Activity.openAuthenticationUrlInChromeCustomTab(
    session: CustomTabsSession?,
    darkTheme: Boolean,
    url: String
) {
    val uri = url.toUri()
    try {
        buildCustomTabsIntent(session, darkTheme, lockedDown = true).launchUrl(this, uri)
    } catch (_: ActivityNotFoundException) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (_: ActivityNotFoundException) {
            toast(R.string.error_no_compatible_app_found)
        }
    }
}

/**
 * Open a page of the family's account provider for an account action only an adult should take while someone is signed
 * in (adding an account, resetting the identity, approving a new device): behind the parental gate, then in the same
 * locked-down Custom Tab as [openAuthenticationUrlInChromeCustomTab].
 */
fun Activity.openAccountUrlBehindParentalGate(
    session: CustomTabsSession?,
    darkTheme: Boolean,
    url: String
) {
    val uri = url.toUri()
    startActivityBehindParentalGate(
        target = buildCustomTabsIntent(session, darkTheme, lockedDown = true).intent.setData(uri),
        fallback = Intent(Intent.ACTION_VIEW, uri),
        noActivityFoundMessage = getString(R.string.error_no_compatible_app_found),
    )
}

/**
 * @param session the Custom Tabs session to use, if any.
 * @param darkTheme whether the tab uses the dark colour scheme.
 * @param lockedDown for the account provider's pages: no "Open in browser", share, bookmark or download entries, and
 * links are not handed to other apps. AuthTabIntent would be stricter still, but it returns the redirect to an activity
 * result instead of the app's OAuth redirect intent filter the sign-in flow is built on.
 */
private fun buildCustomTabsIntent(
    session: CustomTabsSession?,
    darkTheme: Boolean,
    lockedDown: Boolean = false,
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
        .apply {
            if (lockedDown) {
                setShareState(CustomTabsIntent.SHARE_STATE_OFF)
                setOpenInBrowserButtonState(CustomTabsIntent.OPEN_IN_BROWSER_STATE_OFF)
                setBookmarksButtonEnabled(false)
                setDownloadButtonEnabled(false)
                setSendToExternalDefaultHandlerEnabled(false)
                setInstantAppsEnabled(false)
            }
        }
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
