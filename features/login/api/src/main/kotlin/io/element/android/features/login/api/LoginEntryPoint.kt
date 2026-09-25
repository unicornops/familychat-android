/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.api

import com.bumble.appyx.core.modality.BuildContext
import com.bumble.appyx.core.node.Node
import com.bumble.appyx.core.plugin.Plugin
import io.element.android.libraries.architecture.FeatureEntryPoint

interface LoginEntryPoint : FeatureEntryPoint {
    data class Params(
        val accountProvider: String?,
        val loginHint: String?,
        /** Host answering the client-server API for the family's homeserver; only with [signInCodeId]. */
        val hs: String? = null,
        /** Names a control panel sign-in code held in memory by the login feature (see [LoginParams]); only with [hs]. */
        val signInCodeId: String? = null,
    )

    interface Callback : Plugin {
        fun navigateToBugReport()
        fun onDone()
    }

    fun createNode(
        parentNode: Node,
        buildContext: BuildContext,
        params: Params,
        callback: Callback,
    ): Node
}
