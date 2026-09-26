/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.androidutils.browser

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.os.PatternMatcher
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.androidutils.system.openGooglePlay
import io.element.android.libraries.androidutils.system.openUrlInExternalApp
import io.element.android.libraries.parentalgate.api.ParentalGate
import io.element.android.libraries.parentalgate.api.ParentalGateExempt
import io.element.android.libraries.parentalgate.api.startActivityBehindParentalGate
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Before
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

private const val AN_EXTERNAL_URL = "https://example.org/some/page"
private const val AN_APP_LINK = "https://safechat.family/app/login?account_provider=smith.safechat.family"
private const val A_MATRIX_LINK = "matrix:u/alice:smith.safechat.family"
private const val CUSTOM_TABS_EXTRA_SESSION = "android.support.customtabs.extra.SESSION"

/**
 * The shared link openers are the app's choke point for links that leave it: they must hand every external link to the
 * parental gate and never start it themselves, while links the app declares for itself open in-app directly.
 */
class ParentalGateLinksTest : RobolectricTest() {
    private val application: Application get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        // Stand-in for the app's MainActivity and the intent filters :app declares.
        val mainActivity = ComponentName(application.packageName, "io.element.android.x.MainActivity")
        val packageManager = shadowOf(application.packageManager)
        packageManager.addActivityIfNotPresent(mainActivity)
        packageManager.addIntentFilterForActivity(
            mainActivity,
            IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addCategory(Intent.CATEGORY_BROWSABLE)
                addDataScheme("https")
                addDataAuthority("safechat.family", null)
                addDataPath("/app/", PatternMatcher.PATTERN_PREFIX)
            },
        )
        packageManager.addIntentFilterForActivity(
            mainActivity,
            IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addCategory(Intent.CATEGORY_BROWSABLE)
                addDataScheme("matrix")
            },
        )
    }

    @Test
    fun `an external link only starts the gate`() {
        val activity = anActivity()
        activity.openUrlInExternalApp(AN_EXTERNAL_URL)
        val gateIntent = shadowOf(activity).nextStartedActivity
        assertIsGate(gateIntent)
        val target = gateIntent.target()
        assertThat(target.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(target.data).isEqualTo(AN_EXTERNAL_URL.toUri())
        assertThat(gateIntent.getStringExtra(ParentalGate.EXTRA_NO_ACTIVITY_FOUND_MESSAGE)).isNotEmpty()
        // Nothing else was started: the browser is only opened by the gate, once passed.
        assertThat(shadowOf(activity).nextStartedActivity).isNull()
    }

    @Test
    fun `mail, phone and other schemes are gated too`() {
        listOf("mailto:someone@example.org", "tel:+353123456", "geo:0,0?q=Dublin", "http://example.org", "sms:123").forEach { url ->
            val activity = anActivity()
            activity.openUrlInExternalApp(url)
            val gateIntent = shadowOf(activity).nextStartedActivity
            assertIsGate(gateIntent)
            assertThat(gateIntent.target().data).isEqualTo(url.toUri())
        }
    }

    @Test
    fun `a link on safechat dot family outside the app paths is gated`() {
        val activity = anActivity()
        activity.openUrlInExternalApp("https://safechat.family/privacy")
        assertIsGate(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `links the app handles itself open in-app without the gate`() {
        listOf(AN_APP_LINK, A_MATRIX_LINK).forEach { url ->
            val activity = anActivity()
            activity.openUrlInExternalApp(url)
            val started = shadowOf(activity).nextStartedActivity
            assertThat(started.component?.className).isNotEqualTo(ParentalGate.ACTIVITY_CLASS_NAME)
            assertThat(started.action).isEqualTo(Intent.ACTION_VIEW)
            assertThat(started.data).isEqualTo(url.toUri())
            // Pinned to this app, so it cannot fall through to the browser
            assertThat(started.`package`).isEqualTo(activity.packageName)
        }
    }

    @Test
    fun `an external link from a non-activity context starts the gate in a new task`() {
        application.openUrlInExternalApp(AN_EXTERNAL_URL)
        val gateIntent = shadowOf(application).nextStartedActivity
        assertIsGate(gateIntent)
        assertThat(gateIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
        assertThat(shadowOf(application).nextStartedActivity).isNull()
    }

    @Test
    fun `an in-app link from a non-activity context starts in a new task`() {
        application.openUrlInExternalApp(AN_APP_LINK)
        val started = shadowOf(application).nextStartedActivity
        assertThat(started.`package`).isEqualTo(application.packageName)
        assertThat(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isNotEqualTo(0)
    }

    @Test
    fun `a Custom Tab link only starts the gate, carrying the Custom Tab and a browser fallback`() {
        val activity = anActivity()
        activity.openUrlInChromeCustomTab(null, darkTheme = false, url = AN_EXTERNAL_URL)
        val gateIntent = shadowOf(activity).nextStartedActivity
        assertIsGate(gateIntent)
        val target = gateIntent.target()
        assertThat(target.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(target.data).isEqualTo(AN_EXTERNAL_URL.toUri())
        assertThat(target.hasExtra(CUSTOM_TABS_EXTRA_SESSION)).isTrue()
        val fallback = IntentCompat.getParcelableExtra(gateIntent, ParentalGate.EXTRA_FALLBACK_INTENT, Intent::class.java)
        assertThat(fallback?.data).isEqualTo(AN_EXTERNAL_URL.toUri())
        assertThat(fallback?.hasExtra(CUSTOM_TABS_EXTRA_SESSION)).isFalse()
        assertThat(shadowOf(activity).nextStartedActivity).isNull()
    }

    @Test
    fun `a Custom Tab link the app handles itself opens in-app`() {
        val activity = anActivity()
        activity.openUrlInChromeCustomTab(null, darkTheme = true, url = AN_APP_LINK)
        val started = shadowOf(activity).nextStartedActivity
        assertThat(started.component?.className).isNotEqualTo(ParentalGate.ACTIVITY_CLASS_NAME)
        assertThat(started.`package`).isEqualTo(activity.packageName)
        assertThat(started.hasExtra(CUSTOM_TABS_EXTRA_SESSION)).isFalse()
    }

    @OptIn(ParentalGateExempt::class)
    @Test
    fun `the sign-in Custom Tab is exempt and opens directly`() {
        val activity = anActivity()
        activity.openAuthenticationUrlInChromeCustomTab(null, darkTheme = false, url = "https://account.smith.safechat.family/authorize")
        val started = shadowOf(activity).nextStartedActivity
        assertThat(started.component?.className).isNotEqualTo(ParentalGate.ACTIVITY_CLASS_NAME)
        assertThat(started.data).isEqualTo("https://account.smith.safechat.family/authorize".toUri())
        assertThat(started.hasExtra(CUSTOM_TABS_EXTRA_SESSION)).isTrue()
    }

    @Test
    fun `the store listing is gated, with the web page as fallback`() {
        val activity = anActivity()
        activity.openGooglePlay("org.example.app")
        val gateIntent = shadowOf(activity).nextStartedActivity
        assertIsGate(gateIntent)
        assertThat(gateIntent.target().data).isEqualTo("market://details?id=org.example.app".toUri())
        val fallback = IntentCompat.getParcelableExtra(gateIntent, ParentalGate.EXTRA_FALLBACK_INTENT, Intent::class.java)
        assertThat(fallback?.data).isEqualTo("https://play.google.com/store/apps/details?id=org.example.app".toUri())
        assertThat(shadowOf(activity).nextStartedActivity).isNull()
    }

    @Test
    fun `any intent can be put behind the gate`() {
        val activity = anActivity()
        val launchOtherApp = Intent(Intent.ACTION_MAIN).setPackage("org.example.other")
        activity.startActivityBehindParentalGate(launchOtherApp)
        val gateIntent = shadowOf(activity).nextStartedActivity
        assertIsGate(gateIntent)
        assertThat(gateIntent.target().`package`).isEqualTo("org.example.other")
        assertThat(gateIntent.flags and Intent.FLAG_ACTIVITY_NEW_TASK).isEqualTo(0)
    }

    @Test
    fun `without the gate in the build, nothing opens`() {
        val activity = anActivity()
        shadowOf(activity.application).checkActivities(true)
        activity.openUrlInExternalApp(AN_EXTERNAL_URL)
        assertThat(shadowOf(activity).nextStartedActivity).isNull()
    }

    private fun anActivity(): Activity = Robolectric.buildActivity(Activity::class.java).setup().get()

    private fun assertIsGate(intent: Intent?) {
        assertThat(intent).isNotNull()
        assertThat(intent!!.component).isEqualTo(ComponentName(application.packageName, ParentalGate.ACTIVITY_CLASS_NAME))
        assertThat(intent.action).isNull()
        assertThat(intent.data).isNull()
    }

    private fun Intent.target(): Intent = requireNotNull(IntentCompat.getParcelableExtra(this, ParentalGate.EXTRA_TARGET_INTENT, Intent::class.java))
}
