/*
 * Copyright 2026 Unicorn Operations Ltd.
 *
 * SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
 * Please see LICENSE files in the repository root for full details.
 */

package config

import Versions

/**
 * Family Chat release versions (#7). A release tag is `v<upstream version>-fc.<n>`, e.g. `v26.09.4-fc.1`: the upstream
 * release it is based on, and our release number on top of it.
 *
 * Upstream's version code is `yyyy·10000 + mm·100 + r` (r: upstream release number of the month). Ours keeps the same
 * order and adds our number as a last digit: `yyyy·10000 + mm·100 + r·10 + fc`. It only ever increases, both between
 * our releases of one upstream version and across upstream merges. `r` and `fc` must stay within 0..9 (checked here
 * and by the release workflow); app/build.gradle.kts still multiplies by 10 for the ABI code, which keeps the result
 * well under Play's 2 100 000 000 limit.
 *
 * Upstream's own constants in Versions.kt are left alone, so that upstream merges stay conflict-free.
 */
object FamilyChatVersion {
    /** Our release number on top of the upstream version, from `-Pfamilychat.fc=<n>`; 0 for local and CI builds. */
    fun versionCode(fc: Int): Int {
        require(fc in 0..9) { "Family Chat release number must be in 0..9, got $fc" }
        val upstreamRelease = Versions.VERSION_CODE % 100
        require(upstreamRelease in 0..9) { "Upstream release number $upstreamRelease no longer fits one digit: revise FamilyChatVersion" }
        return (Versions.VERSION_CODE - upstreamRelease) + upstreamRelease * 10 + fc
    }

    fun versionName(fc: Int): String = if (fc == 0) Versions.VERSION_NAME else "${Versions.VERSION_NAME}-fc.$fc"
}
