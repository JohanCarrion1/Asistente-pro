#!/usr/bin/env bash
set -euo pipefail
device_api=$(adb shell getprop ro.build.version.sdk | tr -d '\r')
report="app/build/android-verification/api-$device_api"
mkdir -p "$report"
adb shell getprop ro.build.version.release > "$report/android-version.txt"
if [[ "$device_api" -ge 37 ]]; then
  adb shell cmd audio set-enable-hardening throw > "$report/android17-audio-hardening.txt"
fi
collect_evidence() {
  adb logcat -d -b crash > "$report/crash-log.txt"
  adb pull /sdcard/Pictures/AsistenteProVerification "$report/screenshots" || true
  cp -R app/build/outputs/androidTest-results "$report/" || true
}
trap collect_evidence EXIT
if [[ -f "$RUNNER_TEMP/asistente-v2/app-debug.apk" ]]; then
  adb install "$RUNNER_TEMP/asistente-v2/app-debug.apk" | tee "$report/install-previous.txt"
fi
if [[ -f "$RUNNER_TEMP/asistente-v3/AsistentePro-Nativo-3.0.1.apk" ]]; then
  adb install -r "$RUNNER_TEMP/asistente-v3/AsistentePro-Nativo-3.0.1.apk" | tee "$report/install-version-3.txt"
fi
adb install -r verification-app/build/outputs/apk/debug/verification-app-debug.apk | tee "$report/install-test-fixture.txt"
# Google images may already contain the real YouTube. Keep this test deterministic
# by disabling that preinstalled package only inside this disposable CI emulator.
if adb shell pm list packages com.google.android.youtube | grep -q 'com.google.android.youtube'; then
  adb shell pm disable-user --user 0 com.google.android.youtube | tee "$report/disable-preinstalled-youtube.txt"
fi
adb install -r app/build/outputs/apk/debug/app-debug.apk | tee "$report/install-native.txt"
adb install -r app/build/outputs/apk/debug/app-debug.apk | tee "$report/update-native.txt"
adb shell am start -W -n app.asistentepro.android/org.asistentepro.asistentepro.MainActivity | tee "$report/start-native.txt"
adb shell pidof app.asistentepro.android | tee "$report/process.txt"
adb shell pm list packages | grep -F 'asistentepro' | tee "$report/packages-coexist.txt"
./gradlew :app:connectedDebugAndroidTest --console=plain --stacktrace
