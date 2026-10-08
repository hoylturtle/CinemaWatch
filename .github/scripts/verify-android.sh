#!/usr/bin/env bash
set -euo pipefail
mkdir -p build/upgrade-check build/ui-screenshots
trap 'adb pull /sdcard/cinemawatch-ui build/ui-screenshots >/dev/null 2>&1 || true' EXIT
run_test() {
  local test_class="$1"
  local log_path="build/upgrade-check/${test_class}.log"
  adb shell am instrument -w -e class "com.cinemawatch.${test_class}" com.cinemawatch.test/androidx.test.runner.AndroidJUnitRunner > "$log_path"
  if ! grep -Eq 'OK \([1-9][0-9]* tests?\)' "$log_path"; then
    cat "$log_path"
    exit 1
  fi
}
adb install -r build/upgrade-fixture.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
run_test UpgradeSeedTest
adb install -r app/build/outputs/apk/debug/app-debug.apk
run_test UpgradeVerifyTest
run_test FirstRunTest
run_test UpdatesUiTest

run_test FloorPlanTest
