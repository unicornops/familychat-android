/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.parentalgate.impl

sealed interface ParentalGateEvent {
    data class UpdateAnswer(val answer: String) : ParentalGateEvent
    data object Submit : ParentalGateEvent
}
