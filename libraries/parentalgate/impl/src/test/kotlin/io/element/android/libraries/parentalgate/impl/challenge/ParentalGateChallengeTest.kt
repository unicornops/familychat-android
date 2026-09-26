/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.parentalgate.impl.challenge

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ParentalGateChallengeTest {
    private val typeNumber = ParentalGateChallenge.TypeNumber(47)
    private val multiply = ParentalGateChallenge.Multiply(left = 12, right = 7)

    @Test
    fun `the answer is the number, or the product`() {
        assertThat(typeNumber.answer).isEqualTo(47)
        assertThat(multiply.answer).isEqualTo(84)
    }

    @Test
    fun `the right digits are accepted`() {
        assertThat(typeNumber.isAnsweredBy("47")).isTrue()
        assertThat(multiply.isAnsweredBy("84")).isTrue()
    }

    @Test
    fun `surrounding whitespace is ignored`() {
        assertThat(typeNumber.isAnsweredBy(" 47")).isTrue()
        assertThat(typeNumber.isAnsweredBy("47 ")).isTrue()
        assertThat(typeNumber.isAnsweredBy("\t47\n")).isTrue()
        assertThat(typeNumber.isAnsweredBy(" 47 ")).isTrue()
    }

    @Test
    fun `leading zeros are ignored`() {
        assertThat(typeNumber.isAnsweredBy("047")).isTrue()
        assertThat(typeNumber.isAnsweredBy("0000047")).isTrue()
        assertThat(ParentalGateChallenge.TypeNumber(0).isAnsweredBy("000")).isTrue()
    }

    @Test
    fun `wrong or malformed answers are rejected`() {
        listOf(
            "",
            "   ",
            "48",
            "470",
            "4 7",
            "+47",
            "-47",
            "47.0",
            "4,7",
            "forty-seven",
            "٤٧",
            "４７",
            "47a",
            "0x2F",
            "99999999999999999999999",
        ).forEach { input ->
            assertThat(typeNumber.isAnsweredBy(input)).isFalse()
        }
    }

    @Test
    fun `a long run of zeros followed by the answer is still the answer`() {
        assertThat(typeNumber.isAnsweredBy("0".repeat(30) + "47")).isTrue()
    }
}
