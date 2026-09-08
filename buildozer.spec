[app]

title = Asistente Pro
package.name = asistentepro
package.domain = org.asistentepro

source.dir = .
source.include_exts = py,kv,png,jpg,jpeg,json,mp3,wav,ogg
version = 1.0.0

requirements = python3,kivy

orientation = portrait

android.api = 33
android.minapi = 21
android.ndk = 25b
android.archs = arm64-v8a

android.permissions = INTERNET,CAMERA,RECORD_AUDIO

android.accept_sdk_license = True

fullscreen = 0


[buildozer]

log_level = 2
warn_on_root = 0
