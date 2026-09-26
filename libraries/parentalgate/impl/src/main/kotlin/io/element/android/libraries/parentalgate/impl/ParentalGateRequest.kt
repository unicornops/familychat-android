/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.parentalgate.impl

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.core.content.IntentCompat
import io.element.android.libraries.parentalgate.api.ParentalGate
import timber.log.Timber

/**
 * What to do once the gate is passed, read from the intent built by [ParentalGate.createIntent].
 * A request without a [target] only reports the result to the caller.
 */
data class ParentalGateRequest(
    val target: Intent?,
    val fallback: Intent?,
    val noActivityFoundMessage: String?,
) {
    companion object {
        fun from(intent: Intent): ParentalGateRequest = ParentalGateRequest(
            target = IntentCompat.getParcelableExtra(intent, ParentalGate.EXTRA_TARGET_INTENT, Intent::class.java),
            fallback = IntentCompat.getParcelableExtra(intent, ParentalGate.EXTRA_FALLBACK_INTENT, Intent::class.java),
            noActivityFoundMessage = intent.getStringExtra(ParentalGate.EXTRA_NO_ACTIVITY_FOUND_MESSAGE),
        )
    }
}

/**
 * Start what the gate was guarding. Called only once the gate is passed: this is the one place in the app that starts
 * an intent leaving the app on behalf of `openUrlInExternalApp` and friends.
 */
fun Activity.startParentalGateTarget(request: ParentalGateRequest) {
    val candidates = listOfNotNull(request.target, request.fallback)
    for (intent in candidates) {
        try {
            startActivity(intent)
            return
        } catch (exception: ActivityNotFoundException) {
            Timber.w(exception, "No activity found for the parental gate target")
        } catch (@Suppress("TooGenericExceptionCaught") exception: RuntimeException) {
            // FileUriExposedException, SecurityException (a non-exported target)...: never crash on the way out.
            Timber.w(exception, "Could not start the parental gate target")
        }
    }
    if (candidates.isNotEmpty()) {
        request.noActivityFoundMessage?.let { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
    }
}
