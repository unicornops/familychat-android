/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.parentalgate.impl.challenge

import androidx.compose.runtime.Immutable

/**
 * One question of the parental gate: a multiplication with both numbers written in words (see [EnglishNumberWords]),
 * "What is twenty-three times seven?", answered in digits. Reading the written-out numbers and multiplying a two-digit
 * number in your head is the adult-level part.
 */
@Immutable
data class ParentalGateChallenge(
    val left: Int,
    val right: Int,
) {
    /** The number the user must type. */
    val answer: Int get() = left * right
}

/** Longest answer we parse; longer input is wrong anyway and must not overflow. */
private const val MAX_ANSWER_DIGITS = 9

/**
 * Whether [input] is the answer. Surrounding whitespace and leading zeros are ignored ("  0047 " answers 47); anything
 * other than the ASCII digits 0-9 is wrong, including signs, separators and spaces between digits.
 */
fun ParentalGateChallenge.isAnsweredBy(input: String): Boolean {
    val digits = input.trim()
    if (digits.isEmpty() || digits.any { it !in '0'..'9' }) return false
    val significant = digits.trimStart('0').ifEmpty { "0" }
    if (significant.length > MAX_ANSWER_DIGITS) return false
    return significant.toInt() == answer
}
