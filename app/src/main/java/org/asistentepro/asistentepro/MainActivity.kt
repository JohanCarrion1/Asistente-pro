package org.asistentepro.asistentepro

import android.Manifest
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.voice.VoiceInteractionService
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val runtime by lazy { AureaRuntime.get(this) }
    private val model get() = runtime.engine
    private var recognizer: SpeechRecognizer? = null
    private var generation = 0
    private var pendingHandsFree = false
    private var showOnStart = false
    private var pendingAction: DeviceAction? = null
    private val audioPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (it) listen() else model.error("El micrófono necesita permiso. Puedes seguir escribiendo.")
    }
    private val handsFreePermissions = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startHandsFree()
        else model.error("Concede el permiso de micrófono para activar Manos libres.")
    }
    private val cameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        val action = pendingAction; pendingAction = null
        if (it && action != null) model.action(action) else model.error("Concede permiso de cámara para usar la linterna.")
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT; window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).apply { isAppearanceLightStatusBars = false; isAppearanceLightNavigationBars = false }
        setContent {
            val state = model.state.collectAsStateWithLifecycle().value
            BackHandler(state.page != Page.Assistant) { model.page(Page.Assistant) }
            AsistenteApp(state, AssistantCallbacks(
                onPage = { stopVoice(); model.page(it) }, onDraft = model::draft,
                onSend = { stopVoice(); model.send(it) }, onMic = ::microphone,
                onStop = { stopVoice(); model.stop() }, onNew = { stopVoice(); model.newConversation() },
                onAction = ::perform, onKeyDraft = model::keyDraft, onSaveKey = model::saveKey, onRemoveKey = model::removeKey,
                onReading = model::toggleReading, onModel = model::model, onProvider = model::provider,
                onHandsFree = ::handsFree, onOverlayPermission = ::overlayPermission, onDefaultAssistant = ::defaultAssistant,
                onShowOverlay = { showOnStart = true; handsFree(true) },
                onOpen = { stopVoice(); model.openConversation(it) }, onDelete = model::deleteConversation,
                onClearHistory = model::clearHistory, onDismiss = model::dismissError, onSpeak = runtime::speak,
            ))
        }
    }
    override fun onStart() { super.onStart(); runtime.foregroundLauncher = { startActivity(it) } }
    override fun onResume() {
        super.onResume()
        model.permissions(Settings.canDrawOverlays(this), VoiceInteractionService.isActiveService(this, ComponentName(this, AureaVoiceService::class.java)))
        if (pendingHandsFree && Settings.canDrawOverlays(this)) { pendingHandsFree = false; handsFree(true) }
    }
    private fun perform(action: DeviceAction) {
        stopVoice()
        if (action is DeviceAction.Torch && ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            pendingAction = action; cameraPermission.launch(Manifest.permission.CAMERA)
        } else model.action(action)
    }
    private fun overlayPermission() {
        runCatching { startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) }
            .onFailure { model.error("Abre los ajustes de Android y permite a Aurea mostrarse sobre otras apps.") }
    }
    private fun defaultAssistant() {
        runCatching {
            if (Build.VERSION.SDK_INT >= 29) {
                val role = getSystemService(RoleManager::class.java)
                if (role.isRoleAvailable(RoleManager.ROLE_ASSISTANT) && !role.isRoleHeld(RoleManager.ROLE_ASSISTANT)) startActivity(role.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT))
                else startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
            } else startActivity(Intent(Settings.ACTION_VOICE_INPUT_SETTINGS))
        }.onFailure { model.error("Selecciona Aurea como app de asistencia en los ajustes de Android.") }
    }
    private fun handsFree(enabled: Boolean) {
        if (!enabled) { stopService(Intent(this, HandsFreeService::class.java)); pendingHandsFree = false; return }
        if (!Settings.canDrawOverlays(this)) { pendingHandsFree = true; overlayPermission(); return }
        if (HandsFreeService.active != null) { if (showOnStart) HandsFreeService.active?.listenNow(); showOnStart = false; return }
        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) permissions += Manifest.permission.POST_NOTIFICATIONS
        handsFreePermissions.launch(permissions.toTypedArray())
    }
    private fun startHandsFree() {
        recognizer?.destroy(); recognizer = null
        runCatching { ContextCompat.startForegroundService(this, Intent(this, HandsFreeService::class.java).apply { if (showOnStart) action = HandsFreeService.SHOW }); showOnStart = false }
            .onFailure { model.error("Android no permitió activar Manos libres. Inténtalo con la app abierta.") }
    }
    private fun microphone() {
        when (model.state.value.phase) {
            VoicePhase.Listening, VoicePhase.Speaking -> stopVoice()
            VoicePhase.Thinking -> model.stop()
            VoicePhase.Idle -> if (HandsFreeService.active != null) HandsFreeService.active?.listenNow()
                else if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) listen()
                else audioPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }
    private fun listen() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { model.error("Activa Manos libres en Ajustes para usar la voz local, o instala un servicio de voz de Android."); return }
        recognizer?.destroy(); val current = ++generation
        recognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { if (current == generation) model.phase(VoicePhase.Listening) }
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onError(error: Int) { if (current == generation) model.error("No alcancé a escucharte. Toca el micrófono para intentarlo otra vez.") }
                override fun onResults(results: Bundle?) {
                    if (current != generation) return
                    model.phase(VoicePhase.Idle)
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { model.send(WakePhrase.stripOptionalName(it), spoken = true) }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    if (current == generation) partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let(model::draft)
                }
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            runCatching { startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES").putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)) }
                .onFailure { model.error("No se pudo iniciar el servicio de voz. Puedes activar Manos libres o seguir escribiendo.") }
        }
    }
    private fun stopVoice() { generation++; recognizer?.cancel(); runtime.stopSpeaking(); if (model.state.value.phase == VoicePhase.Listening) model.phase(VoicePhase.Idle) }
    override fun onStop() { generation++; recognizer?.cancel(); runtime.foregroundLauncher = null; super.onStop() }
    override fun onDestroy() { recognizer?.destroy(); super.onDestroy() }
}
