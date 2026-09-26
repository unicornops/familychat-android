/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.matrix.impl.fixtures.fakes

import io.element.android.tests.testutils.lambda.lambdaError
import org.matrix.rustcomponents.sdk.LoginWithQrCodeHandler
import org.matrix.rustcomponents.sdk.NoHandle
import org.matrix.rustcomponents.sdk.QrCodeData
import org.matrix.rustcomponents.sdk.QrLoginProgressListener

class FakeFfiLoginWithQrCodeHandler(
    private val scanResult: suspend (QrCodeData) -> Unit = { lambdaError() },
) : LoginWithQrCodeHandler(NoHandle) {
    override suspend fun scan(qrCodeData: QrCodeData, progressListener: QrLoginProgressListener) {
        scanResult(qrCodeData)
    }
}
