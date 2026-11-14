[app]

title = Asistente Pro
package.name = asistentepro
package.domain = org.asistentepro

source.dir = .
source.include_exts = py,kv,png,jpg,jpeg,json,mp3,wav,ogg,xml
version = 1.0.1

requirements = python3==3.11.5,hostpython3==3.11.5,kivy==2.3.0,requests==2.32.3,charset-normalizer==3.3.2,urllib3==2.2.2,idna==3.7,certifi,flask==2.3.3,werkzeug==2.3.8,jinja2==3.1.6,markupsafe==2.1.5,itsdangerous==2.2.0,click==8.1.7,blinker==1.8.2

orientation = portrait

# Arquitectura unica: compilacion mas rapida y menos puntos de fallo
android.archs = arm64-v8a

# Toolchain estable: evita cambios de Python/NDK entre compilaciones.
p4a.commit = 957a3e5f8c270f7aa648ba185e5a68c1077a798d
android.api = 33
android.minapi = 21
android.ndk = 25b
android.sdk_path = .android-sdk
android.skip_update = True
android.enable_androidx = True
android.extra_manifest_application_arguments = android/manifest_application.xml
icon.filename = icons/icon.png
source.exclude_dirs = tests,.git,.github,.android-sdk

# Aceptar automaticamente las licencias del SDK de Android.
# Sin esto, sdkmanager se salta la instalacion de build-tools y la
# compilacion falla con "Aidl not found" (run #41).
android.accept_sdk_license = True

android.permissions = INTERNET,CAMERA,RECORD_AUDIO

fullscreen = 0


[buildozer]

log_level = 2
warn_on_root = 0
