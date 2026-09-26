/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.textcomposer.components.markdown

import android.view.textclassifier.TextClassifier
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import io.element.android.tests.testutils.robolectric.RobolectricTest
import org.junit.Test

class MarkdownEditTextTest : RobolectricTest() {
    @Test
    fun `the composer has no text selection actions that leave the app`() {
        val editText = MarkdownEditText(ApplicationProvider.getApplicationContext())
        assertThat(editText.textClassifier).isSameInstanceAs(TextClassifier.NO_OP)
        assertThat(editText.customSelectionActionModeCallback).isNotNull()
        assertThat(editText.customInsertionActionModeCallback).isNotNull()
    }
}
