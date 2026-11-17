#!/usr/bin/env bash
# Copyright 2026 Unicorn Operations Ltd.
#
# SPDX-License-Identifier: AGPL-3.0-only OR LicenseRef-Element-Commercial.
# Please see LICENSE files in the repository root for full details.

# Runs inside android-emulator-runner (emulator-smoke.yml): installs the APK, runs the no-account Maestro flows and
# always leaves the logcat, a screen recording and Maestro's output in $OUT, pass or fail.
set -u

: "${APK:?}" "${APP_ID:?}" "${OUT:?}"
mkdir -p "$OUT/maestro"

adb install -r "$APK" || exit 1
adb logcat -c

# Best-effort screen recording (screenrecord stops after three minutes, enough for these flows).
adb shell screenrecord --time-limit 170 /sdcard/smoke.mp4 &
record_pid=$!

maestro test \
  -e MAESTRO_APP_ID="$APP_ID" \
  --format junit --output "$OUT/maestro/report.xml" \
  --test-output-dir "$OUT/maestro/output" \
  --debug-output "$OUT/maestro/debug" \
  .maestro/smokeTests.yaml
status=$?

# Let screenrecord finalise the file, then collect everything.
adb shell pkill -INT screenrecord || true
wait "$record_pid" 2>/dev/null || true
sleep 2
adb pull /sdcard/smoke.mp4 "$OUT/smoke.mp4" || true
adb logcat -d > "$OUT/logcat.txt" || true

# A crash of the app under test fails the job even if every flow passed.
if grep -E "FATAL EXCEPTION|Process: ${APP_ID//./\\.}," "$OUT/logcat.txt" > "$OUT/crashes.txt" 2>&1 && [ -s "$OUT/crashes.txt" ]; then
  echo "::error::The app crashed during the smoke run, see logcat.txt"
  cat "$OUT/crashes.txt"
  status=1
fi

exit "$status"
