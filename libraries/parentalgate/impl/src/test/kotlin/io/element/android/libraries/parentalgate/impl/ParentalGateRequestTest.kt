/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.parentalgate.impl

import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import androidx.core.net.toUri
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.parentalgate.api.ParentalGate
import io.element.android.libraries.parentalgate.api.ParentalGateResultContract
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast

private const val A_URL = "https://example.org/page"
private const val A_MESSAGE = "No app found"

class ParentalGateRequestTest : RobolectricTest() {
    @Test
    fun `the api module points at this activity`() {
        assertThat(ParentalGate.ACTIVITY_CLASS_NAME).isEqualTo(ParentalGateActivity::class.java.name)
        val intent = ParentalGate.createIntent(ApplicationProvider.getApplicationContext())
        assertThat(intent.component?.className).isEqualTo(ParentalGateActivity::class.java.name)
    }

    @Test
    fun `a request survives the round trip through the gate intent`() {
        val target = Intent(Intent.ACTION_VIEW, A_URL.toUri())
        val fallback = Intent(Intent.ACTION_VIEW, "https://example.org/fallback".toUri())
        val gateIntent = ParentalGate.createIntent(ApplicationProvider.getApplicationContext(), target, fallback, A_MESSAGE)
        val request = ParentalGateRequest.from(gateIntent)
        assertThat(request.target?.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(request.target?.data).isEqualTo(A_URL.toUri())
        assertThat(request.fallback?.data).isEqualTo("https://example.org/fallback".toUri())
        assertThat(request.noActivityFoundMessage).isEqualTo(A_MESSAGE)
    }

    @Test
    fun `the result contract passes only on RESULT_OK`() {
        val contract = ParentalGateResultContract()
        assertThat(contract.parseResult(Activity.RESULT_OK, null)).isTrue()
        assertThat(contract.parseResult(Activity.RESULT_CANCELED, null)).isFalse()
        val intent = contract.createIntent(ApplicationProvider.getApplicationContext(), Unit)
        assertThat(intent.component?.className).isEqualTo(ParentalGateActivity::class.java.name)
        assertThat(ParentalGateRequest.from(intent).target).isNull()
    }

    @Test
    fun `once passed, the target is started`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val target = Intent(Intent.ACTION_VIEW, A_URL.toUri())
        activity.startParentalGateTarget(ParentalGateRequest(target = target, fallback = null, noActivityFoundMessage = A_MESSAGE))
        val started = shadowOf(activity).nextStartedActivity
        assertThat(started.action).isEqualTo(Intent.ACTION_VIEW)
        assertThat(started.data).isEqualTo(A_URL.toUri())
        assertThat(shadowOf(activity).nextStartedActivity).isNull()
    }

    @Test
    fun `the fallback is started when nothing handles the target`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        shadowOf(activity.application).checkActivities(true)
        val browser = ComponentName("org.example.browser", "org.example.browser.Main")
        shadowOf(activity.packageManager).addActivityIfNotPresent(browser)
        shadowOf(activity.packageManager).addIntentFilterForActivity(
            browser,
            IntentFilter(Intent.ACTION_VIEW).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                addDataScheme("https")
            },
        )
        activity.startParentalGateTarget(
            ParentalGateRequest(
                target = Intent(Intent.ACTION_VIEW, "market://details?id=org.example".toUri()),
                fallback = Intent(Intent.ACTION_VIEW, A_URL.toUri()),
                noActivityFoundMessage = A_MESSAGE,
            )
        )
        assertThat(shadowOf(activity).nextStartedActivity.data).isEqualTo(A_URL.toUri())
        assertThat(ShadowToast.getLatestToast()).isNull()
    }

    @Test
    fun `a toast explains when no app can open the target`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        shadowOf(activity.application).checkActivities(true)
        activity.startParentalGateTarget(
            ParentalGateRequest(
                target = Intent(Intent.ACTION_VIEW, "market://details?id=org.example".toUri()),
                fallback = null,
                noActivityFoundMessage = A_MESSAGE,
            )
        )
        assertThat(shadowOf(activity).nextStartedActivity).isNull()
        assertThat(ShadowToast.getTextOfLatestToast()).isEqualTo(A_MESSAGE)
    }

    @Test
    fun `a request without a target starts nothing`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        activity.startParentalGateTarget(ParentalGateRequest(target = null, fallback = null, noActivityFoundMessage = A_MESSAGE))
        assertThat(shadowOf(activity).nextStartedActivity).isNull()
        assertThat(ShadowToast.getLatestToast()).isNull()
    }
}
