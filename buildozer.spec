[app]
title = Asistente Pro
package.name = AsistentePro
package.domain = org.asistentepro
source.dir = .
version = 1.0.0
requirements = python3,kivy==2.3.1,flask==3.1.3,requests==2.34.2,SpeechRecognition==3.17.0,gTTS==2.5.4,pydub==0.25.1,Pillow==12.3.0,psutil==7.2.2
orientation = portrait
android.api = 33
android.minapi = 26
android.archs = arm64-v8a, armeabi-v7a
android.permissions = INTERNET,CAMERA,MICROPHONE,RECORD_AUDIO,ACCESS_FINE_LOCATION,VIBRATE

[buildozer]
log_level = 2
warn_on_root = 1
pip.install_args = --break-system-packages
