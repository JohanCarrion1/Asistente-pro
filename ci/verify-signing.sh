#!/usr/bin/env bash
set -euo pipefail
expected=$(tr -d '\r\n' < ci/signing.sha256)
key="${ASISTENTE_KEYSTORE:?Configura ASISTENTE_KEYSTORE con la ruta explícita del keystore}"
if [[ $# -eq 0 ]]; then
  if [[ ! -f "$key" ]]; then
    test "$expected" = BOOTSTRAP || { echo 'Falta la firma fijada. Publicación bloqueada: restaura el keystore.' >&2; exit 1; }
    mkdir -p "$(dirname "$key")"
    keytool -genkeypair -keystore "$key" -storetype PKCS12 -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 3072 -validity 10000 -dname 'CN=Asistente Pro Native, O=Asistente Pro'
  fi
  actual=$(keytool -exportcert -keystore "$key" -storepass android -alias androiddebugkey | sha256sum | cut -d' ' -f1)
else
  actual=$("$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --print-certs "$1" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')
fi
if [[ "$expected" = BOOTSTRAP ]]; then
  expected=$(keytool -exportcert -keystore "$key" -storepass android -alias androiddebugkey | sha256sum | cut -d' ' -f1)
fi
test "$actual" = "$expected" || { echo 'ERROR: la firma no coincide con la identidad estable de Asistente Pro. Publicación bloqueada.' >&2; exit 1; }
mkdir -p app/build
printf '%s\n' "$actual" > app/build/signing-public.sha256
echo "Firma verificada: $actual"
