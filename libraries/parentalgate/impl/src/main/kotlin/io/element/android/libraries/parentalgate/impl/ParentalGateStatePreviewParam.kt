/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.parentalgate.impl

import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import io.element.android.libraries.parentalgate.impl.challenge.ParentalGateChallenge

open class ParentalGateStatePreviewParam : PreviewParameterProvider<ParentalGateState> {
    override val values: Sequence<ParentalGateState>
        get() = sequenceOf(
            aParentalGateState(),
            aParentalGateState(answer = "7215"),
            aParentalGateState(challenge = ParentalGateChallenge.Multiply(left = 14, right = 7)),
            aParentalGateState(challenge = ParentalGateChallenge.Multiply(left = 6, right = 19), showWrongAnswer = true),
        )
}

internal fun aParentalGateState(
    challenge: ParentalGateChallenge = ParentalGateChallenge.TypeNumber(7215),
    answer: String = "",
    showWrongAnswer: Boolean = false,
    isPassed: Boolean = false,
    eventSink: (ParentalGateEvent) -> Unit = {},
) = ParentalGateState(
    challenge = challenge,
    answer = answer,
    showWrongAnswer = showWrongAnswer,
    isPassed = isPassed,
    eventSink = eventSink,
)
