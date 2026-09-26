/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.androidutils.system

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import io.element.android.libraries.androidutils.compat.queryIntentActivitiesCompat

/**
 * Open [uri] inside this app if one of the app's own activities declares it (our App Links on
 * `https://safechat.family/app/`, `matrix:` links, the notification deep link, the OAuth redirect). Those links do not
 * leave the app, so they skip the parental gate.
 *
 * The intent is pinned to our own package, so an App Link opens here even while `assetlinks.json` is unverified,
 * instead of falling through to the browser.
 *
 * @return true if the link was opened in-app, false if the caller must treat it as a link that leaves the app.
 */
internal fun Context.openInAppIfHandled(uri: Uri): Boolean {
    val intent = Intent(Intent.ACTION_VIEW, uri).setPackage(packageName)
    val isHandledInApp = packageManager.queryIntentActivitiesCompat(intent, PackageManager.MATCH_DEFAULT_ONLY).isNotEmpty()
    if (!isHandledInApp) return false
    if (this !is Activity) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    return try {
        startActivity(intent)
        true
    } catch (_: ActivityNotFoundException) {
        false
    }
}
