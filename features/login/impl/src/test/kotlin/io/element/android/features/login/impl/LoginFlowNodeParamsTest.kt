/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.login.impl

import com.google.common.truth.Truth.assertThat
import io.element.android.features.login.impl.screens.onboarding.OnBoardingNode
import io.element.android.features.login.impl.tokenlogin.SignInCodeStore
import org.junit.Test

/**
 * The password fallback after a sign-in code goes to the link's account provider (through discovery), never to `hs`
 * (unicornops/family-chat#254, rule 2): a crafted link can name another family's server as `hs`.
 */
class LoginFlowNodeParamsTest {
    // `hs` names another family's server: a password must never go there
    private fun aLink(signInCodeId: String?) = LoginFlowNode.Params(
        accountProvider = "smith.ie",
        loginHint = "mxid:@kid:smith.ie",
        hs = "attacker.safechat.family",
        signInCodeId = signInCodeId,
    )

    private val expected = OnBoardingNode.Params(
        accountProvider = "smith.ie",
        loginHint = "mxid:@kid:smith.ie",
        showBackButton = false,
    )

    @Test
    fun `after the user declines the code, the password form is for the account provider`() {
        val signInCodeStore = SignInCodeStore()
        val params = aLink(signInCodeStore.put("syl_token"))
        // Declining discards the code, then hands over to the regular flow
        signInCodeStore.discard(params.signInCodeId!!)

        assertThat(params.toOnBoardingParams(showBackButton = false)).isEqualTo(expected)
    }

    @Test
    fun `after a failed code, the password form is for the account provider`() {
        val signInCodeStore = SignInCodeStore()
        val params = aLink(signInCodeStore.put("syl_token"))
        // The code was handed out for redemption, and the server refused it
        signInCodeStore.consume(params.signInCodeId!!)

        assertThat(params.hasSignInCode(signInCodeStore)).isFalse()
        assertThat(params.toOnBoardingParams(showBackButton = false)).isEqualTo(expected)
    }

    @Test
    fun `when the code is gone after a process death, the password form is for the account provider`() {
        // A fresh store: the id restored from the saved state names nothing any more
        val params = aLink("an-id-from-before-the-process-died")

        assertThat(params.hasSignInCode(SignInCodeStore())).isFalse()
        assertThat(params.toOnBoardingParams(showBackButton = true)).isEqualTo(expected.copy(showBackButton = true))
    }
}
