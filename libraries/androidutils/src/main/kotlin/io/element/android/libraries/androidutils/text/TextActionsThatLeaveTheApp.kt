/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.androidutils.text

import android.content.Intent
import android.os.Build
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.textclassifier.TextClassifier
import android.widget.EditText
import android.widget.TextView

/**
 * Family Chat (parental gate, unicornops/family-chat#232 decision 10): remove the text selection actions that hand
 * the text to another app without the gate. That is the "smart" actions of the text classifier (open a link, call a
 * number, send an email, show a map) and the Process-Text / web search entries other apps add ("Search", "Translate").
 * Cut, copy, paste and select all stay.
 *
 * Use it on every View-based editable or selectable text field. Compose text fields do not show these actions.
 */
fun TextView.hideTextActionsThatLeaveTheApp() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        setTextClassifier(TextClassifier.NO_OP)
    }
    customSelectionActionModeCallback = HideTextActionsThatLeaveTheAppCallback
    customInsertionActionModeCallback = HideTextActionsThatLeaveTheAppCallback
}

/**
 * Apply [hideTextActionsThatLeaveTheApp] to every [EditText] under this view. For editors created by a library that
 * gives no access to its view, such as the rich text composer.
 */
fun View.hideTextActionsThatLeaveTheAppInEditTexts() {
    if (this is EditText) {
        hideTextActionsThatLeaveTheApp()
    }
    if (this is ViewGroup) {
        for (index in 0 until childCount) {
            getChildAt(index).hideTextActionsThatLeaveTheAppInEditTexts()
        }
    }
}

/**
 * Hide every item of [this] menu that would leave the app. See [hideTextActionsThatLeaveTheApp].
 */
fun Menu.hideItemsThatLeaveTheApp() {
    for (index in 0 until size()) {
        val item = getItem(index)
        if (item.leavesTheApp()) {
            item.isVisible = false
        }
    }
}

private val ACTIONS_THAT_LEAVE_THE_APP = setOf(
    Intent.ACTION_PROCESS_TEXT,
    Intent.ACTION_WEB_SEARCH,
    Intent.ACTION_VIEW,
    Intent.ACTION_SEND,
    Intent.ACTION_SENDTO,
    Intent.ACTION_DIAL,
)

private fun MenuItem.leavesTheApp(): Boolean {
    return itemId == android.R.id.textAssist ||
        groupId == android.R.id.textAssist ||
        intent?.action in ACTIONS_THAT_LEAVE_THE_APP
}

/**
 * TextView runs its own callback first and ours last, in both create and prepare, so hiding in prepare sees every item
 * TextView and the other apps added.
 */
private object HideTextActionsThatLeaveTheAppCallback : ActionMode.Callback {
    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
        menu.hideItemsThatLeaveTheApp()
        return true
    }

    override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
        menu.hideItemsThatLeaveTheApp()
        return true
    }

    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = false

    override fun onDestroyActionMode(mode: ActionMode) = Unit
}
