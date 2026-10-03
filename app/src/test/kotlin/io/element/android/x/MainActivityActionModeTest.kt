/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.x

import android.content.Intent
import android.view.ActionMode
import android.view.Menu
import android.view.MenuInflater
import android.view.View
import android.widget.PopupMenu
import com.google.common.truth.Truth.assertThat
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment

/**
 * Family Chat (#8): [MainActivity.onActionModeStarted] is the last line of defence for text selection toolbars, whatever
 * view started them. It hides the actions that leave the app without the parental gate, and keeps the others.
 */
class MainActivityActionModeTest : RobolectricTest() {
    @Test
    fun `text selection toolbars keep copy and lose the actions that leave the app`() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        val menu = PopupMenu(RuntimeEnvironment.getApplication(), View(RuntimeEnvironment.getApplication())).menu
        val copy = menu.add(Menu.NONE, android.R.id.copy, 0, "Copy")
        val selectAll = menu.add(Menu.NONE, android.R.id.selectAll, 1, "Select all")
        val openLink = menu.add(android.R.id.textAssist, android.R.id.textAssist, 2, "Open link")
        val translate = menu.add(Menu.NONE, Menu.NONE, 3, "Translate").setIntent(Intent(Intent.ACTION_PROCESS_TEXT))
        val webSearch = menu.add(Menu.NONE, Menu.NONE, 4, "Search").setIntent(Intent(Intent.ACTION_WEB_SEARCH))
        val call = menu.add(Menu.NONE, Menu.NONE, 5, "Call").setIntent(Intent(Intent.ACTION_DIAL))

        activity.onActionModeStarted(FakeActionMode(menu))

        assertThat(copy.isVisible).isTrue()
        assertThat(selectAll.isVisible).isTrue()
        assertThat(openLink.isVisible).isFalse()
        assertThat(translate.isVisible).isFalse()
        assertThat(webSearch.isVisible).isFalse()
        assertThat(call.isVisible).isFalse()
    }
}

private class FakeActionMode(private val menu: Menu) : ActionMode() {
    override fun setTitle(title: CharSequence?) = Unit
    override fun setTitle(resId: Int) = Unit
    override fun setSubtitle(subtitle: CharSequence?) = Unit
    override fun setSubtitle(resId: Int) = Unit
    override fun setCustomView(view: View?) = Unit
    override fun invalidate() = Unit
    override fun finish() = Unit
    override fun getMenu(): Menu = menu
    override fun getTitle(): CharSequence? = null
    override fun getSubtitle(): CharSequence? = null
    override fun getCustomView(): View? = null
    override fun getMenuInflater(): MenuInflater = MenuInflater(RuntimeEnvironment.getApplication())
}
