#!/usr/bin/env bash
set -euo pipefail
expected='305170c9d2d00d1739c9e1ce6f0d91f69707a2a816ed1a76de2c976d66027895'
if [[ $# -eq 0 ]]; then
  test -f "$HOME/.android/debug.keystore" || {
    echo 'Falta la firma existente. No se generará otra ni se publicará un APK incompatible. Restaura el keystore antes de continuar.' >&2
    exit 1
  }
  actual=$(keytool -exportcert -keystore "$HOME/.android/debug.keystore" -storepass android -alias androiddebugkey | sha256sum | cut -d' ' -f1)
else
  actual=$("$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --print-certs "$1" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')
fi
test "$actual" = "$expected" || { echo 'ERROR: la firma no coincide con la identidad estable de Asistente Pro. Publicación bloqueada.' >&2; exit 1; }
echo "Firma verificada: $actual"
