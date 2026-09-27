/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only.
 * Please see LICENSE files in the repository root for full details.
 */

package io.element.android.features.location.impl.common.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.element.android.compound.theme.ElementTheme
import io.element.android.features.location.impl.R
import io.element.android.libraries.androidutils.system.openUrlInExternalApp
import io.element.android.libraries.designsystem.preview.ElementPreview
import io.element.android.libraries.designsystem.preview.PreviewsDayNight
import io.element.android.libraries.designsystem.theme.components.Text

/** The map data licence page, opened through the parental gate. */
internal const val MAP_ATTRIBUTION_URL = "https://www.openstreetmap.org/copyright"

/**
 * The map tiles' attribution (MapTiler and OpenStreetMap contributors), always visible on the map.
 *
 * Family Chat (parental gate, unicornops/family-chat#232 decision 10): MapLibre's own attribution button opens the
 * browser directly from its dialog, so it is turned off in MapDefaults and this label replaces it. Tapping it opens the
 * licence page through openUrlInExternalApp(), behind the gate.
 */
@Composable
fun MapAttribution(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Text(
        text = stringResource(R.string.screen_location_map_attribution),
        style = ElementTheme.typography.fontBodyXsRegular,
        color = ElementTheme.colors.textSecondary,
        modifier = modifier
            .background(ElementTheme.colors.bgCanvasDefault.copy(alpha = 0.8f), RoundedCornerShape(4.dp))
            .clickable(role = Role.Button) { context.openUrlInExternalApp(MAP_ATTRIBUTION_URL) }
            .padding(horizontal = 4.dp, vertical = 2.dp),
    )
}

@PreviewsDayNight
@Composable
internal fun MapAttributionPreview() = ElementPreview {
    MapAttribution()
}
