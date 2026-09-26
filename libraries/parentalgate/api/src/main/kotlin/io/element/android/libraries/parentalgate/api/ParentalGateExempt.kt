/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.parentalgate.api

/**
 * Marks an API that leaves the app WITHOUT the parental gate. Opting in is a decision that has to be justified next to
 * the `@OptIn`, and is reserved for authentication on the family's own account provider (the OAuth sign-in Custom Tab
 * and the flows that must complete on it, such as approving an identity reset or a new device), where the app waits
 * for the browser to hand back and a gate would lock children out of their own account.
 *
 * Everything else that leaves the app goes through [startActivityBehindParentalGate].
 */
@RequiresOptIn(
    level = RequiresOptIn.Level.ERROR,
    message = "This opens a page outside the app without the parental gate. Only authentication flows may do this, " +
        "see libraries/parentalgate/README.md.",
)
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
annotation class ParentalGateExempt
