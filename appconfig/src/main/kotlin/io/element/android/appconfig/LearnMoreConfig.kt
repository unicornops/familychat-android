/*
 * Copyright (c) 2025 Element Creations Ltd.
 * Copyright 2023-2025 New Vector Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.appconfig

object LearnMoreConfig {
    // TODO(unicornops/family-chat#234): point these at dedicated encryption help pages once the
    //  website has them; for now every "learn more" link opens the help section.
    private const val HELP_URL: String = "https://safechat.family/docs/"

    const val ENCRYPTION_URL: String = HELP_URL
    const val DEVICE_VERIFICATION_URL: String = HELP_URL
    const val SECURE_BACKUP_URL: String = HELP_URL
    const val IDENTITY_CHANGE_URL: String = HELP_URL
    const val HISTORY_VISIBLE_URL: String = HELP_URL
}
