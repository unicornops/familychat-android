/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.designsystem.theme

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.ApplicationInfo
import android.content.pm.ResolveInfo
import com.google.common.truth.Truth.assertThat
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * Family Chat (#11): [disableTextActionsThatLeaveTheApp] empties Compose's Process-Text query through an internal test
 * hook, by reflection. A Compose upgrade can rename or remove it, and the production code only logs a warning then. This
 * test fails instead: check it on every Compose BOM bump.
 */
class ComposeProcessTextSwitchTest : RobolectricTest() {
    @Test
    fun `the Compose Process-Text hook still exists`() {
        val implClass = Class.forName(PROCESS_TEXT_IMPL)
        assertThat(implClass.getField("INSTANCE").get(null)).isNotNull()
        implClass.getMethod("setProcessTextActivitiesQuery", Function1::class.java)
        implClass.getMethod("queryProcessTextActivities", Context::class.java)
    }

    @Test
    fun `Compose lists no Process-Text app once text actions that leave the app are disabled`() {
        val context = RuntimeEnvironment.getApplication()
        // A "Translate" app that offers to process selected text.
        @Suppress("DEPRECATION")
        shadowOf(context.packageManager).addResolveInfoForIntent(
            Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain"),
            aProcessTextResolveInfo(),
        )
        val implClass = Class.forName(PROCESS_TEXT_IMPL)
        val instance = implClass.getField("INSTANCE").get(null)
        val getQuery = implClass.getMethod("getProcessTextActivitiesQuery")
        val setQuery = implClass.getMethod("setProcessTextActivitiesQuery", Function1::class.java)
        val query = implClass.getMethod("queryProcessTextActivities", Context::class.java)
        // The query is process-wide: another test of this sandbox may have disabled it already.
        val previous = getQuery.invoke(instance)
        setQuery.invoke(instance, defaultQuery(context))
        try {
            // Through Compose's own query, the app is listed...
            assertThat(query.invoke(instance, context) as List<*>).isNotEmpty()

            disableTextActionsThatLeaveTheApp()

            // ...and no longer once the switch has run.
            assertThat(query.invoke(instance, context) as List<*>).isEmpty()
        } finally {
            setQuery.invoke(instance, previous)
        }
    }

    /** What Compose's default query does: list the Process-Text activities for plain text. */
    private fun defaultQuery(context: Context): (Context) -> List<ResolveInfo> = {
        context.packageManager.queryIntentActivities(Intent(Intent.ACTION_PROCESS_TEXT).setType("text/plain"), 0)
    }

    private fun aProcessTextResolveInfo() = ResolveInfo().apply {
        activityInfo = ActivityInfo().apply {
            packageName = "com.example.translate"
            name = "com.example.translate.ProcessTextActivity"
            exported = true
            applicationInfo = ApplicationInfo().apply { packageName = "com.example.translate" }
        }
    }

    private companion object {
        const val PROCESS_TEXT_IMPL = "androidx.compose.foundation.text.contextmenu.ProcessTextApi23Impl"
    }
}
