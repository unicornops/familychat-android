/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.parentalgate.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.parentalgate.impl.challenge.ParentalGateChallenge
import io.element.android.tests.testutils.WarmUpRule
import io.element.android.tests.testutils.test
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

private val A_FIRST_CHALLENGE = ParentalGateChallenge.TypeNumber(7215)
private val A_SECOND_CHALLENGE = ParentalGateChallenge.Multiply(left = 14, right = 7)
private val A_THIRD_CHALLENGE = ParentalGateChallenge.TypeNumber(1001)

class ParentalGatePresenterTest {
    @get:Rule
    val warmUpRule = WarmUpRule()

    @Test
    fun `present - initial state shows a fresh challenge and is not passed`() = runTest {
        val previousChallenges = mutableListOf<ParentalGateChallenge?>()
        val presenter = createPresenter(previousChallenges)
        presenter.test {
            val initialState = awaitItem()
            assertThat(initialState.challenge).isEqualTo(A_FIRST_CHALLENGE)
            assertThat(initialState.answer).isEmpty()
            assertThat(initialState.showWrongAnswer).isFalse()
            assertThat(initialState.isPassed).isFalse()
            assertThat(initialState.canSubmit).isFalse()
            assertThat(previousChallenges).containsExactly(null)
        }
    }

    @Test
    fun `present - the right answer passes the gate`() = runTest {
        createPresenter().test {
            awaitItem().eventSink(ParentalGateEvent.UpdateAnswer(" 7215 "))
            val typedState = awaitItem()
            assertThat(typedState.canSubmit).isTrue()
            typedState.eventSink(ParentalGateEvent.Submit)
            val passedState = awaitItem()
            assertThat(passedState.isPassed).isTrue()
            assertThat(passedState.canSubmit).isFalse()
            assertThat(passedState.challenge).isEqualTo(A_FIRST_CHALLENGE)
        }
    }

    @Test
    fun `present - a wrong answer never passes and replaces the question with a new one`() = runTest {
        val previousChallenges = mutableListOf<ParentalGateChallenge?>()
        createPresenter(previousChallenges).test {
            awaitItem().eventSink(ParentalGateEvent.UpdateAnswer("7216"))
            awaitItem().eventSink(ParentalGateEvent.Submit)
            val wrongState = awaitItem()
            assertThat(wrongState.isPassed).isFalse()
            assertThat(wrongState.showWrongAnswer).isTrue()
            assertThat(wrongState.answer).isEmpty()
            assertThat(wrongState.challenge).isEqualTo(A_SECOND_CHALLENGE)
            // The generator is told which question to avoid
            assertThat(previousChallenges).containsExactly(null, A_FIRST_CHALLENGE).inOrder()
        }
    }

    @Test
    fun `present - the answer to the replaced question no longer opens the gate`() = runTest {
        createPresenter().test {
            awaitItem().eventSink(ParentalGateEvent.UpdateAnswer("1"))
            awaitItem().eventSink(ParentalGateEvent.Submit)
            // Retrying the first question's answer on the new question is wrong, and brings a third question
            awaitItem().eventSink(ParentalGateEvent.UpdateAnswer("7215"))
            awaitItem().eventSink(ParentalGateEvent.Submit)
            val state = awaitItem()
            assertThat(state.isPassed).isFalse()
            assertThat(state.challenge).isEqualTo(A_THIRD_CHALLENGE)
            // The new question's own answer does open it
            state.eventSink(ParentalGateEvent.UpdateAnswer("1001"))
            awaitItem().eventSink(ParentalGateEvent.Submit)
            assertThat(awaitItem().isPassed).isTrue()
        }
    }

    @Test
    fun `present - submitting a blank answer does nothing`() = runTest {
        val previousChallenges = mutableListOf<ParentalGateChallenge?>()
        createPresenter(previousChallenges).test {
            val initialState = awaitItem()
            initialState.eventSink(ParentalGateEvent.Submit)
            initialState.eventSink(ParentalGateEvent.UpdateAnswer("  "))
            val blankState = awaitItem()
            blankState.eventSink(ParentalGateEvent.Submit)
            expectNoEvents()
            assertThat(blankState.canSubmit).isFalse()
            assertThat(previousChallenges).containsExactly(null)
        }
    }

    @Test
    fun `present - once passed, further events are ignored`() = runTest {
        createPresenter().test {
            awaitItem().eventSink(ParentalGateEvent.UpdateAnswer("7215"))
            awaitItem().eventSink(ParentalGateEvent.Submit)
            val passedState = awaitItem()
            passedState.eventSink(ParentalGateEvent.UpdateAnswer("0"))
            passedState.eventSink(ParentalGateEvent.Submit)
            expectNoEvents()
        }
    }

    private fun createPresenter(
        previousChallenges: MutableList<ParentalGateChallenge?> = mutableListOf(),
        challenges: ArrayDeque<ParentalGateChallenge> = ArrayDeque(listOf(A_FIRST_CHALLENGE, A_SECOND_CHALLENGE, A_THIRD_CHALLENGE)),
    ) = ParentalGatePresenter(
        challengeGenerator = FakeParentalGateChallengeGenerator(
            generateResult = { previous ->
                previousChallenges.add(previous)
                challenges.removeFirst()
            }
        ),
    )
}
