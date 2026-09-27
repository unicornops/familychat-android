/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.parentalgate.impl.challenge

/**
 * Writes a number out in British English words: 47 is "forty-seven", 7015 is "seven thousand and fifteen",
 * 7215 is "seven thousand two hundred and fifteen".
 *
 * Not localised: the parental gate asks its questions in English whatever the device language (the numbers in words
 * are the whole task, and a translated version needs per-language grammar, not string resources).
 */
object EnglishNumberWords {
    const val MAX = 9_999

    private val UNITS = listOf(
        "zero",
        "one",
        "two",
        "three",
        "four",
        "five",
        "six",
        "seven",
        "eight",
        "nine",
        "ten",
        "eleven",
        "twelve",
        "thirteen",
        "fourteen",
        "fifteen",
        "sixteen",
        "seventeen",
        "eighteen",
        "nineteen",
    )
    private val TENS = listOf("", "", "twenty", "thirty", "forty", "fifty", "sixty", "seventy", "eighty", "ninety")

    fun toWords(number: Int): String {
        require(number in 0..MAX) { "Only 0..$MAX can be written out" }
        return when {
            number < 100 -> belowHundred(number)
            number < 1_000 -> hundreds(number)
            else -> thousands(number)
        }
    }

    private fun belowHundred(number: Int): String {
        if (number < 20) return UNITS[number]
        val units = number % 10
        return if (units == 0) TENS[number / 10] else "${TENS[number / 10]}-${UNITS[units]}"
    }

    private fun hundreds(number: Int): String {
        val head = "${UNITS[number / 100]} hundred"
        val rest = number % 100
        return if (rest == 0) head else "$head and ${belowHundred(rest)}"
    }

    private fun thousands(number: Int): String {
        val head = "${UNITS[number / 1_000]} thousand"
        val rest = number % 1_000
        return when {
            rest == 0 -> head
            rest < 100 -> "$head and ${belowHundred(rest)}"
            else -> "$head ${hundreds(rest)}"
        }
    }
}
