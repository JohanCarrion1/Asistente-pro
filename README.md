# Asistente Pro 2.0

Asistente Android nativo en **Kotlin + Jetpack Compose**, con diseño carbón y jade,
conversaciones con Groq, voz de Android e icono adaptativo propio.

## Vista previa

<img src="docs/previews/inicio.png" width="230" alt="Inicio nativo en carbón y jade" /> <img src="docs/previews/chat.png" width="230" alt="Conversación con el asistente" /> <img src="docs/previews/ajustes.png" width="230" alt="Ajustes de IA y privacidad" />

[Nuevo icono](docs/previews/icono.png). Capturas renderizadas desde los componentes Compose de la aplicación.

## Descargar el APK

En **Actions → Asistente Pro - Android nativo**, abre la ejecución verde más reciente.
En **Artifacts**, descarga **asistente-pro-apk**, descomprime el ZIP e instala
`app-debug.apk`. Compatible con Android 8.0 o posterior, sin dependencia de Python,
Kivy, Flask, WebView ni servidor local. El APK nativo sirve para ARM y x86.

La firma debug usa la misma caché que la versión anterior. Si una instalación antigua
tiene otra firma, Android pedirá desinstalarla antes de instalar esta versión.

## Funciones

- Interfaz adaptable, chat, estados de escucha y respuesta, núcleo animado.
- Reconocimiento de voz mediante el servicio instalado de Android. El micrófono
  se activa al tocarlo; no hay escucha permanente ni palabra de activación.
- Respuestas habladas con TextToSpeech; requiere una voz en español instalada.
- Conversaciones con contexto, cancelación de solicitudes, copiar y leer respuestas.
- Historial local, reabrir conversaciones, eliminación individual o completa.
- Clave de Groq cifrada con Android Keystore; no se incorpora ninguna clave al APK.
- Modelos Groq rápido y profundo seleccionables en Ajustes.
- Linterna real, abrir cámara, ajustes Wi-Fi, temporizador y batería.
- Comandos como `enciende la linterna`, `apaga la linterna`, `abre la cámara`,
  `temporizador de 5 minutos` y `cuánta batería tengo` funcionan sin clave de IA.
- Los temporizadores se confirman en la app de reloj. `abre mensajes` abre la app
  SMS; el usuario elige destinatario y confirma el envío.

La IA no controla silenciosamente el teléfono ni simula acciones ejecutadas.
Android puede limitar las funciones disponibles según permisos y apps instaladas.

## Configurar IA

Abre **Ajustes**, obtén una clave en <https://console.groq.com/keys> y guárdala.
La clave solo se usa para consultar Groq. Los últimos mensajes necesarios para
responder se envían al proveedor; el historial permanece en el almacenamiento
privado de esta app. La voz utiliza el servicio de reconocimiento de Android.

## Compilar y verificar

Requisitos: JDK 17, Android SDK 35.

```sh
./gradlew :app:assembleDebug
./gradlew :app:recordPaparazziDebug :app:lintDebug
```

Actions publica el APK y **asistente-pro-vistas-y-pruebas** con capturas de inicio,
chat, ajustes y pantalla pequeña con texto ampliado. Las pruebas también verifican
los comandos nativos, contexto enviado a Groq y errores de autenticación y respuesta.
Las capturas verifican renderizado; los permisos, la cámara y la voz deben comprobarse
en un dispositivo real, pues dependen del hardware y de los servicios de Android.

## Estructura

`app/` contiene toda la aplicación nativa. Los archivos Python y Buildozer de la raíz
se conservan como referencia de la versión anterior y ya no se compilan en Actions.
