/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.x

import android.os.Bundle
import com.google.common.truth.Truth.assertThat
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test

class LaunchIntentTrackerTest : RobolectricTest() {
    @Test
    fun `a new activity handles its launch intent once`() {
        val sut = LaunchIntentTracker(savedInstanceState = null)
        assertThat(sut.shouldHandleLaunchIntent()).isTrue()
        assertThat(sut.shouldHandleLaunchIntent()).isFalse()
    }

    @Test
    fun `a recreated activity does not handle the launch intent again`() {
        val first = LaunchIntentTracker(savedInstanceState = null)
        first.shouldHandleLaunchIntent()
        val outState = Bundle().also { first.onSaveInstanceState(it) }

        val recreated = LaunchIntentTracker(savedInstanceState = outState)

        assertThat(recreated.shouldHandleLaunchIntent()).isFalse()
    }

    @Test
    fun `a recreated activity still handles a launch intent saved before it was handled`() {
        val first = LaunchIntentTracker(savedInstanceState = null)
        val outState = Bundle().also { first.onSaveInstanceState(it) }

        val recreated = LaunchIntentTracker(savedInstanceState = outState)

        assertThat(recreated.shouldHandleLaunchIntent()).isTrue()
    }

    @Test
    fun `a new intent arriving before the main node is ready is handled`() {
        val first = LaunchIntentTracker(savedInstanceState = null)
        first.shouldHandleLaunchIntent()
        val recreated = LaunchIntentTracker(savedInstanceState = Bundle().also { first.onSaveInstanceState(it) })

        // onCreate(savedInstanceState) -> onNewIntent -> onMainNodeInit
        recreated.onLaunchIntentReplaced()

        assertThat(recreated.shouldHandleLaunchIntent()).isTrue()
    }
}
