/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalFoundationApi::class)

package io.element.android.libraries.designsystem.theme

import android.content.Context
import androidx.compose.foundation.ComposeFoundationFlags
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuComponent
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuKeys
import androidx.compose.foundation.text.contextmenu.data.TextContextMenuSeparator
import androidx.compose.foundation.text.contextmenu.modifier.filterTextContextMenuComponents
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import io.element.android.libraries.androidutils.system.openUrlInExternalApp
import timber.log.Timber

/**
 * Family Chat (parental gate, unicornops/family-chat#232 decision 10). Wraps every activity's content, through
 * [ElementThemeApp], so that
 * - links opened by Compose text ([LocalUriHandler]) go through the parental gate ([SafeUriHandler]);
 * - the text selection menu of every text in this window keeps only cut, copy, paste, select all and autofill, and
 *   drops the smart actions (open link, call, map) and the Process-Text items ("Search", "Translate") that hand the text
 *   to another app.
 *
 * Dialogs and bottom sheets are separate windows the modifier cannot reach; [disableTextActionsThatLeaveTheApp],
 * called once from the Application, removes those items there too.
 */
@Composable
fun ParentalGateSafeContent(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = remember(context) { SafeUriHandler(context) }
    CompositionLocalProvider(LocalUriHandler provides uriHandler) {
        Box(
            modifier = modifier.filterTextContextMenuComponents(::isTextContextMenuComponentAllowed),
            propagateMinConstraints = true,
        ) {
            content()
        }
    }
}

/** Opens a link from Compose text with openUrlInExternalApp(), so through the parental gate. */
class SafeUriHandler(private val context: Context) : UriHandler {
    override fun openUri(uri: String) {
        context.openUrlInExternalApp(uri)
    }
}

private val ALLOWED_TEXT_CONTEXT_MENU_KEYS = setOf(
    TextContextMenuKeys.CutKey,
    TextContextMenuKeys.CopyKey,
    TextContextMenuKeys.PasteKey,
    TextContextMenuKeys.SelectAllKey,
    TextContextMenuKeys.AutofillKey,
)

/** Only the editing actions stay; anything else (smart actions, Process-Text, unknown items) is dropped. */
fun isTextContextMenuComponentAllowed(component: TextContextMenuComponent): Boolean {
    return component is TextContextMenuSeparator || component.key in ALLOWED_TEXT_CONTEXT_MENU_KEYS
}

/**
 * Process-wide switches, for the windows [ParentalGateSafeContent] cannot reach (dialogs, bottom sheets): no text
 * classification (smart actions) and no Process-Text activities in Compose text selection menus. Call once, early, from
 * the Application.
 */
fun disableTextActionsThatLeaveTheApp() {
    ComposeFoundationFlags.isSmartSelectionEnabled = false
    disableComposeProcessTextItems()
}

private const val PROCESS_TEXT_IMPL_CLASS = "androidx.compose.foundation.text.contextmenu.ProcessTextApi23Impl"

/**
 * Compose lists Process-Text activities through an internal, replaceable query (its test hook). There is no public
 * switch, so it is replaced by reflection; consumer-rules.pro keeps the class. If Compose changes, this logs and
 * leaves the items to the root filter of [ParentalGateSafeContent] (which covers every screen but dialogs).
 */
@Suppress("TooGenericExceptionCaught")
private fun disableComposeProcessTextItems() {
    try {
        val implClass = Class.forName(PROCESS_TEXT_IMPL_CLASS)
        val instance = implClass.getField("INSTANCE").get(null)
        val noActivities: (Context) -> List<Any> = { emptyList() }
        implClass.getMethod("setProcessTextActivitiesQuery", Function1::class.java).invoke(instance, noActivities)
    } catch (exception: Exception) {
        Timber.w(exception, "Could not turn off the Process-Text items of Compose text menus")
    }
}
