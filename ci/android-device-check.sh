#!/usr/bin/env bash
set -euo pipefail
report=app/build/android-verification
mkdir -p "$report"
trap 'adb logcat -d -b crash > app/build/android-verification/crash-log.txt; adb pull /sdcard/Pictures/AsistenteProVerification app/build/android-verification/screenshots || true' EXIT
if [[ -f "$RUNNER_TEMP/asistente-v2/app-debug.apk" ]]; then
  adb install "$RUNNER_TEMP/asistente-v2/app-debug.apk" | tee "$report/install-previous.txt"
fi
adb install -r app/build/outputs/apk/debug/app-debug.apk | tee "$report/install-native.txt"
adb install -r app/build/outputs/apk/debug/app-debug.apk | tee "$report/update-native.txt"
adb shell am start -W -n app.asistentepro.android/org.asistentepro.asistentepro.MainActivity | tee "$report/start-native.txt"
adb shell pidof app.asistentepro.android | tee "$report/process.txt"
adb shell pm list packages | grep -F 'asistentepro' | tee "$report/packages-coexist.txt"
./gradlew :app:connectedDebugAndroidTest --console=plain --stacktrace
