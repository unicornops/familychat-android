/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.parentalgate.impl.challenge

import com.google.common.truth.Truth.assertThat
import org.junit.Assert.assertThrows
import org.junit.Test

class EnglishNumberWordsTest {
    @Test
    fun `known numbers are written in British English`() {
        mapOf(
            0 to "zero",
            7 to "seven",
            12 to "twelve",
            13 to "thirteen",
            19 to "nineteen",
            20 to "twenty",
            40 to "forty",
            47 to "forty-seven",
            99 to "ninety-nine",
            100 to "one hundred",
            101 to "one hundred and one",
            115 to "one hundred and fifteen",
            140 to "one hundred and forty",
            999 to "nine hundred and ninety-nine",
            1_000 to "one thousand",
            1_001 to "one thousand and one",
            1_100 to "one thousand one hundred",
            1_101 to "one thousand one hundred and one",
            7_015 to "seven thousand and fifteen",
            7_215 to "seven thousand two hundred and fifteen",
            8_080 to "eight thousand and eighty",
            9_999 to "nine thousand nine hundred and ninety-nine",
        ).forEach { (number, words) ->
            assertThat(EnglishNumberWords.toWords(number)).isEqualTo(words)
        }
    }

    @Test
    fun `every number up to the maximum reads back as itself`() {
        val seen = mutableSetOf<String>()
        for (number in 0..EnglishNumberWords.MAX) {
            val words = EnglishNumberWords.toWords(number)
            assertThat(words).doesNotContainMatch("[0-9]")
            assertThat(words).isEqualTo(words.trim())
            assertThat(words).doesNotContain("  ")
            assertThat(parseEnglishNumber(words)).isEqualTo(number)
            assertThat(seen.add(words)).isTrue()
        }
    }

    @Test
    fun `numbers outside the range are rejected`() {
        assertThrows(IllegalArgumentException::class.java) { EnglishNumberWords.toWords(-1) }
        assertThrows(IllegalArgumentException::class.java) { EnglishNumberWords.toWords(EnglishNumberWords.MAX + 1) }
    }

    /** An independent reader for the words, so the round trip checks the writer against something it does not share. */
    private fun parseEnglishNumber(words: String): Int {
        val values = mapOf(
            "zero" to 0,
            "one" to 1,
            "two" to 2,
            "three" to 3,
            "four" to 4,
            "five" to 5,
            "six" to 6,
            "seven" to 7,
            "eight" to 8,
            "nine" to 9,
            "ten" to 10,
            "eleven" to 11,
            "twelve" to 12,
            "thirteen" to 13,
            "fourteen" to 14,
            "fifteen" to 15,
            "sixteen" to 16,
            "seventeen" to 17,
            "eighteen" to 18,
            "nineteen" to 19,
            "twenty" to 20,
            "thirty" to 30,
            "forty" to 40,
            "fifty" to 50,
            "sixty" to 60,
            "seventy" to 70,
            "eighty" to 80,
            "ninety" to 90,
        )
        var total = 0
        var current = 0
        for (token in words.replace("-", " ").split(" ").filter { it != "and" }) {
            when (token) {
                "thousand" -> {
                    total += current * 1_000
                    current = 0
                }
                "hundred" -> current *= 100
                else -> current += values.getValue(token)
            }
        }
        return total + current
    }
}
