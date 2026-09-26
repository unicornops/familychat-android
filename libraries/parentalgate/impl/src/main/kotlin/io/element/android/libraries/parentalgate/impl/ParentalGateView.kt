/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.libraries.parentalgate.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.compound.tokens.generated.CompoundIcons
import io.element.android.libraries.designsystem.atomic.pages.FlowStepPage
import io.element.android.libraries.designsystem.components.BigIcon
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Button
import io.element.android.libraries.designsystem.theme.components.Text
import io.element.android.libraries.designsystem.theme.components.TextButton
import io.element.android.libraries.designsystem.theme.components.TextField
import io.element.android.libraries.parentalgate.impl.challenge.EnglishNumberWords
import io.element.android.libraries.parentalgate.impl.challenge.ParentalGateChallenge
import io.element.android.libraries.ui.strings.CommonStrings

@Composable
fun ParentalGateView(
    state: ParentalGateState,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    FlowStepPage(
        modifier = modifier,
        isScrollable = true,
        onBackClick = onCancel,
        iconStyle = BigIcon.Style.Default(CompoundIcons.Lock()),
        title = stringResource(R.string.screen_parental_gate_title),
        subTitle = stringResource(R.string.screen_parental_gate_subtitle),
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Read as one announcement, again each time a wrong answer replaces the question.
                Column(
                    modifier = Modifier.semantics(mergeDescendants = true) {
                        heading()
                        liveRegion = LiveRegionMode.Polite
                    },
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (state.showWrongAnswer) {
                        Text(
                            text = stringResource(R.string.screen_parental_gate_wrong_answer),
                            style = ElementTheme.typography.fontBodyMdRegular,
                            color = ElementTheme.colors.textCriticalPrimary,
                        )
                    }
                    Text(
                        text = questionText(state.challenge),
                        style = ElementTheme.typography.fontHeadingSmMedium,
                        color = ElementTheme.colors.textPrimary,
                    )
                }
                TextField(
                    value = state.answer,
                    onValueChange = { state.eventSink(ParentalGateEvent.UpdateAnswer(it)) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    label = stringResource(R.string.screen_parental_gate_answer_label),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { state.eventSink(ParentalGateEvent.Submit) },
                    ),
                )
            }
        },
        buttons = {
            Button(
                text = stringResource(CommonStrings.action_continue),
                enabled = state.canSubmit,
                modifier = Modifier.fillMaxWidth(),
                onClick = { state.eventSink(ParentalGateEvent.Submit) },
            )
            TextButton(
                text = stringResource(CommonStrings.action_cancel),
                modifier = Modifier.fillMaxWidth(),
                onClick = onCancel,
            )
        },
    )
}

@Composable
private fun questionText(challenge: ParentalGateChallenge): String {
    return stringResource(
        R.string.screen_parental_gate_question_multiply,
        EnglishNumberWords.toWords(challenge.left),
        EnglishNumberWords.toWords(challenge.right),
    )
}

@PreviewsDayNight
@Composable
internal fun ParentalGateViewPreview(@PreviewParameter(ParentalGateStatePreviewParam::class) state: ParentalGateState) = ElementPreview {
    ParentalGateView(
        state = state,
        onCancel = {},
    )
}
