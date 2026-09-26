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
            aParentalGateState(answer = "161"),
            aParentalGateState(challenge = ParentalGateChallenge(left = 6, right = 47), showWrongAnswer = true),
        )
}

internal fun aParentalGateState(
    challenge: ParentalGateChallenge = ParentalGateChallenge(left = 23, right = 7),
    answer: String = "",
    showWrongAnswer: Boolean = false,
    isPassed: Boolean = false,
    isDismissed: Boolean = false,
    eventSink: (ParentalGateEvent) -> Unit = {},
) = ParentalGateState(
    challenge = challenge,
    answer = answer,
    showWrongAnswer = showWrongAnswer,
    isPassed = isPassed,
    isDismissed = isDismissed,
    eventSink = eventSink,
)
