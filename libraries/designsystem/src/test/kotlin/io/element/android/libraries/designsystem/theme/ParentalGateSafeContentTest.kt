/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalFoundationApi::class)

package io.element.android.libraries.designsystem.theme

import android.app.Activity
import android.content.ComponentName
import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuItem
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSeparator
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.parentalgate.api.ParentalGate
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf

class ParentalGateSafeContentTest : RobolectricTest() {
    @Test
    fun `only the editing items of the text menu are kept`() {
        listOf(
            TextContextMenuKeys.CutKey,
            TextContextMenuKeys.CopyKey,
            TextContextMenuKeys.PasteKey,
            TextContextMenuKeys.SelectAllKey,
            TextContextMenuKeys.AutofillKey,
        ).forEach { key ->
            assertThat(isTextContextMenuComponentAllowed(anItem(key))).isTrue()
        }
        assertThat(isTextContextMenuComponentAllowed(TextContextMenuSeparator)).isTrue()
        // Anything else: Process-Text entries, smart actions, items added by other code
        assertThat(isTextContextMenuComponentAllowed(anItem("Translate"))).isFalse()
        assertThat(isTextContextMenuComponentAllowed(anItem(Any()))).isFalse()
    }

    private fun anItem(key: Any) = TextContextMenuItem(key = key, label = key.toString(), onClick = {})

    @Test
    fun `links opened from Compose text go through the parental gate`() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        SafeUriHandler(activity).openUri("https://example.org")
        val started = shadowOf(activity).nextStartedActivity
        assertThat(started.component).isEqualTo(ComponentName(activity.packageName, ParentalGate.ACTIVITY_CLASS_NAME))
    }

    @Test
    fun `smart selection is turned off for the whole process`() {
        disableTextActionsThatLeaveTheApp()
        assertThat(ComposeFoundationFlags.isSmartSelectionEnabled).isFalse()
    }
}
