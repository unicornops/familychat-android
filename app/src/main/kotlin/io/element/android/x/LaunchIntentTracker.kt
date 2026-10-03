/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.x

import android.os.Bundle

/**
 * Family Chat (#8, N8): makes [MainActivity] handle its launch intent once per activity, not once per activity instance.
 *
 * When the system recreates the activity (process death, or a configuration change it does not handle), `getIntent()`
 * is the original launch intent again, without `FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`. Handling it again would replay
 * a sign-in code link: an "already signed in" dialog on every recreation, or a dead code after signing out.
 */
class LaunchIntentTracker(savedInstanceState: Bundle?) {
    private var handled = savedInstanceState?.getBoolean(KEY_HANDLED) ?: false

    /** Returns true the first time it is called for a launch intent, false once that intent was handled. */
    fun shouldHandleLaunchIntent(): Boolean {
        val shouldHandle = !handled
        handled = true
        return shouldHandle
    }

    /** A new intent replaced the launch intent before it could be handled: handle that one. */
    fun onLaunchIntentReplaced() {
        handled = false
    }

    fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean(KEY_HANDLED, handled)
    }

    private companion object {
        const val KEY_HANDLED = "family_chat_launch_intent_handled"
    }
}
