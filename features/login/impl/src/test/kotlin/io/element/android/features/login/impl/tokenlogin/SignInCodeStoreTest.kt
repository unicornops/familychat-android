/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl.tokenlogin

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.TestTimeSource

class SignInCodeStoreTest {
    @Test
    fun `a code is handed out once`() {
        val sut = SignInCodeStore()
        val id = sut.put("syl_token")
        assertThat(sut.contains(id)).isTrue()
        assertThat(sut.consume(id)).isEqualTo("syl_token")
        assertThat(sut.contains(id)).isFalse()
        assertThat(sut.consume(id)).isNull()
    }

    @Test
    fun `an unknown or missing id resolves to nothing`() {
        val sut = SignInCodeStore()
        sut.put("syl_token")
        assertThat(sut.consume("another-id")).isNull()
        assertThat(sut.consume(null)).isNull()
        assertThat(sut.contains(null)).isFalse()
    }

    @Test
    fun `a new link replaces the previous code, the same link keeps its id`() {
        val sut = SignInCodeStore()
        val first = sut.put("syl_first")
        assertThat(sut.put("syl_first")).isEqualTo(first)
        val second = sut.put("syl_second")
        assertThat(second).isNotEqualTo(first)
        assertThat(sut.contains(first)).isFalse()
        assertThat(sut.consume(second)).isEqualTo("syl_second")
    }

    @Test
    fun `discard forgets the code`() {
        val sut = SignInCodeStore()
        val id = sut.put("syl_token")
        sut.discard("another-id")
        assertThat(sut.contains(id)).isTrue()
        sut.discard(id)
        assertThat(sut.contains(id)).isFalse()
    }

    @Test
    fun `a code expires after 15 minutes`() {
        val timeSource = TestTimeSource()
        val sut = SignInCodeStore().also { it.timeSource = timeSource }
        val id = sut.put("syl_token")
        timeSource += 14.minutes
        assertThat(sut.contains(id)).isTrue()
        timeSource += 2.minutes
        assertThat(sut.consume(id)).isNull()
    }

    @Test
    fun `the store never prints the code`() {
        val sut = SignInCodeStore()
        sut.put("syl_token")
        assertThat(sut.toString()).doesNotContain("syl_token")
    }
}
