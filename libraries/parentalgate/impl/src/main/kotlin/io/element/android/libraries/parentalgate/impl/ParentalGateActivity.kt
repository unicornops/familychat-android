/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.parentalgate.impl

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import dev.zacsweers.metro.Inject
import io.element.android.compound.colors.SemanticColorsLightDark
import io.element.android.features.enterprise.api.EnterpriseService
import io.element.android.libraries.architecture.bindings
import io.element.android.libraries.core.meta.BuildMeta
import io.element.android.libraries.designsystem.theme.ElementThemeApp
import io.element.android.libraries.featureflag.api.FeatureFlagService
import io.element.android.libraries.parentalgate.api.ParentalGate
import io.element.android.libraries.parentalgate.impl.di.ParentalGateBindings
import io.element.android.libraries.preferences.api.store.AppPreferencesStore

/**
 * The parental gate screen, started by [ParentalGate.createIntent]. It asks one question; the right answer starts the
 * guarded intent (or returns RESULT_OK), cancelling or backing out starts nothing (RESULT_CANCELED).
 */
class ParentalGateActivity : AppCompatActivity() {
    @Inject lateinit var presenter: ParentalGatePresenter
    @Inject lateinit var appPreferencesStore: AppPreferencesStore
    @Inject lateinit var featureFlagService: FeatureFlagService
    @Inject lateinit var enterpriseService: EnterpriseService
    @Inject lateinit var buildMeta: BuildMeta

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        bindings<ParentalGateBindings>().inject(this)
        val request = ParentalGateRequest.from(intent)
        setResult(RESULT_CANCELED)
        setContent {
            val colors by remember {
                enterpriseService.semanticColorsFlow(sessionId = null)
            }.collectAsState(SemanticColorsLightDark.default)
            ElementThemeApp(
                appPreferencesStore = appPreferencesStore,
                featureFlagService = featureFlagService,
                compoundLight = colors.light,
                compoundDark = colors.dark,
                buildMeta = buildMeta,
            ) {
                val state = presenter.present()
                LaunchedEffect(state.isPassed) {
                    if (state.isPassed) onGatePassed(request)
                }
                ParentalGateView(
                    state = state,
                    onCancel = ::finish,
                )
            }
        }
    }

    private fun onGatePassed(request: ParentalGateRequest) {
        startParentalGateTarget(request)
        setResult(RESULT_OK)
        finish()
    }
}
