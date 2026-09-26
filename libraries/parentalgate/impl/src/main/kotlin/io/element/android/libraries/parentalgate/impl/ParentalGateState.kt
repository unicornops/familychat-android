/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.parentalgate.impl

import io.element.android.libraries.parentalgate.impl.challenge.ParentalGateChallenge

data class ParentalGateState(
    /** The question on screen. Replaced by a new one after every wrong answer, never asked twice in a row. */
    val challenge: ParentalGateChallenge,
    val answer: String,
    /** True once a wrong answer replaced the question, so the screen says why it changed. */
    val showWrongAnswer: Boolean,
    /** True once the right answer was given: the gate opens and closes. */
    val isPassed: Boolean,
    val eventSink: (ParentalGateEvent) -> Unit,
) {
    val canSubmit: Boolean = answer.isNotBlank() && !isPassed
}
