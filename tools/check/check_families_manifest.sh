#!/usr/bin/env bash

# Copyright 2026 Unicorn Operations Ltd.
#
# SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
# Please see LICENSE files in the repository root for full details.

# Family Chat declares a child audience (Play Families policy, unicornops/family-chat#232 decision 4).
# Fails if a merged app manifest asks for a permission or ships a component that the policy, or our own
# decisions, rule out. A new library can bring these in silently, so this runs on every CI build.
#
# Usage: tools/check/check_families_manifest.sh [merged AndroidManifest.xml ...]
# With no argument, it checks every merged manifest under app/build/intermediates (build them first, e.g.
# ./gradlew :app:processGplayReleaseMainManifest :app:processFdroidReleaseMainManifest).

set -euo pipefail

forbidden=(
    # No location sharing (BuildTimeConfig.LOCATION_SHARING_ENABLED).
    "android.permission.ACCESS_COARSE_LOCATION"
    "android.permission.ACCESS_FINE_LOCATION"
    "android.permission.ACCESS_BACKGROUND_LOCATION"
    "android.permission.FOREGROUND_SERVICE_LOCATION"
    "io.element.android.features.location.impl.live.service.LiveLocationSharingService"
    # No advertising ID or analytics (Firebase brings these unless excluded).
    "com.google.android.gms.permission.AD_ID"
    "android.permission.ACCESS_ADSERVICES_AD_ID"
    "android.permission.ACCESS_ADSERVICES_ATTRIBUTION"
    "android.permission.ACCESS_ADSERVICES_TOPICS"
    "com.google.android.gms.measurement"
    "com.google.firebase.analytics"
    # Parental gate: no installing apps from a chat.
    "android.permission.REQUEST_INSTALL_PACKAGES"
    # Device and account data the app has no use for.
    "android.permission.QUERY_ALL_PACKAGES"
    "android.permission.READ_PHONE_STATE"
    "android.permission.READ_CONTACTS"
    "android.permission.GET_ACCOUNTS"
)

if [[ $# -gt 0 ]]; then
    manifests=("$@")
else
    mapfile -t manifests < <(find app/build/intermediates/merged_manifest app/build/intermediates/merged_manifests \
        -name AndroidManifest.xml 2>/dev/null | sort)
fi

if [[ ${#manifests[@]} -eq 0 ]]; then
    echo "No merged manifest found: build one first." >&2
    exit 2
fi

failed=0
for manifest in "${manifests[@]}"; do
    for name in "${forbidden[@]}"; do
        if grep -qF "\"${name}" "${manifest}"; then
            echo "❌ ${manifest}: ${name}" >&2
            failed=1
        fi
    done
done

if [[ ${failed} -ne 0 ]]; then
    echo "Forbidden permission or component in a merged manifest (see above). Remove it with tools:node=\"remove\" in app/src/main/AndroidManifest.xml or drop the dependency." >&2
    exit 1
fi
echo "✅ ${#manifests[@]} merged manifest(s) checked: no forbidden permission or component."
