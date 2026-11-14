package org.asistentepro.asistentepro

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Bundle
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.Locale

class MainActivity : ComponentActivity() {
    private val model: AssistantViewModel by viewModels()
    private var recognizer: SpeechRecognizer? = null
    private var speaker: TextToSpeech? = null
    private var speechReady = false
    private var voiceGeneration = 0
    private var pendingAction: DeviceAction? = null
    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) listen() else model.error("El micrófono necesita permiso. Puedes seguir escribiendo.")
    }
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { allowed ->
        val action = pendingAction; pendingAction = null
        if (allowed && action != null) perform(action) else model.error("Activa el permiso de cámara para usar la linterna.")
    }
    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) { model.torch(enabled) }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false; isAppearanceLightNavigationBars = false
        }
        model.onDeviceAction = ::perform
        model.onSpeak = ::speak
        runCatching { getSystemService(CameraManager::class.java).registerTorchCallback(torchCallback, null) }
        speaker = TextToSpeech(this) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = speaker?.setLanguage(Locale("es", "PE"))
                speechReady = result != null && result >= 0
                if (!speechReady) speechReady = speaker?.setLanguage(Locale("es", "ES"))?.let { it >= 0 } == true
            }
        }
        speaker?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(id: String?) { runOnUiThread { model.phase(VoicePhase.Speaking) } }
            override fun onDone(id: String?) { runOnUiThread { model.phase(VoicePhase.Idle) } }
            @Deprecated("Deprecated in Java")
            override fun onError(id: String?) { runOnUiThread { model.phase(VoicePhase.Idle) } }
        })
        setContent {
            val state = model.state.collectAsStateWithLifecycle().value
            BackHandler(state.page != Page.Assistant) { model.page(Page.Assistant) }
            AsistenteApp(state, AssistantCallbacks(
                onPage = { stopVoice(); model.page(it) }, onDraft = model::draft,
                onSend = { stopVoice(); model.send(it) }, onMic = ::microphone,
                onStop = { stopVoice(); model.stop() }, onNew = { stopVoice(); model.newConversation() },
                onAction = { stopVoice(); perform(it) }, onKeyDraft = model::keyDraft,
                onSaveKey = model::saveKey, onRemoveKey = model::removeKey,
                onReading = model::toggleReading, onModel = model::model,
                onOpen = { stopVoice(); model.openConversation(it) }, onDelete = model::deleteConversation,
                onClearHistory = model::clearHistory, onDismiss = model::dismissError, onSpeak = ::speak,
            ))
        }
    }
    private fun microphone() {
        when (model.state.value.phase) {
            VoicePhase.Listening, VoicePhase.Speaking -> stopVoice()
            VoicePhase.Thinking -> model.stop()
            VoicePhase.Idle -> if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listen() else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    private fun listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            model.error("No hay reconocimiento de voz instalado. Activa el servicio de voz de Android o usa el teclado."); return
        }
        recognizer?.destroy()
        val generation = ++voiceGeneration
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { if (generation == voiceGeneration) model.phase(VoicePhase.Listening) }
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onError(error: Int) {
                    if (generation != voiceGeneration) return
                    val message = when (error) {
                        SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No alcancé a escucharte. Toca el micrófono e inténtalo otra vez."
                        SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "El servicio de voz necesita conexión. Puedes usar el teclado."
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Activa el permiso del micrófono en Ajustes de Android."
                        else -> "El micrófono no está disponible ahora. Inténtalo de nuevo."
                    }
                    model.error(message)
                }
                override fun onResults(results: Bundle?) {
                    if (generation != voiceGeneration) return
                    model.phase(VoicePhase.Idle)
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { model.send(it) }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    if (generation != voiceGeneration) return
                    partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(model::draft)
                }
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
        }
        model.phase(VoicePhase.Listening)
        runCatching {
            recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-PE")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            })
        }.onFailure { model.error("No se pudo iniciar el micrófono. Puedes seguir escribiendo.") }
    }
    private fun speak(text: String) {
        if (!speechReady) { model.error("Instala una voz en español desde los ajustes de texto a voz de Android."); return }
        recognizer?.cancel()
        speaker?.speak(text.take(3500), TextToSpeech.QUEUE_FLUSH, null, "asistente-response")
    }
    private fun stopVoice() {
        voiceGeneration++
        recognizer?.cancel(); speaker?.stop()
        if (model.state.value.phase != VoicePhase.Thinking) model.phase(VoicePhase.Idle)
    }
    private fun perform(action: DeviceAction) {
        if (action is DeviceAction.Torch && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            pendingAction = action; cameraPermission.launch(Manifest.permission.CAMERA); return
        }
        runCatching {
            when (action) {
                is DeviceAction.Torch -> {
                    val manager = getSystemService(CameraManager::class.java)
                    val camera = manager.cameraIdList.firstOrNull { manager.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
                    if (camera == null) { model.error("Este dispositivo no tiene linterna disponible."); return }
                    val enabled = action.enabled ?: !model.state.value.torchOn
                    manager.setTorchMode(camera, enabled); model.torch(enabled)
                    model.deviceResult(if (enabled) "Linterna encendida." else "Linterna apagada.")
                }
                DeviceAction.Camera -> { startActivity(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)); model.deviceResult("Abrí la cámara. Puedes tomar la foto desde allí.") }
                DeviceAction.Wifi -> { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)); model.deviceResult("Abrí los ajustes de Wi-Fi para que elijas la red.") }
                DeviceAction.Sms -> { startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))); model.deviceResult("Abrí Mensajes. Elige el destinatario y confirma el envío.") }
                DeviceAction.Help -> model.deviceResult("Puedes hablar o escribir para consultar tu batería, encender la linterna, abrir la cámara o tus apps, cambiar el volumen, crear alarmas y temporizadores. Estas acciones no necesitan una clave de IA. Para preguntas generales, conecta Groq en Ajustes. Android te pedirá confirmar las acciones que lo requieran.")
                is DeviceAction.Volume -> {
                    val audio = getSystemService(AudioManager::class.java)
                    audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, if (action.raise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    model.deviceResult("Volumen multimedia: ${audio.getStreamVolume(AudioManager.STREAM_MUSIC)} de ${audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)}.")
                }
                is DeviceAction.OpenApp -> {
                    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                    val activity = packageManager.queryIntentActivities(intent, 0).firstOrNull {
                        java.text.Normalizer.normalize(it.loadLabel(packageManager).toString().lowercase(), java.text.Normalizer.Form.NFD).replace("\\p{M}+".toRegex(), "").contains(action.name)
                    }
                    if (activity == null) { model.error("No encuentro ${action.name} entre tus aplicaciones instaladas."); return }
                    startActivity(Intent.makeMainActivity(android.content.ComponentName(activity.activityInfo.packageName, activity.activityInfo.name)))
                    model.deviceResult("Abrí ${activity.loadLabel(packageManager)}.")
                }
                is DeviceAction.Alarm -> { startActivity(Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, action.hour); putExtra(AlarmClock.EXTRA_MINUTES, action.minute)
                    putExtra(AlarmClock.EXTRA_MESSAGE, "Asistente Pro"); putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                }); model.deviceResult("Abrí Reloj. Revisa y confirma la alarma.") }
                DeviceAction.Battery -> {
                    val level = getSystemService(BatteryManager::class.java).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                    if (level in 0..100) model.deviceResult("Tu batería está al $level %.") else model.error("Android no pudo informar el nivel de batería.")
                }
                is DeviceAction.Timer -> { startActivity(Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, action.seconds)
                    putExtra(AlarmClock.EXTRA_MESSAGE, "Asistente Pro")
                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                }); model.deviceResult("Abrí Reloj con un temporizador de ${action.seconds} segundos. Confírmalo allí.") }
            }
        }.onFailure { model.error("Android no pudo abrir esta función. Comprueba los permisos y las aplicaciones instaladas.") }
    }
    override fun onStop() { super.onStop(); stopVoice() }
    override fun onDestroy() {
        recognizer?.destroy(); speaker?.shutdown()
        runCatching { getSystemService(CameraManager::class.java).unregisterTorchCallback(torchCallback) }
        model.onDeviceAction = null; model.onSpeak = null
        super.onDestroy()
    }
}
