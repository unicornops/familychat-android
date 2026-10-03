/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.textcomposer

import android.view.textclassifier.TextClassifier
import android.widget.EditText
import android.widget.FrameLayout
import com.google.common.truth.Truth.assertThat
import io.element.android.libraries.androidutils.text.hideTextActionsThatLeaveTheAppInEditTexts
import io.element.android.tests.testutils.robolectric.RobolectricTest
import io.element.android.wysiwyg.EditorEditText
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import java.io.File
import java.net.URI
import java.util.jar.JarFile

/**
 * Family Chat (#11): the rich text composer's EditText is not reachable through the library's API, so [TextComposer]
 * finds it under the host view and replaces its selection callbacks with ours.
 */
class RichTextEditorSelectionActionsTest : RobolectricTest() {
    @Test
    fun `matrix-rich-text-editor never sets selection callbacks of its own`() {
        // EditorEditText loads the Rust library, so it cannot be created on the JVM: look at the compiled library instead.
        // If this fails, the library now sets its own callbacks, which hideTextActionsThatLeaveTheApp() would replace and
        // break: chain to them instead, or ask upstream for a hook.
        val classUrl = EditorEditText::class.java.classLoader!!.getResource("io/element/android/wysiwyg/EditorEditText.class")!!
        assertThat(classUrl.protocol).isEqualTo("jar")
        val library = File(URI(classUrl.path.substringBefore("!/")))
        val callers = JarFile(library).use { jar ->
            jar.entries().asSequence()
                .filter { it.name.startsWith("io/element/android/wysiwyg/") && it.name.endsWith(".class") }
                .filter { entry ->
                    val bytes = jar.getInputStream(entry).use { it.readBytes() }.toString(Charsets.ISO_8859_1)
                    CALLBACK_SETTERS.any { it in bytes }
                }
                .map { it.name }
                .toList()
        }
        assertThat(callers).isEmpty()
    }

    @Test
    fun `the filter reaches an EditText nested under the host view`() {
        val context = RuntimeEnvironment.getApplication()
        val editText = EditText(context)
        val hostView = FrameLayout(context).apply {
            addView(FrameLayout(context).apply { addView(editText) })
        }

        hostView.hideTextActionsThatLeaveTheAppInEditTexts()

        assertThat(editText.customSelectionActionModeCallback).isNotNull()
        assertThat(editText.customInsertionActionModeCallback).isNotNull()
        assertThat(editText.textClassifier).isEqualTo(TextClassifier.NO_OP)
    }

    private companion object {
        val CALLBACK_SETTERS = listOf("setCustomSelectionActionModeCallback", "setCustomInsertionActionModeCallback")
    }
}
