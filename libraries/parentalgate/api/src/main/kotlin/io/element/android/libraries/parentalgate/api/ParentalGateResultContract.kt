/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.parentalgate.api

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.contract.ActivityResultContract

/**
 * Ask for the parental gate and learn whether it was passed, for an action that is not an intent: a purchase, or a
 * setting only a parent should change. The app sells nothing today; this is the entry point anything purchasable must
 * use.
 *
 * ```
 * val gate = rememberLauncherForActivityResult(ParentalGateResultContract()) { passed -> if (passed) buy() }
 * gate.launch(Unit)
 * ```
 *
 * For a link or any other intent, use [startActivityBehindParentalGate] instead: it needs no result handling.
 */
class ParentalGateResultContract : ActivityResultContract<Unit, Boolean>() {
    override fun createIntent(context: Context, input: Unit): Intent = ParentalGate.createIntent(context)

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean = resultCode == Activity.RESULT_OK
}
