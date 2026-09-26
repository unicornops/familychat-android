/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.parentalgate.impl.challenge

import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesBinding
import java.security.SecureRandom
import kotlin.random.Random
import kotlin.random.asKotlinRandom

interface ParentalGateChallengeGenerator {
    /** A random challenge, never equal to [previous] and never with the same answer. */
    fun generate(previous: ParentalGateChallenge?): ParentalGateChallenge
}

@ContributesBinding(AppScope::class)
class DefaultParentalGateChallengeGenerator : ParentalGateChallengeGenerator {
    private val random = SecureRandom().asKotlinRandom()

    override fun generate(previous: ParentalGateChallenge?): ParentalGateChallenge {
        return ParentalGateChallenges.next(random, previous)
    }
}

/**
 * The challenge ranges. Adult-level but quick: a four-digit number written in words, or a single digit times a
 * number in the teens (products from 33 to 171).
 */
object ParentalGateChallenges {
    val TYPE_NUMBER_RANGE = 1_001..EnglishNumberWords.MAX
    val MULTIPLY_SMALL_RANGE = 3..9
    val MULTIPLY_TEEN_RANGE = 11..19

    fun next(random: Random, previous: ParentalGateChallenge?): ParentalGateChallenge {
        var challenge: ParentalGateChallenge
        do {
            challenge = random(random)
        } while (previous != null && challenge.answer == previous.answer)
        return challenge
    }

    private fun random(random: Random): ParentalGateChallenge {
        return if (random.nextBoolean()) {
            ParentalGateChallenge.TypeNumber(TYPE_NUMBER_RANGE.random(random))
        } else {
            val small = MULTIPLY_SMALL_RANGE.random(random)
            val teen = MULTIPLY_TEEN_RANGE.random(random)
            if (random.nextBoolean()) {
                ParentalGateChallenge.Multiply(left = teen, right = small)
            } else {
                ParentalGateChallenge.Multiply(left = small, right = teen)
            }
        }
    }
}
