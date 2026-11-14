package org.asistentepro.asistentepro

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*

class AureaVoiceService : VoiceInteractionService()

class AureaSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = AureaSession(this)
}

/** System assistant gesture opens this card without taking over the foreground app. */
class AureaSession(context: android.content.Context) : VoiceInteractionSession(context) {
    private val runtime = AureaRuntime.get(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var speech: SpeechRecognizer? = null
    private var card: OverlayContent? = null
    override fun onCreate() { setTheme(R.style.Theme_AureaSession); super.onCreate() }
    override fun onCreateContentView(): View {
        val container = FrameLayout(context).apply { setPadding(16, 12, 16, 32) }
        card = OverlayContent(context, runtime, ::listen, ::hide)
        container.addView(card)
        scope.launch { runtime.engine.state.collect { card?.update(it) } }
        window?.window?.setLayout(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT)
        window?.window?.setGravity(Gravity.BOTTOM)
        return container
    }
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        runtime.sessionLauncher = { intent -> startAssistantActivity(intent) }
        if (HandsFreeService.active != null) HandsFreeService.active?.listenNow() else listen()
    }
    private fun listen() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            runtime.engine.error("Abre Aurea y concede permiso de micrófono para hablar."); return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(context)) { runtime.engine.error("Activa Manos libres en Aurea para usar el reconocimiento local sin un servicio de voz del sistema."); return }
        if (HandsFreeService.active != null) { HandsFreeService.active?.listenNow(); return }
        speech?.destroy(); runtime.stopSpeaking()
        speech = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) { runtime.engine.phase(VoicePhase.Listening) }
                override fun onBeginningOfSpeech() = Unit
                override fun onRmsChanged(rmsdB: Float) = Unit
                override fun onBufferReceived(buffer: ByteArray?) = Unit
                override fun onEndOfSpeech() = Unit
                override fun onError(error: Int) { runtime.engine.error("No alcancé a escucharte. Toca Hablar para intentarlo otra vez.") }
                override fun onResults(results: Bundle?) {
                    runtime.engine.phase(VoicePhase.Idle)
                    results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.let { runtime.engine.send(WakePhrase.stripOptionalName(it), spoken = true) }
                }
                override fun onPartialResults(partialResults: Bundle?) = Unit
                override fun onEvent(eventType: Int, params: Bundle?) = Unit
            })
            startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM).putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-ES"))
        }
    }
    override fun onHide() {
        speech?.destroy(); speech = null; runtime.sessionLauncher = null
        if (runtime.engine.state.value.phase == VoicePhase.Listening && HandsFreeService.active == null) runtime.engine.phase(VoicePhase.Idle)
        super.onHide()
    }
    override fun onDestroy() { speech?.destroy(); scope.cancel(); super.onDestroy() }
}
