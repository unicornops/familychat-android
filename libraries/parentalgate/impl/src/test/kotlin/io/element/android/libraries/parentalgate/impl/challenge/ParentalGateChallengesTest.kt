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
    fun `a challenge is a number from 13 to 49, not a multiple of ten, times a digit from 3 to 9`() {
        val random = Random(seed = 42)
        var previous: ParentalGateChallenge? = null
        repeat(10_000) {
            val challenge = ParentalGateChallenges.next(random, previous)
            val (digit, multiplicand) = listOf(challenge.left, challenge.right).sorted()
            assertThat(digit).isIn(3..9)
            assertThat(multiplicand).isIn(13..49)
            assertThat(multiplicand % 10).isNotEqualTo(0)
            assertThat(challenge.answer).isIn(39..441)
            previous = challenge
        }
    }

    @Test
    fun `the ranges are the ones agreed with iOS`() {
        assertThat(ParentalGateChallenges.DIGIT_RANGE).isEqualTo(3..9)
        assertThat(ParentalGateChallenges.MULTIPLICAND_RANGE).containsExactlyElementsIn((13..49).filter { it % 10 != 0 })
        assertThat(ParentalGateChallenges.MULTIPLICAND_RANGE).containsNoneOf(20, 30, 40)
    }

    @Test
    fun `every multiplicand and digit comes up, in both orders`() {
        val random = Random(seed = 3)
        val challenges = List(20_000) { ParentalGateChallenges.next(random, null) }
        val pairs = challenges.map { listOf(it.left, it.right).sorted() }
        assertThat(pairs.map { it[0] }.toSet()).containsExactlyElementsIn(ParentalGateChallenges.DIGIT_RANGE.toList())
        assertThat(pairs.map { it[1] }.toSet()).containsExactlyElementsIn(ParentalGateChallenges.MULTIPLICAND_RANGE)
        val digitFirst = challenges.count { it.left in ParentalGateChallenges.DIGIT_RANGE }
        assertThat(digitFirst).isIn(9_000..11_000)
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
