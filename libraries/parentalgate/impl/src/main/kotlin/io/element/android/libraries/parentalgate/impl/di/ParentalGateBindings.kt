/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.parentalgate.impl.di

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import io.element.android.libraries.parentalgate.impl.ParentalGateActivity

@ContributesTo(AppScope::class)
interface ParentalGateBindings {
    fun inject(activity: ParentalGateActivity)
}
