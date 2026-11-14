#!/usr/bin/env bash
set -euo pipefail
api="${1:?Indica la API del emulador}"
report=app/build/android-verification/avd
mkdir -p "$report"
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --version > "$report/cmdline-tools-version.txt"
avd_file="${ANDROID_AVD_HOME:?}/xylon-api${api}.ini"
cp "$avd_file" "$report/metadata.ini"
cat "$avd_file"
if [[ "$api" == 37.0 ]]; then
  grep -Fx 'target=android-37.0' "$avd_file"
fi
