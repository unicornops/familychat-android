/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.util

import android.content.Context
import io.element.android.appconfig.AuthenticationConfig
import io.element.android.libraries.androidutils.system.openUrlInExternalApp

fun openLearnMorePage(context: Context) {
    // Through the shared opener, so the link sits behind the parental gate like every other link out.
    context.openUrlInExternalApp(AuthenticationConfig.SLIDING_SYNC_READ_MORE_URL)
}
