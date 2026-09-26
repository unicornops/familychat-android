/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.tests.konsist

import com.google.common.truth.Truth.assertThat
import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test

/**
 * Family Chat declares a child audience, so every link that leaves the app sits behind the parental gate
 * (unicornops/family-chat#232 decision 10, `libraries/parentalgate/README.md`). The gate is enforced in the shared
 * openers of `libraries/androidutils`; this test fails when code, typically new upstream code after a rebase, builds its
 * own VIEW intent or Custom Tab instead of using them.
 */
class KonsistParentalGateTest {
    private val outboundIntentPattern = Regex("""\bIntent\.ACTION_VIEW\b|\bCustomTabsIntent\b|\.launchUrl\(""")

    /** Files allowed to build VIEW intents or Custom Tabs, and why. */
    private val allowedFiles = mapOf(
        // The shared openers themselves: they hand the intent to the gate.
        "libraries/androidutils" to setOf("SystemUtils.kt", "ChromeCustomTab.kt", "InAppLinks.kt"),
        // Reads the action of an incoming intent.
        "libraries/deeplink/impl" to setOf("DefaultDeeplinkParser.kt"),
        "appnav" to setOf("IntentResolver.kt"),
        // Builds an intent for the app's own MainActivity.
        "app" to setOf("DefaultIntentProvider.kt"),
        // "Open with" on a received file: exporting the user's own file, like the share sheet, not a link out.
        "libraries/mediaviewer/impl" to setOf("AndroidLocalMediaActions.kt"),
        // Opens a maps app, through startActivityBehindParentalGate.
        "features/location/impl" to setOf("AndroidLocationActions.kt"),
    )

    @Test
    fun `links leaving the app go through the parental gate`() {
        Konsist
            .scopeFromProduction()
            .files
            .filter { outboundIntentPattern.containsMatchIn(it.text) }
            .also { assertThat(it).isNotEmpty() }
            .assertTrue(
                additionalMessage = "Open links with Context.openUrlInExternalApp() or Activity.openUrlInChromeCustomTab(), and " +
                    "other outbound intents with startActivityBehindParentalGate(), so they sit behind the parental gate. " +
                    "See libraries/parentalgate/README.md.",
            ) { file ->
                allowedFiles[file.moduleName]?.contains(file.nameWithExtension) == true
            }
    }
}
