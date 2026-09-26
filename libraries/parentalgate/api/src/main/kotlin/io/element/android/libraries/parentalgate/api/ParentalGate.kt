/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.parentalgate.api

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import timber.log.Timber

/**
 * The parental gate: an adult-level question (a multiplication written in words, for example "What is twenty-three
 * times seven?") that must be answered before the app hands anything to another app. Family Chat declares a child audience (Google Play
 * Families policy), so every link that leaves the app and anything purchasable sits behind it.
 * See unicornops/family-chat#232 decision 10 and `libraries/parentalgate/README.md`.
 *
 * Most code never calls this directly: `Context.openUrlInExternalApp()` and `Activity.openUrlInChromeCustomTab()` in
 * `libraries/androidutils` already go through it. Use [startActivityBehindParentalGate] for any other intent that
 * leaves the app, and [ParentalGateResultContract] for an action that is not an intent (a purchase).
 *
 * The gate screen lives in `:libraries:parentalgate:impl`. This module only knows its class name, so that
 * `:libraries:androidutils` can reach it without depending on Compose or the design system.
 */
object ParentalGate {
    /** Fully qualified name of the gate activity, declared (not exported) by `:libraries:parentalgate:impl`. */
    const val ACTIVITY_CLASS_NAME = "io.element.android.libraries.parentalgate.impl.ParentalGateActivity"

    /** The [Intent] started once the gate is passed. Absent when the caller only wants a result. */
    const val EXTRA_TARGET_INTENT = "io.element.android.libraries.parentalgate.EXTRA_TARGET_INTENT"

    /** Started instead of the target when no app can handle the target. */
    const val EXTRA_FALLBACK_INTENT = "io.element.android.libraries.parentalgate.EXTRA_FALLBACK_INTENT"

    /** Shown when neither the target nor the fallback can be started. */
    const val EXTRA_NO_ACTIVITY_FOUND_MESSAGE = "io.element.android.libraries.parentalgate.EXTRA_NO_ACTIVITY_FOUND_MESSAGE"

    /**
     * Build the intent that shows the gate. With a [target], passing the gate starts it (or [fallback]); without one,
     * the gate only returns [Activity.RESULT_OK] to the caller, see [ParentalGateResultContract].
     */
    fun createIntent(
        context: Context,
        target: Intent? = null,
        fallback: Intent? = null,
        noActivityFoundMessage: String? = null,
    ): Intent {
        return Intent()
            .setClassName(context.packageName, ACTIVITY_CLASS_NAME)
            .apply {
                target?.let { putExtra(EXTRA_TARGET_INTENT, it) }
                fallback?.let { putExtra(EXTRA_FALLBACK_INTENT, it) }
                noActivityFoundMessage?.let { putExtra(EXTRA_NO_ACTIVITY_FOUND_MESSAGE, it) }
            }
    }
}

/**
 * Start [target] only after an adult has answered the parental gate. Nothing is started if the gate is dismissed or
 * answered wrongly. Use it for every intent that hands the user to another app (browser, mail, dialler, store, maps,
 * another app's launcher intent), and for new outbound entry points such as the control-panel link and the GIF
 * attribution link.
 *
 * Not for: links the app handles itself, the sign-in Custom Tab (authentication on the family's own account
 * provider), share sheets, saving or opening a received file, and system settings pages. See the README.
 *
 * Fails closed: if the gate activity is missing from the build, nothing is opened.
 */
fun Context.startActivityBehindParentalGate(
    target: Intent,
    fallback: Intent? = null,
    noActivityFoundMessage: String? = null,
) {
    val gateIntent = ParentalGate.createIntent(
        context = this,
        target = target,
        fallback = fallback,
        noActivityFoundMessage = noActivityFoundMessage,
    )
    if (this !is Activity) {
        gateIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        startActivity(gateIntent)
    } catch (exception: ActivityNotFoundException) {
        Timber.e(exception, "The parental gate is missing from this build, not opening the link")
    }
}
