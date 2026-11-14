#!/usr/bin/env bash
set -euo pipefail
if git ls-files | rg -i '(^|/)(buildozer\.spec|requirements\.txt|.*\.py)$'; then
  echo 'ERROR: el repositorio activo aún contiene el runtime anterior.' >&2
  exit 1
fi
test -f app/src/main/AndroidManifest.xml
test -f app/src/main/java/org/asistentepro/asistentepro/MainActivity.kt
echo 'Proyecto Android nativo: sin Python, Kivy, Flask ni Buildozer.'
