/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.parentalgate.impl.challenge

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.random.Random

class ParentalGateChallengesTest {
    @Test
    fun `challenges stay in their ranges and both kinds come up about as often`() {
        val random = Random(seed = 42)
        var previous: ParentalGateChallenge? = null
        var typeNumberCount = 0
        val iterations = 10_000
        repeat(iterations) {
            val challenge = ParentalGateChallenges.next(random, previous)
            when (challenge) {
                is ParentalGateChallenge.TypeNumber -> {
                    typeNumberCount++
                    assertThat(challenge.number).isIn(ParentalGateChallenges.TYPE_NUMBER_RANGE)
                }
                is ParentalGateChallenge.Multiply -> {
                    val operands = setOf(challenge.left, challenge.right)
                    assertThat(operands.any { it in ParentalGateChallenges.MULTIPLY_SMALL_RANGE }).isTrue()
                    assertThat(operands.any { it in ParentalGateChallenges.MULTIPLY_TEEN_RANGE }).isTrue()
                }
            }
            previous = challenge
        }
        assertThat(typeNumberCount).isIn(4_500..5_500)
    }

    @Test
    fun `a new challenge never repeats the previous question or its answer`() {
        val random = Random(seed = 7)
        var previous = ParentalGateChallenges.next(random, null)
        repeat(10_000) {
            val next = ParentalGateChallenges.next(random, previous)
            assertThat(next).isNotEqualTo(previous)
            assertThat(next.answer).isNotEqualTo(previous.answer)
            previous = next
        }
    }

    @Test
    fun `challenges are spread out, not a short cycle`() {
        val random = Random(seed = 1)
        val challenges = List(200) { ParentalGateChallenges.next(random, null) }
        assertThat(challenges.toSet().size).isGreaterThan(150)
        assertThat(challenges.map { it.answer }.toSet().size).isGreaterThan(100)
    }

    @Test
    fun `multiplication operands come in both orders`() {
        val random = Random(seed = 3)
        val multiplications = List(1_000) { ParentalGateChallenges.next(random, null) }
            .filterIsInstance<ParentalGateChallenge.Multiply>()
        assertThat(multiplications.any { it.left in ParentalGateChallenges.MULTIPLY_SMALL_RANGE }).isTrue()
        assertThat(multiplications.any { it.left in ParentalGateChallenges.MULTIPLY_TEEN_RANGE }).isTrue()
    }

    @Test
    fun `the default generator draws a fresh challenge every time`() {
        val generator = DefaultParentalGateChallengeGenerator()
        var previous = generator.generate(previous = null)
        repeat(100) {
            val next = generator.generate(previous)
            assertThat(next.answer).isNotEqualTo(previous.answer)
            previous = next
        }
    }
}
