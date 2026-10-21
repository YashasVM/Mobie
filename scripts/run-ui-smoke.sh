#!/usr/bin/env bash
# Run inside android-emulator-runner, before it tears down the emulator.
# Deliberately do not enable errexit: diagnostics must survive a failed Gradle invocation.
set -uo pipefail

"${GRADLEW:-./gradlew}" --no-daemon connectedDebugAndroidTest
test_status=$?

evidence_dir=${UI_SMOKE_EVIDENCE_DIR:-app/build/ui-smoke-evidence}
if mkdir -p "$evidence_dir"; then
  for file in memory-system-apps.png memory-system-apps-hierarchy.txt; do
    if ! "${ADB:-adb}" pull "/sdcard/$file" "$evidence_dir/$file"; then
      printf '::warning::Settings diagnostic unavailable: %s\n' "$file" >&2
    fi
  done
else
  printf '::warning::Could not create UI smoke evidence directory\n' >&2
fi

# A failed pull must neither hide a test failure nor turn a passing test run into a failure.
exit "$test_status"
