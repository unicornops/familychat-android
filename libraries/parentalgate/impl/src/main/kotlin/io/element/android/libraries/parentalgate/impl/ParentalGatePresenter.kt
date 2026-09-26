/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.parentalgate.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import dev.zacsweers.metro.Inject
import io.element.android.libraries.architecture.Presenter
import io.element.android.libraries.parentalgate.impl.challenge.ParentalGateChallenge
import io.element.android.libraries.parentalgate.impl.challenge.ParentalGateChallengeGenerator
import io.element.android.libraries.parentalgate.impl.challenge.isAnsweredBy

/**
 * A new challenge each time the gate is shown (the state is not saved, so a recreated screen asks a new question
 * too), and a new challenge after every wrong answer: the same question can never be retried until it is right.
 * There is no timer.
 */
@Inject
class ParentalGatePresenter(
    private val challengeGenerator: ParentalGateChallengeGenerator,
) : Presenter<ParentalGateState> {
    @Composable
    override fun present(): ParentalGateState {
        var attempt by remember { mutableStateOf(Attempt(challenge = challengeGenerator.generate(previous = null))) }

        fun handleEvent(event: ParentalGateEvent) {
            val current = attempt
            if (current.isPassed) return
            attempt = when (event) {
                is ParentalGateEvent.UpdateAnswer -> current.copy(answer = event.answer)
                ParentalGateEvent.Submit -> when {
                    current.answer.isBlank() -> current
                    current.challenge.isAnsweredBy(current.answer) -> current.copy(isPassed = true)
                    else -> Attempt(
                        challenge = challengeGenerator.generate(previous = current.challenge),
                        showWrongAnswer = true,
                    )
                }
            }
        }

        return ParentalGateState(
            challenge = attempt.challenge,
            answer = attempt.answer,
            showWrongAnswer = attempt.showWrongAnswer,
            isPassed = attempt.isPassed,
            eventSink = ::handleEvent,
        )
    }

    /** Everything that changes together on a submit, so a wrong answer is one state change, not three. */
    private data class Attempt(
        val challenge: ParentalGateChallenge,
        val answer: String = "",
        val showWrongAnswer: Boolean = false,
        val isPassed: Boolean = false,
    )
}
