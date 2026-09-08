# Asistente Pro - IA Potente con Groq

Asistente personal Android con **IA ultra rápida** impulsada por la API de **Groq** (modelo `llama-3.1-8b-instant`). Controla tu dispositivo por **texto y voz**, consulta información del sistema y ejecuta acciones rápidas — todo con una interfaz moderna Material Design oscura.

## 🚀 Características

- **🤖 IA con Groq (Llama 3.1 8B - ultra rápida)**: Respuestas en menos de 1 segundo.
- **🎤 Control por voz y texto**: Escribe o habla; el asistente te entiende.
- **📱 Control del dispositivo**: Vibrar, linterna, batería, foto, ubicación, SMS, apps.
- **🌐 Funciona con internet**: Backend Flask local + API remota de Groq.
- **⚙️ Monitor de sistema**: CPU, RAM y batería en tiempo real (psutil).
- **🔍 Historial con búsqueda**: Encuentra cualquier conversación.
- **✨ Animaciones suaves**: Banner ASCII animado, transiciones fluidas, indicador "Escribiendo...".

## 📁 Estructura

```
AsistentePro/
├── app.py                  # Frontend Kivy (Material Design)
├── buildozer.spec          # Configuración para compilar APK
├── run.sh                  # Inicia backend + app
├── build_apk.sh            # Compila el APK
├── backend/
│   ├── api.py              # API Flask (puerto 5000)
│   └── config.py           # API Key y configuración de Groq
└── icons/
    ├── icon.png            # Icono generado (512x512)
    └── generar_icono.py    # Script que genera el icono
```

## 🛠 Instalación

### 1. Requisitos (escritorio/Linux)

```bash
sudo apt update
sudo apt install -y python3 python3-pip ffmpeg
pip3 install --user kivy==2.3.1 flask==3.1.3 requests==2.34.2 \
    SpeechRecognition==3.17.0 gTTS==2.5.4 pydub==0.25.1 \
    Pillow==12.3.0 psutil==7.2.2
```

### 2. Configurar la API Key

Abre `backend/config.py` y asegúrate de que la clave esté configurada:

```python
API_KEY = "TU_GROQ_API_KEY_AQUI"
MODELO = "llama-3.1-8b-instant"
```

> ⚠️ **Mantén tu API Key secreta - no la compartas** ni la subas a repos públicos.

### 3. Probar en escritorio

```bash
cd AsistentePro
./run.sh
```

Esto inicia el backend Flask en `http://0.0.0.0:5000` y luego abre la app Kivy.

Puedes probar el backend sin interfaz:

```bash
curl -X POST http://localhost:5000/comando -H "Content-Type: application/json" \
     -d '{"texto": "Hola, ¿quién eres?"}'
```

## 📲 Compilar APK (Android)

### 1. Instalar Buildozer

```bash
pip3 install --user buildozer
```

### 2. Compilar

```bash
cd AsistentePro
./build_apk.sh
```

El APK se generará en:

```
bin/AsistentePro-1.0.0-arm64-v8a_armeabi-v7a-debug.apk
```

Transfiérelo a tu teléfono y haz doble clic para instalar.
En el teléfono, el backend corre localmente vía `localhost:5000`.

## 💬 Comandos de ejemplo

| Preguntas / comandos | Acción |
|----------------------|--------|
| "¿Quién eres?" | Explica su identidad (IA) |
| "Dame el estado de la batería" | Acción batería |
| "manda vibrar" | Hace vibrar el teléfono |
| "enciende la linterna" | Enciende la linterna |
| "captura una foto" | Toma una foto |
| "¿dónde estoy?" | Ubicación aproximada |
| "Llámana en 5 minutos" | Planea con IA |

## 🖼 Screenshots

*(Pendientes: añade aquí capturas de la app en ejecución)*

```
┌──────────────────────────┐
│  🤖 Asistente Pro       │
│  ┌────────────────────┐ │
│  │ 🧑 ¡Hola! abre la  │ │
│  │    linterna porfa  │ │
│  │ 🤖 ¡Listo! 🔦      │ │
│  │    Linterna enc.   │ │
│  └────────────────────┘ │
│  [ Escribe aquí   ] 🎤➤ │
└──────────────────────────┘
```

## 🧰 Scripts útiles

- `./run.sh` — Arranca backend + aplicación.
- `./build_apk.sh` — Compila el APK con Buildozer.
- `python3 icons/generar_icono.py` — Regenera el icono de la app.

## 📝 Notas

- El reconocimiento de voz del frontend usa el módulo nativo `android_speech` cuando se compila para Android; en escritorio se desactiva automáticamente.
- El backend escucha en `0.0.0.0:5000`, ideal para probar desde otros dispositivos de tu red local.

---
**Asistente Pro v1.0.0** — Hecho con ❤️ y Groq.