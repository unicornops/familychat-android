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
    fun `the library's editor is still an EditText, which the view-tree filter can find`() {
        assertThat(EditText::class.java.isAssignableFrom(EditorEditText::class.java)).isTrue()
        // RichTextEditor (wysiwyg-compose) still creates an EditorEditText in its AndroidView.
        val compose = jarOf(RICH_TEXT_EDITOR_CLASS)
        assertThat(compose.classesContaining("io/element/android/wysiwyg/EditorEditText", prefix = "io/element/android/wysiwyg/compose/"))
            .isNotEmpty()
    }

    @Test
    fun `matrix-rich-text-editor never sets selection callbacks or a text classifier of its own`() {
        // EditorEditText loads the Rust library, so it cannot be created on the JVM: look at the compiled library instead.
        // If this fails, the library now sets them itself, and hideTextActionsThatLeaveTheApp() would replace them (or,
        // after our one-off post, be replaced by them): chain to them instead, or ask upstream for a hook.
        val callers = listOf(jarOf(EDITOR_EDIT_TEXT_CLASS), jarOf(RICH_TEXT_EDITOR_CLASS)).distinct().flatMap { jar ->
            SETTERS.flatMap { setter -> jar.classesContaining(setter, prefix = "io/element/android/wysiwyg/") }
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

    private fun jarOf(className: String): File {
        val classUrl = javaClass.classLoader!!.getResource(className.replace('.', '/') + ".class")!!
        assertThat(classUrl.protocol).isEqualTo("jar")
        return File(URI(classUrl.path.substringBefore("!/")))
    }

    private fun File.classesContaining(text: String, prefix: String): List<String> = JarFile(this).use { jar ->
        jar.entries().asSequence()
            .filter { it.name.startsWith(prefix) && it.name.endsWith(".class") }
            .filter { entry -> text in jar.getInputStream(entry).use { it.readBytes() }.toString(Charsets.ISO_8859_1) }
            .map { "$name!${it.name}" }
            .toList()
    }

    private companion object {
        const val EDITOR_EDIT_TEXT_CLASS = "io.element.android.wysiwyg.EditorEditText"
        const val RICH_TEXT_EDITOR_CLASS = "io.element.android.wysiwyg.compose.RichTextEditorKt"
        val SETTERS = listOf("setCustomSelectionActionModeCallback", "setCustomInsertionActionModeCallback", "setTextClassifier")
    }
}
