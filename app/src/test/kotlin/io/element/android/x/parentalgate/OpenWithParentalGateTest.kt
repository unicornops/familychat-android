/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.x.parentalgate

import android.content.ContentResolver
import android.content.Intent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.IntentCompat
import androidx.core.net.toUri
import app.cash.molecule.RecompositionMode
import app.cash.molecule.moleculeFlow
import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.matrix.test.core.aBuildMeta
import io.element.android.libraries.mediaviewer.api.aPdfMediaInfo
import io.element.android.libraries.mediaviewer.impl.local.AndroidLocalMediaActions
import io.element.android.libraries.mediaviewer.test.viewer.aLocalMedia
import io.element.android.libraries.parentalgate.api.ParentalGate
import io.element.android.tests.testutils.robolectric.RobolectricTest
import io.element.android.tests.testutils.testCoroutineDispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import java.io.File

/**
 * Family Chat (#8): "Open with" on a received file hands it to another app (an HTML page opens in the browser), so it
 * must go through the parental gate. Runs in the app module so that the real FileProvider and the gate activity from
 * the merged manifest are registered.
 */
class OpenWithParentalGateTest : RobolectricTest() {
    @Test
    fun `open with starts the parental gate, which carries the view intent for the file`() = runTest {
        val application = RuntimeEnvironment.getApplication()
        val sut = AndroidLocalMediaActions(
            context = application,
            coroutineDispatchers = testCoroutineDispatchers(),
            buildMeta = aBuildMeta(applicationId = application.packageName),
        )
        val page = File(application.cacheDir, "a page.html").apply { writeText("<a href=\"https://example.com\">link</a>") }
        moleculeFlow(RecompositionMode.Immediate) {
            CompositionLocalProvider(LocalContext provides application) {
                sut.Configure()
            }
        }.test {
            awaitItem()

            val result = sut.open(aLocalMedia(page.toUri(), mediaInfo = aPdfMediaInfo(filename = page.name).copy(mimeType = "text/html")))

            assertThat(result.exceptionOrNull()).isNull()
            val started = shadowOf(application).nextStartedActivity
            assertThat(started.component?.className).isEqualTo(ParentalGate.ACTIVITY_CLASS_NAME)
            assertThat(started.component?.packageName).isEqualTo(application.packageName)
            // The gate activity is really in the merged manifest: without it the gate fails closed and nothing opens.
            assertThat(application.packageManager.resolveActivity(started, 0)).isNotNull()
            val target = IntentCompat.getParcelableExtra(started, ParentalGate.EXTRA_TARGET_INTENT, Intent::class.java)
            assertThat(target?.action).isEqualTo(Intent.ACTION_VIEW)
            assertThat(target?.type).isEqualTo("text/html")
            assertThat(target?.data?.scheme).isEqualTo(ContentResolver.SCHEME_CONTENT)
            assertThat(target?.data?.authority).isEqualTo("${application.packageName}.fileprovider")
            assertThat(target!!.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION).isNotEqualTo(0)
            // Nothing else was started: the file only leaves the app once the gate is passed.
            assertThat(shadowOf(application).nextStartedActivity).isNull()
            cancelAndIgnoreRemainingEvents()
        }
    }
}
