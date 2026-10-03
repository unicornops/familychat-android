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
# With no argument, it checks the gplay and fdroid release main manifests, and fails if either is missing (build them
# first: ./gradlew :app:processGplayReleaseMainManifest :app:processFdroidReleaseMainManifest).

set -euo pipefail

forbidden=(
    # No location sharing (BuildTimeConfig.LOCATION_SHARING_ENABLED).
    "android.permission.ACCESS_COARSE_LOCATION"
    "android.permission.ACCESS_FINE_LOCATION"
    "android.permission.ACCESS_BACKGROUND_LOCATION"
    "android.permission.FOREGROUND_SERVICE_LOCATION"
    "android.permission.ACCESS_MEDIA_LOCATION"
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
    # Session import from Element Classic (#19): no package visibility of im.vector.app*.
    "im.vector.app"
    # Device and account data the app has no use for.
    "android.permission.QUERY_ALL_PACKAGES"
    "android.permission.READ_PHONE_STATE"
    "android.permission.READ_CONTACTS"
    "android.permission.GET_ACCOUNTS"
)

if [[ $# -gt 0 ]]; then
    manifests=("$@")
else
    manifests=(
        app/build/intermediates/merged_manifest/gplayRelease/processGplayReleaseMainManifest/AndroidManifest.xml
        app/build/intermediates/merged_manifest/fdroidRelease/processFdroidReleaseMainManifest/AndroidManifest.xml
    )
fi

for manifest in "${manifests[@]}"; do
    if [[ ! -f "${manifest}" ]]; then
        echo "Merged manifest not found: ${manifest}. Build it first." >&2
        exit 2
    fi
done

failed=0
for manifest in "${manifests[@]}"; do
    for name in "${forbidden[@]}"; do
        if grep -qF "\"${name}" "${manifest}"; then
            echo "❌ ${manifest}: ${name}" >&2
            failed=1
        fi
    done
    # Play counts Bluetooth and Wi-Fi scanning as location access unless it is flagged neverForLocation.
    if ! python3 - "${manifest}" <<'PY'
import sys
import xml.etree.ElementTree as ET
ANDROID = "{http://schemas.android.com/apk/res/android}"
bad = [
    element.get(ANDROID + "name")
    for element in ET.parse(sys.argv[1]).getroot()
    if element.tag.startswith("uses-permission")
    and element.get(ANDROID + "name") in ("android.permission.BLUETOOTH_SCAN", "android.permission.NEARBY_WIFI_DEVICES")
    and "neverForLocation" not in (element.get(ANDROID + "usesPermissionFlags") or "")
]
for name in bad:
    print(f"❌ {sys.argv[1]}: {name} without usesPermissionFlags=\"neverForLocation\"", file=sys.stderr)
sys.exit(1 if bad else 0)
PY
    then
        failed=1
    fi
done

if [[ ${failed} -ne 0 ]]; then
    echo "Forbidden permission or component in a merged manifest (see above). Remove it with tools:node=\"remove\" in app/src/main/AndroidManifest.xml or drop the dependency." >&2
    exit 1
fi
echo "✅ ${#manifests[@]} merged manifest(s) checked: no forbidden permission or component."
