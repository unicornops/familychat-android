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
 * own VIEW intent, Custom Tab, chooser or link span instead of using them.
 *
 * It is a tripwire, not proof of coverage: it only reads this repository's sources, so code inside libraries (MapLibre,
 * the rich text editor, WebView, Compose) is invisible to it, and a file on the allow-list can gain a new exit unseen.
 */
class KonsistParentalGateTest {
    /**
     * Matches the qualified, imported and aliased forms (`Intent.ACTION_VIEW`, `import android.content.Intent.ACTION_VIEW
     * as VIEW`, `Intent.\n    ACTION_VIEW`), the raw action string, Custom Tabs, and launching another app by package.
     * Comments count too: allow-listing a file is cheaper than a missed link.
     */
    private val outboundIntentPattern = Regex(
        listOf(
            """\bACTION_VIEW\b""",
            """android\.intent\.action\.VIEW\b""",
            """\bACTION_(SENDTO|DIAL|WEB_SEARCH)\b""",
            """android\.intent\.action\.(SENDTO|DIAL|WEB_SEARCH)\b""",
            """\bCustomTabsIntent\b""",
            """\.launchUrl\b""",
            """\bgetLaunchIntentForPackage\b""",
            """\bAndroidUriHandler\b""",
            """\bACTION_SEND(_MULTIPLE)?\b""",
            """\bcreateChooser\b""",
            """\bparseUri\b""",
            """\bCATEGORY_APP_BROWSER\b""",
            """\bmakeMainSelectorActivity\b""",
            """\bURLSpan\b""",
            """\bLinkMovementMethod\b""",
            """\bLinkify\b""",
        ).joinToString("|")
    )

    /** Files allowed to use those APIs, and why. A new entry needs the same scrutiny as a new link out. */
    private val allowedFiles = mapOf(
        // The shared openers themselves (they hand the intent to the gate), the share sheet helper, the text actions
        // filter, and LinkifyHelper, which only creates spans whose clicks go to the callers' gated openers.
        "libraries/androidutils" to setOf(
            "SystemUtils.kt",
            "ChromeCustomTab.kt",
            "InAppLinks.kt",
            "TextActionsThatLeaveTheApp.kt",
            "LinkifyHelper.kt",
        ),
        // Linkifies text; clicks go to LocalUriHandler, which is SafeUriHandler in every activity.
        "libraries/designsystem" to setOf("ClickableLinkText.kt"),
        // Reads the action of an incoming intent (VIEW, SEND).
        "libraries/deeplink/impl" to setOf("DefaultDeeplinkParser.kt"),
        "appnav" to setOf("IntentResolver.kt"),
        "features/share/impl" to setOf("DefaultShareIntentHandler.kt"),
        // Builds an intent for the app's own MainActivity.
        "app" to setOf("DefaultIntentProvider.kt"),
        // Share sheet (not gated, user-initiated sharing) and "Open with" (through startActivityBehindParentalGate).
        "libraries/mediaviewer/impl" to setOf("AndroidLocalMediaActions.kt"),
        "features/viewfolder/impl" to setOf("FileShare.kt"),
        // URLSpans for the timeline; their clicks go to MessagesNode.onLinkClick and the gated openers.
        "features/messages/impl" to setOf("TextPillificationHelper.kt", "TimelineItemContentMessageFactory.kt"),
        // Opens a maps app, through startActivityBehindParentalGate.
        "features/location/impl" to setOf("AndroidLocationActions.kt"),
        // Launches Element Classic, through startActivityBehindParentalGate.
        "features/login/impl" to setOf("MissingKeyBackupNode.kt"),
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

    @Test
    fun `every activity content is wrapped in ElementThemeApp, which provides the gated link handler and text menu filter`() {
        Konsist
            .scopeFromProduction()
            .files
            .filter { it.text.contains("setContent {") && !it.moduleName.startsWith("tests/") }
            .also { assertThat(it).isNotEmpty() }
            .assertTrue(
                additionalMessage = "Wrap the activity content in ElementThemeApp (it adds ParentalGateSafeContent), so links from " +
                    "Compose text and text selection menus stay behind the parental gate.",
            ) { file ->
                file.text.contains("ElementThemeApp(")
            }
    }
}
