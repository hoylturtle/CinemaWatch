#!/usr/bin/env bash
set -euo pipefail
mkdir -p build/upgrade-check build/ui-screenshots
capture_evidence() {
  adb logcat -b crash -d -v threadtime > build/upgrade-check/crash.log 2>&1 || true
  adb logcat -d -v threadtime > build/upgrade-check/logcat.log 2>&1 || true
  adb pull /sdcard/cinemawatch-ui build/ui-screenshots >/dev/null 2>&1 || true
}
trap capture_evidence EXIT
run_test() {
  local test_class="$1"
  local log_path="build/upgrade-check/${test_class}.log"
  adb logcat -c
  timeout 180s adb shell am instrument -w -e class "com.cinemawatch.${test_class}" com.cinemawatch.test/androidx.test.runner.AndroidJUnitRunner > "$log_path"
  if ! grep -Eq 'OK \([1-9][0-9]* tests?\)' "$log_path"; then
    cat "$log_path"
    adb logcat -b crash -d -v threadtime || true
    exit 1
  fi
}
adb install -r candidate/upgrade-fixture.apk
adb install -r candidate/upgrade-seed-androidTest.apk
run_test UpgradeSeedTest
adb uninstall com.cinemawatch.test
adb install -r candidate/app-release.apk
adb install -r candidate/app-release-androidTest.apk
run_test UpgradeVerifyTest
run_test FirstRunTest
run_test UpdatesUiTest

run_test FloorPlanTest

run_test MaintenanceFlowTest

run_test PhoneFlowTest
run_test DataResetUiTest
run_test ReleaseBuildTest
