/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.androidutils.text

import android.content.Intent
import android.view.Menu
import android.view.textclassifier.TextClassifier
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test
import org.robolectric.fakes.RoboMenu

class TextActionsThatLeaveTheAppTest : RobolectricTest() {
    @Test
    fun `only the actions that leave the app are hidden`() {
        val menu = RoboMenu(ApplicationProvider.getApplicationContext())
        val copy = menu.add(Menu.NONE, android.R.id.copy, 0, "Copy")
        val paste = menu.add(Menu.NONE, android.R.id.paste, 1, "Paste")
        val assist = menu.add(Menu.NONE, android.R.id.textAssist, 2, "Open link")
        val secondaryAssist = menu.add(android.R.id.textAssist, Menu.NONE, 3, "Call")
        val translate = menu.add(Menu.NONE, Menu.NONE, 4, "Translate").setIntent(Intent(Intent.ACTION_PROCESS_TEXT))
        val webSearch = menu.add(Menu.NONE, Menu.NONE, 5, "Web search").setIntent(Intent(Intent.ACTION_WEB_SEARCH))
        val view = menu.add(Menu.NONE, Menu.NONE, 6, "Open").setIntent(Intent(Intent.ACTION_VIEW))

        menu.hideItemsThatLeaveTheApp()

        assertThat(copy.isVisible).isTrue()
        assertThat(paste.isVisible).isTrue()
        listOf(assist, secondaryAssist, translate, webSearch, view).forEach {
            assertThat(it.isVisible).isFalse()
        }
    }

    @Test
    fun `a text field loses the text classifier and gets the filtering callbacks`() {
        val editText = EditText(ApplicationProvider.getApplicationContext())
        editText.hideTextActionsThatLeaveTheApp()
        assertThat(editText.textClassifier).isSameInstanceAs(TextClassifier.NO_OP)
        assertThat(editText.customSelectionActionModeCallback).isNotNull()
        assertThat(editText.customInsertionActionModeCallback).isNotNull()
    }
}
