/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

@file:OptIn(ExperimentalTestApi::class)

package io.element.android.libraries.parentalgate.impl

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.runEmptyComposeUiTest
import androidx.core.net.toUri
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.element.android.features.enterprise.test.FakeEnterpriseService
import io.element.android.libraries.di.DependencyInjectionGraphOwner
import io.element.android.libraries.featureflag.test.FakeFeatureFlagService
import io.element.android.libraries.matrix.test.core.aBuildMeta
import io.element.android.libraries.parentalgate.api.ParentalGate
import io.element.android.libraries.parentalgate.impl.challenge.ParentalGateChallenge
import io.element.android.libraries.parentalgate.impl.di.ParentalGateBindings
import io.element.android.libraries.preferences.test.InMemoryAppPreferencesStore
import io.element.android.libraries.ui.strings.CommonStrings
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

private const val A_URL = "https://example.org/page"

/**
 * The whole gate: shown with a link to open, it opens nothing until the question is answered, and then opens the link.
 */
@Config(application = ParentalGateActivityTest.TestApplication::class)
class ParentalGateActivityTest : RobolectricTest() {
    class TestApplication : Application(), DependencyInjectionGraphOwner {
        override fun onCreate() {
            super.onCreate()
            // In the app, the activity inherits the application theme (Theme.ElementX, declared by :app).
            registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
                override fun onActivityPreCreated(activity: Activity, savedInstanceState: Bundle?) {
                    activity.setTheme(androidx.appcompat.R.style.Theme_AppCompat_DayNight_NoActionBar)
                }

                override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
                override fun onActivityStarted(activity: Activity) = Unit
                override fun onActivityResumed(activity: Activity) = Unit
                override fun onActivityPaused(activity: Activity) = Unit
                override fun onActivityStopped(activity: Activity) = Unit
                override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
                override fun onActivityDestroyed(activity: Activity) = Unit
            })
        }

        override val graph: Any = object : ParentalGateBindings {
            override fun inject(activity: ParentalGateActivity) {
                val challenges = ArrayDeque(
                    listOf(
                        ParentalGateChallenge(left = 23, right = 7),
                        ParentalGateChallenge(left = 14, right = 9),
                        ParentalGateChallenge(left = 6, right = 47),
                    )
                )
                activity.presenter = ParentalGatePresenter(FakeParentalGateChallengeGenerator { challenges.removeFirst() })
                activity.appPreferencesStore = InMemoryAppPreferencesStore()
                activity.featureFlagService = FakeFeatureFlagService()
                activity.enterpriseService = FakeEnterpriseService()
                activity.buildMeta = aBuildMeta()
            }
        }
    }

    private val context: Application get() = ApplicationProvider.getApplicationContext()

    private fun gateIntent(): Intent = ParentalGate.createIntent(
        context = context,
        target = Intent(Intent.ACTION_VIEW, A_URL.toUri()),
    )

    @Test
    fun `showing the gate opens nothing`() = runEmptyComposeUiTest {
        ActivityScenario.launchActivityForResult<ParentalGateActivity>(gateIntent()).use { scenario ->
            onNodeWithText(context.getString(R.string.screen_parental_gate_question_multiply, "twenty-three", "seven"))
                .assertExists()
            scenario.onActivity { activity ->
                assertThat(shadowOf(activity).nextStartedActivity).isNull()
                assertThat(activity.isFinishing).isFalse()
            }
        }
    }

    @Test
    fun `the right answer opens the link and closes the gate`() = runEmptyComposeUiTest {
        ActivityScenario.launchActivityForResult<ParentalGateActivity>(gateIntent()).use { scenario ->
            answer("161")
            scenario.onActivity { activity ->
                val started = shadowOf(activity).nextStartedActivity
                assertThat(started.action).isEqualTo(Intent.ACTION_VIEW)
                assertThat(started.data).isEqualTo(A_URL.toUri())
                assertThat(activity.isFinishing).isTrue()
            }
            assertThat(scenario.result.resultCode).isEqualTo(Activity.RESULT_OK)
        }
    }

    @Test
    fun `a wrong answer opens nothing and asks a new question`() = runEmptyComposeUiTest {
        ActivityScenario.launchActivityForResult<ParentalGateActivity>(gateIntent()).use { scenario ->
            answer("162")
            onNodeWithText(context.getString(R.string.screen_parental_gate_question_multiply, "fourteen", "nine")).assertExists()
            onNodeWithText(context.getString(R.string.screen_parental_gate_wrong_answer)).assertExists()
            scenario.onActivity { activity ->
                assertThat(shadowOf(activity).nextStartedActivity).isNull()
                assertThat(activity.isFinishing).isFalse()
            }
        }
    }

    @Test
    fun `three wrong answers close the gate without opening anything`() = runEmptyComposeUiTest {
        ActivityScenario.launchActivityForResult<ParentalGateActivity>(gateIntent()).use { scenario ->
            answer("1")
            answer("2")
            answer("3")
            scenario.onActivity { activity ->
                assertThat(shadowOf(activity).nextStartedActivity).isNull()
            }
            assertThat(scenario.result.resultCode).isEqualTo(Activity.RESULT_CANCELED)
        }
    }

    @Test
    fun `cancelling opens nothing`() = runEmptyComposeUiTest {
        ActivityScenario.launchActivityForResult<ParentalGateActivity>(gateIntent()).use { scenario ->
            onNodeWithText(context.getString(CommonStrings.action_cancel)).performClick()
            scenario.onActivity { activity ->
                assertThat(shadowOf(activity).nextStartedActivity).isNull()
            }
            assertThat(scenario.result.resultCode).isEqualTo(Activity.RESULT_CANCELED)
        }
    }

    @Test
    fun `without a link, passing only reports success`() = runEmptyComposeUiTest {
        ActivityScenario.launchActivityForResult<ParentalGateActivity>(ParentalGate.createIntent(context)).use { scenario ->
            answer("161")
            scenario.onActivity { activity ->
                assertThat(shadowOf(activity).nextStartedActivity).isNull()
            }
            assertThat(scenario.result.resultCode).isEqualTo(Activity.RESULT_OK)
        }
    }

    private fun ComposeUiTest.answer(answer: String) {
        onNodeWithText(context.getString(R.string.screen_parental_gate_answer_label)).performTextInput(answer)
        onNodeWithText(context.getString(CommonStrings.action_continue)).performClick()
        waitForIdle()
    }
}
