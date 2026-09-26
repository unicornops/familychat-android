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
 * The challenge ranges, the same as the iOS app: a number from 13 to 49 that is not a multiple of ten, times a single
 * digit from 3 to 9, in either order (answers from 39 to 441). Typing a number given in words is not asked: young
 * children can do it.
 */
object ParentalGateChallenges {
    val MULTIPLICAND_RANGE = (13..49).filter { it % 10 != 0 }
    val DIGIT_RANGE = 3..9

    fun next(random: Random, previous: ParentalGateChallenge?): ParentalGateChallenge {
        var challenge: ParentalGateChallenge
        do {
            challenge = random(random)
        } while (previous != null && challenge.answer == previous.answer)
        return challenge
    }

    private fun random(random: Random): ParentalGateChallenge {
        val multiplicand = MULTIPLICAND_RANGE.random(random)
        val digit = DIGIT_RANGE.random(random)
        return if (random.nextBoolean()) {
            ParentalGateChallenge(left = multiplicand, right = digit)
        } else {
            ParentalGateChallenge(left = digit, right = multiplicand)
        }
    }
}
