/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.parentalgate.impl

import io.element.android.libraries.parentalgate.impl.challenge.ParentalGateChallenge
import io.element.android.libraries.parentalgate.impl.challenge.ParentalGateChallengeGenerator
import io.element.android.tests.testutils.lambda.lambdaError

class FakeParentalGateChallengeGenerator(
    private val generateResult: (ParentalGateChallenge?) -> ParentalGateChallenge = { lambdaError() },
) : ParentalGateChallengeGenerator {
    override fun generate(previous: ParentalGateChallenge?): ParentalGateChallenge = generateResult(previous)
}
