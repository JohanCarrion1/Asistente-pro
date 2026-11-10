[app]

title = Asistente Pro
package.name = asistentepro
package.domain = org.asistentepro

source.dir = .
source.include_exts = py,kv,png,jpg,jpeg,json,mp3,wav,ogg
version = 1.0.0

requirements = python3,kivy,requests

orientation = portrait

# Arquitectura unica: compilacion mas rapida y menos puntos de fallo
android.archs = arm64-v8a

# Nota: android.api, android.ndk y android.minapi se dejan en sus valores
# por defecto (los que buildozer/python-for-android recomiendan hoy),
# fijarlos a versiones viejas (33/25b/21) rompia la compilacion.

android.permissions = INTERNET,CAMERA,RECORD_AUDIO

fullscreen = 0


[buildozer]

log_level = 2
warn_on_root = 0
